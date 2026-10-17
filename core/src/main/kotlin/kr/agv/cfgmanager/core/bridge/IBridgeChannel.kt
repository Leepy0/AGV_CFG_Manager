package kr.agv.cfgmanager.core.bridge

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 연결 방식. 명령 문법이 조금 다르다(USB 는 인용 · 프롬프트). */
enum class ChannelKind { Ble, UsbSerial, Fake }

sealed interface ChannelState {
    data object Disconnected : ChannelState
    data object Connecting : ChannelState
    data class Connected(val deviceId: String, val name: String, val fw: String) : ChannelState
    data object Rebooting : ChannelState
}

/** 채널 오류. 메시지에 명령 원문(비밀번호 포함 가능)을 넣지 않는다. */
sealed class BridgeChannelException(message: String) : Exception(message) {
    /** [redactedLine] 은 이미 가린 명령 줄. */
    class Timeout(val redactedLine: String) : BridgeChannelException("응답 시간 초과")
    class Disconnected : BridgeChannelException("연결이 끊겼습니다")
    class NotConnected : BridgeChannelException("장치에 연결돼 있지 않습니다")
}

/**
 * 브리지와 통신하는 채널 (docs/DEVELOPMENT_PLAN.md 6.2). 한 번에 명령 하나만 처리한다.
 * BLE · USB · 가짜 장치가 같은 인터페이스를 구현한다.
 */
interface IBridgeChannel {
    val kind: ChannelKind

    /** 연결 상태. */
    val state: StateFlow<ChannelState>

    /** 콘솔 화면용 원문 (마스킹은 호출 측). */
    val raw: SharedFlow<String>

    /**
     * 명령 한 줄을 보내고 응답 텍스트를 돌려준다. 응답 끝은 [ReplyRule] 로 판정한다.
     * @throws BridgeChannelException 시간 초과 · 연결 끊김 · 미연결
     */
    suspend fun exec(line: String, timeout: Duration = 3.seconds): String

    /** 응답 뒤 끊기는 명령(`reboot`). 응답 한 줄을 받고 끊김을 허용한다. */
    suspend fun execThenDisconnect(line: String): String

    /** 재부팅 후 같은 장치가 다시 준비될 때까지(연결 · 로그인 포함). 준비되면 true. */
    suspend fun waitReady(timeout: Duration): Boolean

    suspend fun disconnect()
}
