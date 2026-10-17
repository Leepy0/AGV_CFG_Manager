package kr.agv.cfgmanager.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kotlin.time.Duration

/** USB 콘솔 주소의 접두어. BLE MAC 과 겹치지 않는다 (docs/DEVELOPMENT_PLAN.md 8.6). */
const val USB_ADDRESS_PREFIX = "usb:"

/** 스캔으로 본 보드 한 줄. [lastSeenMs] 는 마지막으로 광고를 받은 시각(epoch ms). USB 행은 [rssi] 를 0 으로 둔다. */
data class BoardRow(
    val address: String,
    val name: String,
    val rssi: Int,
    val lastSeenMs: Long,
) {
    /** 이름의 호기 번호 (`AGVBR-07-A1B2` → 7). 형식이 다르면 null. */
    val agvNumber: Int?
        get() = BoardName.agvNumber(name)

    /** USB 콘솔로 직결된 행인가. 신호 세기가 없고 로그인도 없다. */
    val isUsb: Boolean
        get() = address.startsWith(USB_ADDRESS_PREFIX)
}

/** 광고 이름 규칙 (docs/DEVELOPMENT_PLAN.md 3.2: `AGVBR-<호기 2자리>-<MAC 끝 4자리 hex>`). */
object BoardName {
    const val PREFIX = "AGVBR-"

    private val pattern = Regex("AGVBR-(\\d{1,2})-([0-9A-Fa-f]{4})")

    fun isBoardName(name: String): Boolean = name.startsWith(PREFIX)

    fun agvNumber(name: String): Int? = pattern.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()
}

/** 연결 3단계 중 어디를 하고 있는지. [Read] 는 세션이 장치를 읽는 단계다. */
enum class ConnectStage { Connect, Login, Read }

/** 채널이 마지막 `waitReady` 실패 사유를 알려 줄 때 구현한다. */
interface LinkReport {
    val failure: ConnectFailure?
}

/**
 * 보드를 찾고 채널을 만드는 원천. BLE 와 가짜 장치가 구현한다.
 * 화면은 이 인터페이스만 본다 (지금 연결 방식이 BLE 인지 가짜인지 모른다).
 */
interface BoardSource {
    /** 가짜 장치인가. 가짜면 권한 · 블루투스 준비 검사를 건너뛴다. */
    val isFake: Boolean

    /** [duration] 동안 스캔하며 약 2초마다 목록을 보낸다. 시간이 지나면 흐름이 끝난다. */
    fun discover(duration: Duration): Flow<List<BoardRow>>

    /** 주소의 채널. 아직 연결하지 않는다 (세션이 `waitReady` 를 부른다). */
    fun channel(
        address: String,
        name: String?,
        password: String,
        onStage: (ConnectStage) -> Unit,
    ): IBridgeChannel
}

/**
 * 디버그 빌드의 "가짜 브리지" 설정에 따라 BLE 와 가짜 원천을 고른다.
 * 실기 모드에서는 BLE 행 앞에 USB 행을 붙이고, USB 주소의 채널은 [usb] 가 만든다.
 */
class SwitchingBoardSource(
    private val ble: BoardSource,
    private val fake: BoardSource,
    private val usb: BoardSource,
    private val useFake: () -> Boolean,
) : BoardSource {
    override val isFake: Boolean
        get() = useFake()

    override fun discover(duration: Duration): Flow<List<BoardRow>> =
        if (useFake()) {
            fake.discover(duration)
        } else {
            combine(usb.discover(duration), ble.discover(duration)) { wired, wireless -> wired + wireless }
        }

    override fun channel(
        address: String,
        name: String?,
        password: String,
        onStage: (ConnectStage) -> Unit,
    ): IBridgeChannel = when {
        useFake() -> fake.channel(address, name, password, onStage)
        address.startsWith(USB_ADDRESS_PREFIX) -> usb.channel(address, name, password, onStage)
        else -> ble.channel(address, name, password, onStage)
    }
}
