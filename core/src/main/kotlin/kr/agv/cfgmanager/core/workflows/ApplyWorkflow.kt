package kr.agv.cfgmanager.core.workflows

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.BridgeCommands
import kr.agv.cfgmanager.core.bridge.BridgeConfig
import kr.agv.cfgmanager.core.bridge.BridgeStatus
import kr.agv.cfgmanager.core.bridge.ChannelState
import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.core.bridge.DeviceSnapshot
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kr.agv.cfgmanager.core.bridge.ResponseParsers
import kr.agv.cfgmanager.core.util.Masking
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 적용 워크플로 (docs/DEVELOPMENT_PLAN.md 7.1).
 *
 * Validate → SendSet → ReadBack → Save → [saveOnly 면 여기서 끝] → Reboot → WaitReady → VerifyWifi → Done.
 * - 취소는 코루틴 취소로. SendSet · ReadBack 에서만 실제로 취소되며, 저장 이후는 끝까지 실행한다.
 * - 명령 시간 초과는 한 번 재시도한다. 연결이 끊기면 다시 붙은 뒤 SendSet 부터 처음부터 다시 한다(최대 [maxLinkAttempts] 번).
 * - 비밀번호는 `set pass` 에 실릴 때만 나가며, 기록 · 실패 문구에는 가린 명령만 남는다.
 */
class ApplyWorkflow(
    private val channel: IBridgeChannel,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val reconnectTimeout: Duration = 30.seconds,
    private val wifiTimeout: Duration = 30.seconds,
    private val wifiPollInterval: Duration = 2.seconds,
    private val maxLinkAttempts: Int = 2,
    private val linkDropTimeout: Duration = 10.seconds,
) {
    /**
     * 적용을 실행하고 결과를 돌려준다. [onProgress] 로 단계를 알리고, 마지막에 [WorkflowProgress.Finished] 를 준다.
     * 취소되면 Finished(Cancelled) 를 알린 뒤 [CancellationException] 을 다시 던진다.
     */
    suspend fun run(request: ApplyRequest, onProgress: (WorkflowProgress) -> Unit = {}): ApplyResult {
        val execution = Execution(request, onProgress)
        try {
            return execution.run()
        } catch (cancelled: CancellationException) {
            onProgress(WorkflowProgress.Finished(execution.cancelledResult()))
            throw cancelled
        }
    }

    private inner class Execution(
        private val request: ApplyRequest,
        private val onProgress: (WorkflowProgress) -> Unit,
    ) {
        private var step = ApplyStep.Validate
        private var saved = false
        private var changed: Set<ConfigKey> = emptySet()
        private var lastStatus: BridgeStatus? = null
        private var rebootStartMs = 0L
        private var bootNote: String? = null

        suspend fun run(): ApplyResult {
            val result = try {
                preSave() ?: withContext(NonCancellable) { postSave() }
            } catch (failure: StepFailure) {
                ApplyResult(
                    outcome = ApplyOutcome.Failed(step, failure.failure),
                    historyResult = failure.failure.historyCode(),
                    changedKeys = changed,
                    saved = saved,
                    after = null,
                    bootNote = bootNote,
                )
            } catch (lost: BridgeChannelException) {
                val failure = ApplyFailure.Disconnected(step)
                ApplyResult(ApplyOutcome.Failed(step, failure), failure.historyCode(), changed, saved, null, bootNote)
            }
            onProgress(WorkflowProgress.Finished(result))
            return result
        }

        fun cancelledResult(): ApplyResult = ApplyResult(
            outcome = ApplyOutcome.Cancelled(step),
            historyResult = "cancelled",
            changedKeys = changed,
            saved = saved,
            after = null,
        )

        /** 저장 전 단계. 바뀐 게 없으면 [ApplyResult] 를, 계속하면 null 을 준다. */
        private suspend fun preSave(): ApplyResult? {
            step = ApplyStep.Validate
            report("설정 확인")
            if (channel.state.value !is ChannelState.Connected) {
                throw StepFailure(ApplyFailure.NotReady("장치에 연결되지 않았습니다."))
            }
            val before = request.before.config
                ?: throw StepFailure(ApplyFailure.NotReady("현재 설정을 읽지 못했습니다."))
            val target = request.target
            changed = target.diff(before)
            if (changed.isEmpty()) {
                return ApplyResult(ApplyOutcome.NoChange, "no_change", emptySet(), false, request.before)
            }
            val keys = keysToSend(target)
            var attempts = 0
            while (true) {
                attempts++
                try {
                    sendAll(target, keys)
                    readBack(target, keys)
                    return null
                } catch (lost: BridgeChannelException.Disconnected) {
                    if (attempts >= maxLinkAttempts) throw StepFailure(ApplyFailure.Disconnected(step))
                    report("연결이 끊겨 다시 연결하는 중")
                    if (!channel.waitReady(reconnectTimeout)) throw StepFailure(ApplyFailure.ReconnectTimeout)
                    step = ApplyStep.SendSet
                }
            }
        }

        /** 저장 이후. 취소할 수 없다. */
        private suspend fun postSave(): ApplyResult {
            step = ApplyStep.Save
            report("저장")
            val saveReply = execWithRetry(BridgeCommands.save())
            when (val parsed = ResponseParsers.parseSaveResult(saveReply)) {
                is ResponseParsers.SaveResult.Saved -> saved = true
                is ResponseParsers.SaveResult.Failed -> throw StepFailure(ApplyFailure.SaveFailed(parsed.detail))
                ResponseParsers.SaveResult.Unknown -> throw StepFailure(
                    ApplyFailure.Unexpected(BridgeCommands.save(), saveReply),
                )
            }
            if (request.saveOnly) {
                return finish(ApplyOutcome.SavedOnly, "ok", after = readAfter())
            }

            step = ApplyStep.Reboot
            report("재부팅")
            rebootStartMs = nowMillis()
            val rebootReply = try {
                channel.execThenDisconnect(BridgeCommands.reboot())
            } catch (timeout: BridgeChannelException.Timeout) {
                throw StepFailure(ApplyFailure.Timeout(timeout.redactedLine))
            }
            when (val parsed = ResponseParsers.parseRebootResult(rebootReply)) {
                ResponseParsers.RebootResult.Rebooting -> Unit
                is ResponseParsers.RebootResult.Refused -> throw StepFailure(ApplyFailure.RebootRefused(parsed.text))
                ResponseParsers.RebootResult.Unknown -> throw StepFailure(
                    ApplyFailure.Unexpected(BridgeCommands.reboot(), rebootReply),
                )
            }

            step = ApplyStep.WaitReady
            report("재연결 대기")
            // 보드가 링크를 끊기 전에 waitReady 를 부르면 죽어 가는 옛 링크를 "준비됨"으로 본다. 끊김을 먼저 기다린다.
            withTimeoutOrNull(linkDropTimeout) { channel.state.first { it !is ChannelState.Connected } }
            if (!channel.waitReady(reconnectTimeout)) {
                bootNote = BootNote.timeout(nowMillis() - rebootStartMs)
                throw StepFailure(ApplyFailure.ReconnectTimeout)
            }
            bootNote = BootNote.ok(nowMillis() - rebootStartMs, readStatus())

            step = ApplyStep.VerifyWifi
            report("Wi-Fi 연결 확인")
            val expectedIp = request.target.ip
            val up = withTimeoutOrNull(wifiTimeout) { pollWifi(expectedIp) } == true
            if (!up) {
                val wifi = lastStatus?.wifi
                if (wifi?.up == true) {
                    throw StepFailure(ApplyFailure.WifiIpMismatch(wifi.ip.orEmpty(), expectedIp.orEmpty()))
                }
                throw StepFailure(ApplyFailure.WifiDown(lastStatus?.net?.lastReason))
            }

            step = ApplyStep.Done
            report("완료")
            return finish(ApplyOutcome.Applied, "ok", after = readAfter())
        }

        private suspend fun sendAll(target: BridgeConfig, keys: List<ConfigKey>) {
            step = ApplyStep.SendSet
            keys.forEachIndexed { index, key ->
                currentCoroutineContext().ensureActive()
                report("설정 전송 ${index + 1}/${keys.size}", index + 1, keys.size)
                val value = target.wireValue(key)
                    ?: throw StepFailure(ApplyFailure.InvalidValue(key, "값이 없습니다."))
                val line = when (val built = BridgeCommands.set(channel.kind, key, value)) {
                    is BridgeCommands.SetLine.Ok -> built.line
                    is BridgeCommands.SetLine.TooLong -> throw StepFailure(
                        ApplyFailure.InvalidValue(key, "명령이 너무 깁니다 (${built.bytes}바이트, 최대 ${built.max})."),
                    )
                    is BridgeCommands.SetLine.Invalid -> throw StepFailure(ApplyFailure.InvalidValue(key, built.reason))
                }
                val reply = execWithRetry(line)
                when (ResponseParsers.parseSetResult(reply)) {
                    is ResponseParsers.SetResult.Ok -> Unit
                    is ResponseParsers.SetResult.Invalid -> throw StepFailure(ApplyFailure.InvalidValue(key, reply))
                    ResponseParsers.SetResult.Unknown -> throw StepFailure(
                        ApplyFailure.Unexpected(Masking.redactLine(line), reply),
                    )
                }
            }
        }

        private suspend fun readBack(target: BridgeConfig, keys: List<ConfigKey>) {
            step = ApplyStep.ReadBack
            report("되읽기 대조")
            val text = execWithRetry(BridgeCommands.cfg())
            val device = ResponseParsers.parseCfg(text)?.config
                ?: throw StepFailure(ApplyFailure.UnreadableConfig(text))
            val mismatched = keys.filter { key -> target.canonical(key) != device.canonical(key) }
            if (mismatched.isNotEmpty()) throw StepFailure(ApplyFailure.ReadBackMismatch(mismatched))
        }

        /** 보내는 키 순서: 7.1 의 고정 순서. 비밀번호는 입력이 있을 때만, DNS 키는 펌웨어가 지원할 때만. */
        private fun keysToSend(target: BridgeConfig): List<ConfigKey> = ConfigKey.entries.filter { key ->
            when (key) {
                ConfigKey.PASS -> target.pass != null
                ConfigKey.DNS, ConfigKey.DNS2 -> request.before.capabilities.dnsKeys
                else -> true
            }
        }

        /** 명령 하나. 시간 초과는 한 번 더 보낸 뒤 실패로 본다. 끊김은 그대로 올린다. */
        private suspend fun execWithRetry(line: String): String {
            repeat(2) {
                try {
                    return channel.exec(line)
                } catch (timeout: BridgeChannelException.Timeout) {
                    // 한 번 더 시도
                }
            }
            throw StepFailure(ApplyFailure.Timeout(Masking.redactLine(line)))
        }

        /** 상태 읽기 한 번. 응답이 없거나 끊기면 null. */
        private suspend fun readStatus(): BridgeStatus? = try {
            ResponseParsers.parseStat(channel.exec(BridgeCommands.stat()))
        } catch (failure: BridgeChannelException) {
            null
        }

        /** 연결 확인 주기 폴링. 조건을 만족하면 true 를 주고, 아니면 끝나지 않는다(시간 제한은 호출 측). */
        private suspend fun pollWifi(expectedIp: String?): Boolean {
            while (true) {
                val status = readStatus()
                if (status != null) lastStatus = status
                val wifi = status?.wifi
                if (wifi != null && wifi.up && (expectedIp == null || wifi.ip == expectedIp)) return true
                delay(wifiPollInterval)
            }
        }

        private suspend fun readAfter(): DeviceSnapshot? = try {
            ReadDevice(channel, nowMillis).read()
        } catch (failure: BridgeChannelException) {
            null
        }

        private fun finish(outcome: ApplyOutcome, historyResult: String, after: DeviceSnapshot?): ApplyResult =
            ApplyResult(outcome, historyResult, changed, saved, after, bootNote)

        private fun report(message: String, done: Int = 0, total: Int = 0) {
            onProgress(WorkflowProgress.Step(step, message, done, total))
        }
    }

    /** 저장 전후 실패를 실어 나르는 내부 예외 (정상 흐름의 결과가 아니다). */
    private class StepFailure(val failure: ApplyFailure) : RuntimeException()
}
