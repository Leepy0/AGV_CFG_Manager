package kr.agv.cfgmanager.core.workflows

import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.BridgeCommands
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kr.agv.cfgmanager.core.bridge.ResponseParsers

/** 공장 초기화 결과 (docs/DEVELOPMENT_PLAN.md F-A5). */
sealed interface FactoryResetOutcome {
    /** 저장된 설정을 지우고 재부팅을 요청했다. 보드는 빌드 기본값으로 다시 시작한다. */
    data object Rebooting : FactoryResetOutcome

    /** 설정은 지웠지만 재부팅이 거부됐다 (메인보드 굽기 중). [text] 는 보드 응답 한 줄. */
    data class RebootRefused(val text: String) : FactoryResetOutcome

    /** 설정은 지웠지만 재부팅 응답을 확인하지 못했다. 보드가 다시 보이는지 확인해야 한다. */
    data class RebootUnknown(val reason: String) : FactoryResetOutcome

    /** 설정을 지우지 못했다. 보드 설정은 그대로다. */
    data class EraseFailed(val reason: String) : FactoryResetOutcome
}

/** 이력 `result` 값 (부록 B-3 · [kr.agv.cfgmanager.core.history.HistoryEntry.result]). */
val FactoryResetOutcome.historyResult: String
    get() = when (this) {
        FactoryResetOutcome.Rebooting -> "ok"
        is FactoryResetOutcome.RebootRefused -> "reboot_refused"
        is FactoryResetOutcome.RebootUnknown -> "reboot_unknown"
        is FactoryResetOutcome.EraseFailed -> "erase_failed"
    }

/**
 * 공장 초기화 흐름: `defaults` (저장 설정 삭제) → `reboot`.
 * 호출 측이 세션의 독점 구간 안에서 [channel] 을 넘긴다. 설정 삭제가 성공한 뒤에는 재부팅 실패도 "지워짐" 으로 본다.
 */
object FactoryResetWorkflow {

    suspend fun run(channel: IBridgeChannel): FactoryResetOutcome {
        val erase = try {
            channel.exec(BridgeCommands.defaults())
        } catch (failure: BridgeChannelException) {
            return FactoryResetOutcome.EraseFailed(reasonOf(failure))
        }
        val eraseLine = firstLine(erase)
        if (!eraseLine.startsWith(ERASED_PREFIX)) {
            return FactoryResetOutcome.EraseFailed(eraseLine.ifEmpty { "응답이 없습니다" })
        }
        return try {
            val text = channel.execThenDisconnect(BridgeCommands.reboot())
            when (val parsed = ResponseParsers.parseRebootResult(text)) {
                ResponseParsers.RebootResult.Rebooting -> FactoryResetOutcome.Rebooting
                is ResponseParsers.RebootResult.Refused -> FactoryResetOutcome.RebootRefused(parsed.text)
                ResponseParsers.RebootResult.Unknown -> FactoryResetOutcome.RebootUnknown("재부팅 응답을 해석하지 못했습니다")
            }
        } catch (failure: BridgeChannelException.Disconnected) {
            // 재부팅하면서 연결이 먼저 끊기는 경우는 정상 경로다. 다시 붙는 것은 세션이 맡는다.
            FactoryResetOutcome.Rebooting
        } catch (failure: BridgeChannelException) {
            FactoryResetOutcome.RebootUnknown(reasonOf(failure))
        }
    }

    private const val ERASED_PREFIX = "saved config erased"

    private fun firstLine(text: String): String = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

    private fun reasonOf(failure: BridgeChannelException): String = when (failure) {
        is BridgeChannelException.Timeout -> "응답이 없습니다"
        is BridgeChannelException.Disconnected -> "연결이 끊겼습니다"
        is BridgeChannelException.NotConnected -> "연결된 장치가 없습니다"
    }
}
