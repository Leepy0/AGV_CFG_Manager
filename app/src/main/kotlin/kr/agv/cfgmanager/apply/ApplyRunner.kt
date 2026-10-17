package kr.agv.cfgmanager.apply

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.BridgeConfig
import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.core.bridge.DeviceSnapshot
import kr.agv.cfgmanager.core.history.HistoryEntry
import kr.agv.cfgmanager.core.workflows.ApplyFailure
import kr.agv.cfgmanager.core.workflows.ApplyOutcome
import kr.agv.cfgmanager.core.workflows.ApplyRequest
import kr.agv.cfgmanager.core.workflows.ApplyResult
import kr.agv.cfgmanager.core.workflows.ApplyStep
import kr.agv.cfgmanager.core.workflows.ApplyWorkflow
import kr.agv.cfgmanager.core.workflows.WorkflowProgress
import kr.agv.cfgmanager.core.workflows.historyCode
import kr.agv.cfgmanager.data.HistoryStore
import kr.agv.cfgmanager.session.BridgeSession
import kr.agv.cfgmanager.session.LinkStatus
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 적용 화면이 보는 상태. 회전 · 탭 전환에도 [ApplyRunner] 가 쥐고 있어 진행 패널이 복원된다. */
sealed interface ApplyUi {
    data object Idle : ApplyUi

    data class Running(
        val agv: Int,
        val saveOnly: Boolean,
        val step: ApplyStep,
        val message: String,
        val done: Int,
        val total: Int,
    ) : ApplyUi {
        val cancellable: Boolean get() = ApplyProgress.cancellable(step)
        val fraction: Float get() = ApplyProgress.fraction(step, done, total)
    }

    /** 끝난 결과. [historyFailed] 이면 이력 파일에 쓰지 못했다(결과 자체는 유효). */
    data class Done(
        val result: ApplyResult,
        val agv: Int,
        val saveOnly: Boolean,
        val historyFailed: Boolean,
    ) : ApplyUi
}

/**
 * 적용 워크플로를 세션 잠금 안에서 실행하고, 진행 상태 · 결과 · 이력을 맡는다 (docs/DEVELOPMENT_PLAN.md 7.1, F-H1).
 * 실행은 [scope] (앱 수명) 에서 하므로 화면을 떠나도 끝까지 돈다. 취소는 저장 전 단계에서만 듣는다.
 */
class ApplyRunner(
    private val session: BridgeSession,
    private val history: HistoryStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val isFake: () -> Boolean = { false },
) {
    private val uiFlow = MutableStateFlow<ApplyUi>(ApplyUi.Idle)
    val state: StateFlow<ApplyUi> = uiFlow.asStateFlow()

    private var job: Job? = null
    private var last: Pending? = null

    private data class Pending(val target: BridgeConfig, val saveOnly: Boolean)

    /** 적용을 시작한다. 이미 도는 중이면 false. */
    fun start(target: BridgeConfig, saveOnly: Boolean): Boolean {
        if (uiFlow.value is ApplyUi.Running) return false
        last = Pending(target, saveOnly)
        uiFlow.value = ApplyUi.Running(target.agv, saveOnly, ApplyStep.Validate, "설정 확인", 0, 0)
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) { run(target, saveOnly) }
        return true
    }

    /** 마지막 적용을 같은 값으로 다시 한다. */
    fun retry(): Boolean = last?.let { start(it.target, it.saveOnly) } ?: false

    /** 저장 전 단계에서만 취소한다. 저장 이후에는 무시된다. */
    fun cancel() {
        val running = uiFlow.value as? ApplyUi.Running ?: return
        if (running.cancellable) job?.cancel()
    }

    /** 결과 카드를 닫는다. */
    fun dismiss() {
        if (uiFlow.value is ApplyUi.Done) uiFlow.value = ApplyUi.Idle
    }

    private suspend fun run(target: BridgeConfig, saveOnly: Boolean) {
        val before = session.state.value.snapshot
        var finished: ApplyResult? = null
        val onProgress: (WorkflowProgress) -> Unit = { progress ->
            when (progress) {
                is WorkflowProgress.Step -> uiFlow.value = ApplyUi.Running(
                    agv = target.agv,
                    saveOnly = saveOnly,
                    step = progress.step,
                    message = progress.message,
                    done = progress.done,
                    total = progress.total,
                )
                is WorkflowProgress.Finished -> finished = progress.result
            }
        }
        try {
            if (before == null || session.state.value.link != LinkStatus.Connected) {
                finished = notReady("장치에 연결되지 않았습니다. 보드 탭에서 연결하세요.")
            } else {
                val request = ApplyRequest(before = before, target = target, saveOnly = saveOnly)
                finished = session.withExclusive { channel ->
                    ApplyWorkflow(channel = channel, nowMillis = clock).run(request, onProgress)
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { conclude(finished ?: cancelledResult(), target, saveOnly, before) }
            throw cancelled
        } catch (lost: BridgeChannelException) {
            finished = notReady("장치와 연결돼 있지 않습니다. 보드 탭에서 다시 연결하세요.")
        }
        conclude(finished ?: notReady("적용 결과를 받지 못했습니다."), target, saveOnly, before)
    }

    /** 결과를 반영하고 이력을 남긴 뒤 완료 상태로 바꾼다. 세션 스냅샷은 워크플로가 읽은 값으로 맞춘다. */
    private suspend fun conclude(
        result: ApplyResult,
        target: BridgeConfig,
        saveOnly: Boolean,
        before: DeviceSnapshot?,
    ) {
        result.after?.let { session.adoptSnapshot(it) }
        val historyOk = if (result.outcome == ApplyOutcome.NoChange) true else {
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    history.append(historyEntry(result, target, saveOnly, before, result.after))
                    true
                } catch (failure: IOException) {
                    false
                }
            }
        }
        uiFlow.value = ApplyUi.Done(result, target.agv, saveOnly, historyFailed = !historyOk)
    }

    private fun historyEntry(
        result: ApplyResult,
        target: BridgeConfig,
        saveOnly: Boolean,
        before: DeviceSnapshot?,
        after: DeviceSnapshot?,
    ): HistoryEntry = HistoryEntry(
        at = Instant.ofEpochMilli(clock()).truncatedTo(ChronoUnit.SECONDS).toString(),
        profileId = null,
        profileName = null,
        agv = target.agv,
        bleAddress = session.state.value.address,
        mac = after?.mac ?: before?.mac,
        deviceName = after?.name ?: before?.name,
        ip = target.ip ?: "dhcp",
        ssid = target.ssid,
        fw = after?.fw ?: before?.fw,
        channel = if (isFake()) "fake" else "ble",
        action = "apply",
        result = result.historyResult,
        rssi = after?.status?.wifi?.rssi,
        note = if (saveOnly) "저장만(재부팅 안 함)" else "",
    )

    private fun notReady(reason: String): ApplyResult = ApplyResult(
        outcome = ApplyOutcome.Failed(ApplyStep.Validate, ApplyFailure.NotReady(reason)),
        historyResult = ApplyFailure.NotReady(reason).historyCode(),
        changedKeys = emptySet<ConfigKey>(),
        saved = false,
        after = null,
    )

    private fun cancelledResult(): ApplyResult = ApplyResult(
        outcome = ApplyOutcome.Cancelled(ApplyStep.Validate),
        historyResult = "cancelled",
        changedKeys = emptySet(),
        saved = false,
        after = null,
    )
}
