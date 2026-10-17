package kr.agv.cfgmanager.apply

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kr.agv.cfgmanager.core.history.HistoryEntry
import kr.agv.cfgmanager.core.workflows.FactoryResetOutcome
import kr.agv.cfgmanager.core.workflows.FactoryResetWorkflow
import kr.agv.cfgmanager.core.workflows.historyResult
import kr.agv.cfgmanager.data.HistoryStore
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 초기화 대상 보드 (확인 문구와 이력에 쓴다). */
data class ResetTarget(
    val agv: Int?,
    val bleAddress: String?,
    val mac: String?,
    val deviceName: String?,
    val fw: String?,
)

/** 공장 초기화 실행 결과. [historyFailed] 이면 결과는 유효하지만 이력 파일에 쓰지 못했다. */
data class ResetRun(val outcome: FactoryResetOutcome, val historyFailed: Boolean)

/**
 * 공장 초기화를 세션의 독점 구간에서 실행하고 이력(`action=defaults`)을 남긴다 (docs/DEVELOPMENT_PLAN.md F-A5).
 * [exclusive] 는 `BridgeSession.withExclusive` 를 감싼 함수다. 채널이 없으면 아무것도 보내지 않고 이력도 남기지 않는다.
 */
class FactoryResetRunner(
    private val exclusive: suspend (suspend (IBridgeChannel) -> FactoryResetOutcome) -> FactoryResetOutcome,
    private val history: HistoryStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val isFake: () -> Boolean = { false },
) {
    suspend fun run(target: ResetTarget): ResetRun {
        val outcome = try {
            exclusive { channel -> FactoryResetWorkflow.run(channel) }
        } catch (notConnected: BridgeChannelException.NotConnected) {
            return ResetRun(FactoryResetOutcome.EraseFailed("연결된 장치가 없습니다"), historyFailed = false)
        } catch (lost: BridgeChannelException) {
            FactoryResetOutcome.EraseFailed("연결이 끊겼습니다")
        }
        val historyOk = withContext(NonCancellable + Dispatchers.IO) {
            try {
                history.append(entry(target, outcome))
                true
            } catch (failure: IOException) {
                false
            }
        }
        return ResetRun(outcome, historyFailed = !historyOk)
    }

    private fun entry(target: ResetTarget, outcome: FactoryResetOutcome): HistoryEntry = HistoryEntry(
        at = Instant.ofEpochMilli(clock()).truncatedTo(ChronoUnit.SECONDS).toString(),
        profileId = null,
        profileName = null,
        agv = target.agv,
        bleAddress = target.bleAddress,
        mac = target.mac,
        deviceName = target.deviceName,
        ip = null,
        ssid = null,
        fw = target.fw,
        channel = if (isFake()) "fake" else "ble",
        action = "defaults",
        result = outcome.historyResult,
        rssi = null,
        note = when (outcome) {
            FactoryResetOutcome.Rebooting -> "저장 설정 삭제 후 재부팅"
            else -> ""
        },
    )
}
