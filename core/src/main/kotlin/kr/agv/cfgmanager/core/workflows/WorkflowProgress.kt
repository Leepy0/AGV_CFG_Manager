package kr.agv.cfgmanager.core.workflows

import kr.agv.cfgmanager.core.bridge.BridgeConfig
import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.core.bridge.DeviceSnapshot

/** 적용 워크플로 단계 (docs/DEVELOPMENT_PLAN.md 7.1). */
enum class ApplyStep(val label: String) {
    Validate("설정 확인"),
    SendSet("설정 전송"),
    ReadBack("되읽기 대조"),
    Save("저장"),
    Reboot("재부팅"),
    WaitReady("재연결 대기"),
    VerifyWifi("Wi-Fi 연결 확인"),
    Done("완료"),
}

/** 진행 이벤트. [Step.done] / [Step.total] 로 퍼센트를 그릴 수 있다 (SendSet 에서만 값이 있다). */
sealed interface WorkflowProgress {
    data class Step(
        val step: ApplyStep,
        val message: String,
        val done: Int = 0,
        val total: Int = 0,
    ) : WorkflowProgress

    data class Finished(val result: ApplyResult) : WorkflowProgress
}

/** 적용 요청. [before] 는 적용 직전 장치 읽기 결과, [target] 은 검증을 통과한 목표 설정. */
data class ApplyRequest(
    val before: DeviceSnapshot,
    val target: BridgeConfig,
    val saveOnly: Boolean = false,
)

sealed interface ApplyOutcome {
    /** 바뀐 키가 없어 아무것도 보내지 않았다. */
    data object NoChange : ApplyOutcome

    /** 저장 · 재부팅 · Wi-Fi 확인까지 끝났다. */
    data object Applied : ApplyOutcome

    /** 저장만 했다 ([ApplyRequest.saveOnly]). */
    data object SavedOnly : ApplyOutcome

    data class Failed(val step: ApplyStep, val failure: ApplyFailure) : ApplyOutcome

    /** 저장 전 단계(SendSet · ReadBack)에서 취소됐다. */
    data class Cancelled(val step: ApplyStep) : ApplyOutcome
}

sealed interface ApplyFailure {
    /** 적용할 수 없는 상태 (미연결, 현재 설정을 모름). */
    data class NotReady(val reason: String) : ApplyFailure

    /** 브리지가 값을 거부했다. [reply] 는 장치 응답 원문(비밀번호 없음). */
    data class InvalidValue(val key: ConfigKey, val reply: String) : ApplyFailure

    /** 재시도 뒤에도 응답이 없다. [redactedLine] 은 가린 명령. */
    data class Timeout(val redactedLine: String) : ApplyFailure

    /** 연결이 끊겼고 다시 붙는 데 실패했거나 재시도 횟수를 넘었다. */
    data class Disconnected(val step: ApplyStep) : ApplyFailure

    /** 되읽기 값이 목표와 다르다. */
    data class ReadBackMismatch(val keys: List<ConfigKey>) : ApplyFailure

    /** `cfg` 응답을 해석하지 못했다. */
    data class UnreadableConfig(val reply: String) : ApplyFailure

    data class SaveFailed(val detail: String) : ApplyFailure

    /** 메인보드 굽는 중이라 재부팅을 거부했다. 저장은 끝났다. */
    data class RebootRefused(val reply: String) : ApplyFailure

    /** 재부팅 뒤 같은 보드가 다시 나타나지 않았다. 저장은 끝났다. */
    data object ReconnectTimeout : ApplyFailure

    /** Wi-Fi 가 연결되지 않았다. 저장은 끝났다. [reason] 은 `last_reason` (알 수 없으면 null). */
    data class WifiDown(val reason: Int?) : ApplyFailure

    /** Wi-Fi 는 연결됐지만 IP 가 목표와 다르다. */
    data class WifiIpMismatch(val actual: String, val expected: String) : ApplyFailure

    /** 기대하지 않은 응답. */
    data class Unexpected(val redactedLine: String, val reply: String) : ApplyFailure
}

/** 이력 `result` 값 (부록 B-3). */
fun ApplyFailure.historyCode(): String = when (this) {
    is ApplyFailure.NotReady -> "not_ready"
    is ApplyFailure.InvalidValue -> "invalid:${key.wire}"
    is ApplyFailure.Timeout -> "timeout"
    is ApplyFailure.Disconnected -> "disconnected"
    is ApplyFailure.ReadBackMismatch -> "readback_mismatch"
    is ApplyFailure.UnreadableConfig -> "readback_mismatch"
    is ApplyFailure.SaveFailed -> "save_failed"
    is ApplyFailure.RebootRefused -> "reboot_refused"
    ApplyFailure.ReconnectTimeout -> "reconnect_timeout"
    is ApplyFailure.WifiDown -> "wifi_down:${reason ?: "unknown"}"
    is ApplyFailure.WifiIpMismatch -> "wifi_ip_mismatch"
    is ApplyFailure.Unexpected -> "unexpected"
}

/**
 * 적용 결과.
 * [saved] 는 `save` 가 성공했는지(재부팅 실패 · Wi-Fi 실패여도 true 일 수 있다). [after] 는 가능할 때 다시 읽은 장치 상태.
 */
data class ApplyResult(
    val outcome: ApplyOutcome,
    val historyResult: String,
    val changedKeys: Set<ConfigKey>,
    val saved: Boolean,
    val after: DeviceSnapshot?,
    /** 재부팅 확인 메모 ([BootNote]). 재부팅 전에 실패했으면 null. */
    val bootNote: String? = null,
)
