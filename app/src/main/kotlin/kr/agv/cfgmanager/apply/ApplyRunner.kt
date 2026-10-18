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
import kr.agv.cfgmanager.data.RestorePoint
import kr.agv.cfgmanager.data.RestorePointStore
import kr.agv.cfgmanager.session.BridgeSession
import kr.agv.cfgmanager.session.LinkStatus
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 적용 한 번에 붙는 부가 정보: 이력의 프로필, 성공 뒤 라이브러리 저장 여부. [restore] 는 이전 값 복원 실행이다. */
data class ApplyMeta(
    val profileId: String? = null,
    val profileName: String? = null,
    val rememberWifi: Boolean = false,
    val restore: Boolean = false,
)

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
        /** 적용 직전 값을 저장해 둬서 [ApplyRunner.restore] 로 되돌릴 수 있다. */
        val restorable: Boolean = false,
        /** 이전 Wi-Fi 비밀번호를 앱이 알아서 복원 때 함께 보낸다. false 면 비밀번호는 보내지 않는다. */
        val restorePasswordKnown: Boolean = false,
        /** 이전 값 복원 실행의 결과다. 연속 세팅은 이 결과를 새 호기 완료로 세지 않는다. */
        val restored: Boolean = false,
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
    /** 적용 시작 · 결과를 앱 로그에 남긴다. 값(비밀번호 · IP)은 넘기지 않는다. */
    private val journal: (String) -> Unit = {},
    /** 저장 · 재부팅 · Wi-Fi 확인까지 성공한 뒤 불린다 (프로필 사용 시각, SSID 라이브러리 저장). 실패는 무시한다. */
    private val onSucceeded: suspend (ApplyMeta, BridgeConfig) -> Unit = { _, _ -> },
    /** 적용 직전 값 저장소. null 이면 되돌리기를 끈다. */
    private val restorePoints: RestorePointStore? = null,
    /** SSID 의 비밀번호를 앱이 아는 만큼 돌려준다 (라이브러리). 장치에서는 읽을 수 없다. */
    private val knownPassword: suspend (String) -> String? = { null },
) {
    private val uiFlow = MutableStateFlow<ApplyUi>(ApplyUi.Idle)
    val state: StateFlow<ApplyUi> = uiFlow.asStateFlow()

    private var job: Job? = null
    private var last: Pending? = null
    private var restoreKey: String? = null

    private data class Pending(val target: BridgeConfig, val saveOnly: Boolean, val meta: ApplyMeta)

    /** 적용을 시작한다. 이미 도는 중이면 false. */
    fun start(target: BridgeConfig, saveOnly: Boolean, meta: ApplyMeta = ApplyMeta()): Boolean {
        if (uiFlow.value is ApplyUi.Running) return false
        last = Pending(target, saveOnly, meta)
        uiFlow.value = ApplyUi.Running(target.agv, saveOnly, ApplyStep.Validate, "설정 확인", 0, 0)
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) { run(target, saveOnly, meta) }
        return true
    }

    /** 마지막 적용을 같은 값으로 다시 한다. */
    fun retry(): Boolean = last?.let { start(it.target, it.saveOnly, it.meta) } ?: false

    /** 마지막으로 성공한 적용의 직전 값으로 되돌린다. 저장한 값이 없으면 false. 비밀번호는 아는 경우에만 함께 보낸다. */
    suspend fun restore(): Boolean {
        val key = restoreKey ?: return false
        val point = restorePoints?.find(key) ?: return false
        return start(point.config.copy(pass = point.password), saveOnly = false, meta = ApplyMeta(restore = true))
    }

    /** 저장 전 단계에서만 취소한다. 저장 이후에는 무시된다. */
    fun cancel() {
        val running = uiFlow.value as? ApplyUi.Running ?: return
        if (running.cancellable) job?.cancel()
    }

    /** 결과 카드를 닫는다. */
    fun dismiss() {
        if (uiFlow.value is ApplyUi.Done) uiFlow.value = ApplyUi.Idle
    }

    private suspend fun run(target: BridgeConfig, saveOnly: Boolean, meta: ApplyMeta) {
        val before = session.state.value.snapshot
        // 라이브러리가 새 비밀번호로 덮어쓰기 전에, 되돌릴 때 쓸 이전 비밀번호를 미리 잡는다. 오픈 AP 면 빈 문자열이다.
        val previous = before?.config
        val oldPassword = if (meta.restore || previous == null) null else {
            knownPassword(previous.ssid) ?: if (previous.passLength == 0) "" else null
        }
        journal("${if (meta.restore) "복원" else "적용"} 시작: ${target.agv}호기${if (saveOnly) " (저장만)" else ""}")
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
                finished = notReady("장치에 연결되지 않았습니다. 세팅 탭에서 연결하세요.")
            } else {
                val request = ApplyRequest(before = before, target = target, saveOnly = saveOnly)
                finished = session.withExclusive { channel ->
                    ApplyWorkflow(channel = channel, nowMillis = clock).run(request, onProgress)
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { conclude(finished ?: cancelledResult(), target, saveOnly, before, meta, oldPassword) }
            throw cancelled
        } catch (lost: BridgeChannelException) {
            finished = notReady("장치와 연결돼 있지 않습니다. 세팅 탭에서 다시 연결하세요.")
        }
        conclude(finished ?: notReady("적용 결과를 받지 못했습니다."), target, saveOnly, before, meta, oldPassword)
    }

    /** 결과를 반영하고 이력을 남긴 뒤 완료 상태로 바꾼다. 세션 스냅샷은 워크플로가 읽은 값으로 맞춘다. */
    private suspend fun conclude(
        result: ApplyResult,
        target: BridgeConfig,
        saveOnly: Boolean,
        before: DeviceSnapshot?,
        meta: ApplyMeta,
        oldPassword: String?,
    ) {
        result.after?.let { session.adoptSnapshot(it) }
        journal("${if (meta.restore) "복원" else "적용"} 결과: ${result.historyResult}")
        val historyOk = if (result.outcome == ApplyOutcome.NoChange) true else {
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    history.append(historyEntry(result, target, saveOnly, before, result.after, meta))
                    true
                } catch (failure: IOException) {
                    false
                }
            }
        }
        val succeeded = result.outcome == ApplyOutcome.Applied || result.outcome == ApplyOutcome.SavedOnly
        // 복원 지점은 라이브러리 갱신(onSucceeded)보다 먼저 쓴다. 복원 실행 자신은 지점을 남기지 않는다.
        var restorable = false
        val previous = before?.config
        if (succeeded && !meta.restore && before != null && previous != null && restorePoints != null) {
            val key = before.mac ?: before.deviceId
            restorable = withContext(NonCancellable + Dispatchers.IO) {
                try {
                    val at = Instant.ofEpochMilli(clock()).truncatedTo(ChronoUnit.SECONDS).toString()
                    restorePoints.save(RestorePoint(key, at, before.name, previous, oldPassword))
                    restoreKey = key
                    true
                } catch (failure: IOException) {
                    false
                }
            }
        }
        if (succeeded) {
            withContext(NonCancellable) {
                try {
                    onSucceeded(meta, target)
                } catch (failure: Exception) {
                    // 프로필 · 라이브러리 갱신 실패로 적용 결과를 바꾸지 않는다
                }
            }
        }
        uiFlow.value = ApplyUi.Done(result, target.agv, saveOnly, historyFailed = !historyOk, restorable = restorable, restorePasswordKnown = oldPassword != null, restored = meta.restore)
    }

    private fun historyEntry(
        result: ApplyResult,
        target: BridgeConfig,
        saveOnly: Boolean,
        before: DeviceSnapshot?,
        after: DeviceSnapshot?,
        meta: ApplyMeta,
    ): HistoryEntry = HistoryEntry(
        at = Instant.ofEpochMilli(clock()).truncatedTo(ChronoUnit.SECONDS).toString(),
        profileId = meta.profileId,
        profileName = meta.profileName,
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
        note = listOfNotNull(
            if (meta.restore) "이전 값 복원" else null,
            if (saveOnly) "저장만(재부팅 안 함)" else null,
            result.bootNote,
        ).joinToString(" · "),
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
