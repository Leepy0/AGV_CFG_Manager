package kr.agv.cfgmanager.apply

import kr.agv.cfgmanager.core.bridge.WifiReason
import kr.agv.cfgmanager.core.workflows.ApplyFailure
import kr.agv.cfgmanager.core.workflows.ApplyOutcome
import kr.agv.cfgmanager.core.workflows.ApplyResult
import kr.agv.cfgmanager.core.workflows.ApplyStep

/** 결과 카드의 다음 행동. 버튼 문구와 동작은 화면에서 정한다. */
/** [NextAgv]: 연속 모드에서 다음 호기로 (보드 탭으로 돌아가며 번호를 넘긴다). [Restore]: 적용 직전 값으로 되돌린다. */
enum class ResultAction { NextAgv, NextBoard, Retry, Console, Reboot, Boards, CloseSetup, Restore, Dismiss }

/** 결과 카드 한 장의 문구. 무엇 · 왜 · 다음 행동 (docs/DEVELOPMENT_PLAN.md 9). */
data class ResultView(
    val title: String,
    val step: String?,
    val cause: String,
    val success: Boolean,
    val actions: List<ResultAction>,
)

/** 적용 결과를 카드 문구로 바꾼다. 예외 원문은 넣지 않는다. */
object ApplyMessages {

    fun stepLabel(step: ApplyStep): String = when (step) {
        ApplyStep.Validate -> "설정 확인"
        ApplyStep.SendSet -> "설정 전송"
        ApplyStep.ReadBack -> "되읽기"
        ApplyStep.Save -> "저장"
        ApplyStep.Reboot -> "재부팅"
        ApplyStep.WaitReady -> "재연결"
        ApplyStep.VerifyWifi -> "Wi-Fi 확인"
        ApplyStep.Done -> "완료"
    }

    /** [nextAgv] 가 있으면 연속 모드 중이다. 성공 카드의 기본 버튼은 [ResultAction.NextAgv] 가 된다. */
    fun view(result: ApplyResult, nextAgv: Int? = null, restorable: Boolean = false): ResultView {
        val base = baseView(result, nextAgv)
        val applied = result.outcome == ApplyOutcome.Applied || result.outcome == ApplyOutcome.SavedOnly
        if (!restorable || !applied) return base
        // 되돌리기는 확인 버튼 바로 앞의 맨 아래 보조 행동이다 (주요 행동과 떨어뜨린다).
        return base.copy(actions = base.actions.filter { it != ResultAction.Dismiss } + ResultAction.Restore + ResultAction.Dismiss)
    }

    private fun baseView(result: ApplyResult, nextAgv: Int?): ResultView = when (val outcome = result.outcome) {
        ApplyOutcome.Applied -> ResultView(
            title = "적용 완료",
            step = null,
            cause = "저장하고 재부팅했으며 Wi-Fi 에 연결됐습니다.",
            success = true,
            // 다음 보드로 가는 것이 현장에서 가장 흔한 다음 행동이다. 연속 모드가 아니어도 첫 버튼으로 둔다.
            actions = if (nextAgv != null) {
                listOf(ResultAction.NextAgv, ResultAction.CloseSetup, ResultAction.Dismiss)
            } else {
                listOf(ResultAction.NextBoard, ResultAction.CloseSetup, ResultAction.Dismiss)
            },
        )
        ApplyOutcome.SavedOnly -> ResultView(
            title = "저장했습니다 (재부팅 안 함)",
            step = null,
            cause = "재부팅해야 새 설정으로 동작합니다.",
            success = true,
            actions = if (nextAgv != null) {
                listOf(ResultAction.NextAgv, ResultAction.Reboot, ResultAction.Dismiss)
            } else {
                listOf(ResultAction.Reboot, ResultAction.NextBoard, ResultAction.Dismiss)
            },
        )
        ApplyOutcome.NoChange -> ResultView(
            title = "바뀐 값이 없습니다",
            step = null,
            cause = "보드의 설정이 이미 입력한 값과 같습니다.",
            success = true,
            actions = listOf(ResultAction.Dismiss),
        )
        is ApplyOutcome.Cancelled -> ResultView(
            title = "취소했습니다",
            step = stepLabel(outcome.step),
            cause = "저장 전이라 저장된 설정은 바뀌지 않았습니다. 보드의 편집 중인 값은 재부팅하면 사라집니다.",
            success = false,
            actions = listOf(ResultAction.Retry, ResultAction.Dismiss),
        )
        is ApplyOutcome.Failed -> failure(outcome.step, outcome.failure)
    }

    private fun failure(step: ApplyStep, failure: ApplyFailure): ResultView {
        val saved = step.ordinal >= ApplyStep.Save.ordinal
        return when (failure) {
            is ApplyFailure.NotReady -> ResultView(
                "적용할 수 없습니다", stepLabel(step), failure.reason, false,
                listOf(ResultAction.Boards, ResultAction.Dismiss),
            )
            is ApplyFailure.InvalidValue -> ResultView(
                "브리지가 ${failure.key.wire} 값을 거부했습니다",
                stepLabel(step),
                "저장하지 않았습니다. 세팅 탭에서 ${failure.key.wire} 항목을 고친 뒤 다시 적용하세요.",
                false,
                listOf(ResultAction.Retry, ResultAction.Console),
            )
            is ApplyFailure.Timeout -> ResultView(
                "장치가 응답하지 않습니다",
                stepLabel(step),
                "명령 ${failure.redactedLine} 에 응답이 없어 두 번 시도했습니다. 보드가 멀거나 설정 모드가 닫혔을 수 있습니다.",
                false,
                listOf(ResultAction.Retry, ResultAction.Console),
            )
            is ApplyFailure.Disconnected -> ResultView(
                "연결이 끊겼습니다",
                stepLabel(step),
                if (saved) {
                    "저장 여부가 확실하지 않습니다. 진단 탭의 상태(stat)로 확인하세요."
                } else {
                    "저장하지 않았습니다. 세팅 탭에서 다시 연결한 뒤 다시 적용하세요."
                },
                false,
                if (saved) listOf(ResultAction.Console, ResultAction.Boards) else listOf(ResultAction.Retry, ResultAction.Boards),
            )
            is ApplyFailure.ReadBackMismatch -> ResultView(
                "보낸 값과 장치의 편집 값이 다릅니다",
                stepLabel(step),
                "다른 값: ${failure.keys.joinToString { it.wire }}. 저장하지 않았습니다.",
                false,
                listOf(ResultAction.Retry, ResultAction.Console),
            )
            is ApplyFailure.UnreadableConfig -> ResultView(
                "장치 설정을 읽지 못했습니다",
                stepLabel(step),
                "cfg 응답을 해석하지 못했습니다. 저장하지 않았습니다. 콘솔에서 응답을 확인하세요.",
                false,
                listOf(ResultAction.Retry, ResultAction.Console),
            )
            is ApplyFailure.SaveFailed -> ResultView(
                "저장하지 못했습니다",
                stepLabel(step),
                "${failure.detail}. 재부팅하지 않았습니다.",
                false,
                listOf(ResultAction.Retry, ResultAction.Console),
            )
            is ApplyFailure.RebootRefused -> ResultView(
                "지금은 재부팅할 수 없습니다",
                stepLabel(step),
                "메인보드 펌웨어를 굽는 중입니다. 저장은 끝났습니다. 끝난 뒤 [재부팅] 을 누르세요.",
                false,
                listOf(ResultAction.Reboot, ResultAction.Console),
            )
            ApplyFailure.ReconnectTimeout -> ResultView(
                "재부팅 뒤 보드가 다시 보이지 않습니다",
                stepLabel(step),
                "저장은 끝났습니다. 전원과 거리를 확인하고 세팅 탭에서 다시 찾으세요.",
                false,
                listOf(ResultAction.Boards),
            )
            is ApplyFailure.WifiDown -> ResultView(
                "Wi-Fi 에 연결되지 않았습니다",
                stepLabel(step),
                "저장은 끝났습니다. ${wifiCause(failure.reason)}",
                false,
                listOf(ResultAction.Console),
            )
            is ApplyFailure.WifiIpMismatch -> ResultView(
                "Wi-Fi IP 가 설정과 다릅니다",
                stepLabel(step),
                "장치 IP ${failure.actual}, 설정 IP ${failure.expected}. 저장은 끝났습니다.",
                false,
                listOf(ResultAction.Console),
            )
            is ApplyFailure.Unexpected -> ResultView(
                "예상하지 못한 응답입니다",
                stepLabel(step),
                "${failure.redactedLine} 의 응답을 해석하지 못했습니다. 콘솔에서 확인하세요.",
                false,
                listOf(ResultAction.Console),
            )
        }
    }

    /** Wi-Fi 끊김 사유 문구 (9.3). 사유를 모르면 그렇다고 말한다. */
    fun wifiCause(reason: Int?): String {
        if (reason == null) return "끊김 사유를 알 수 없습니다. 진단 탭에서 상태를 확인하세요."
        val info = WifiReason.describe(reason)
        return "사유 ${info.code}${info.name?.let { " ($it)" }.orEmpty()}: ${info.message}"
    }
}
