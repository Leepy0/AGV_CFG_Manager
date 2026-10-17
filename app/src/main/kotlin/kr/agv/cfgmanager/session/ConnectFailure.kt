package kr.agv.cfgmanager.session

/** 연결 실패 사유 (docs/DEVELOPMENT_PLAN.md 9.1). 메시지에 비밀번호를 넣지 않는다. */
sealed interface ConnectFailure {
    /** 광고는 있으나 NUS 서비스가 없다. */
    data object NotBridge : ConnectFailure

    /** 비밀번호가 틀렸다. [attemptsLeft] 는 앱이 세는 남은 시도 횟수(없으면 null). */
    data class WrongPassword(val attemptsLeft: Int?) : ConnectFailure

    /** 브리지가 5회 연속 실패로 끊었다. */
    data object TooManyFailures : ConnectFailure

    /** GATT 133 이 반복된다. 다른 휴대폰이 연결 중이거나 거리가 멀 수 있다. */
    data object GattUnavailable : ConnectFailure

    /** 시간 안에 광고 · 응답을 보지 못했다 (설정 모드 닫힘 등). */
    data object Timeout : ConnectFailure

    /** 그 밖의 실패. [detail] 은 사람이 읽는 짧은 설명이다 (예외 원문 금지). */
    data class Other(val detail: String) : ConnectFailure
}

/** 실패 사유를 사용자 문구로 바꾼다: 무엇 · 왜 · 다음 행동. */
object ConnectFailureText {
    fun message(failure: ConnectFailure): String = when (failure) {
        ConnectFailure.NotBridge -> "AGV 브리지가 아닌 기기입니다. 목록에서 다른 보드를 고르세요."
        is ConnectFailure.WrongPassword -> {
            val left = failure.attemptsLeft
            if (left != null) {
                "BLE 비밀번호가 틀렸습니다 (남은 시도 ${left}회). 프로필의 BLE 비밀번호를 확인하세요."
            } else {
                "BLE 비밀번호가 틀렸습니다. 확인한 뒤 다시 입력하세요."
            }
        }
        ConnectFailure.TooManyFailures ->
            "5회 연속 실패로 보드가 연결을 끊었습니다. 비밀번호를 확인한 뒤 다시 연결하면 재시도할 수 있습니다."
        ConnectFailure.GattUnavailable ->
            "보드에 연결하지 못했습니다. 다른 휴대폰이 연결돼 있거나 거리가 멀 수 있습니다. 가까이 두고 다시 시도하세요."
        ConnectFailure.Timeout ->
            "보드가 응답하지 않습니다. 설정 모드가 닫혀 있으면 보드의 BOOT 버튼을 3초 누른 뒤 다시 찾으세요."
        is ConnectFailure.Other -> "연결하지 못했습니다: ${failure.detail}. 다시 시도하세요."
    }
}
