package kr.agv.cfgmanager.ui.boards

import kr.agv.cfgmanager.session.BoardRow
import kr.agv.cfgmanager.session.ConnectFailure
import kr.agv.cfgmanager.session.ConnectStage

/** 기본 BLE 비밀번호 (브리지 빌드 기본값). 프로필은 Phase 4 에서 추가된다. */
const val DEFAULT_BLE_PASSWORD = "agvsetup"

/** 스캔 진행 상태. */
enum class ScanState { Idle, Scanning, Done }

/** 스캔을 시작하지 못한 이유 (docs/DEVELOPMENT_PLAN.md 9.1). */
sealed interface ScanError {
    data class Permission(val permissions: List<String>) : ScanError
    data object BluetoothOff : ScanError
    data object LocationOff : ScanError
    data object Failed : ScanError
}

/** 보드 탭의 화면 상태 4가지 (Loading / Empty / Error / Content). */
enum class BoardsView { Loading, Empty, Error, Content }

/** 연결 시트. [stage] 는 진행 중 단계, [failure] 는 마지막 실패. */
data class ConnectSheet(
    val row: BoardRow,
    val password: String = DEFAULT_BLE_PASSWORD,
    val busy: Boolean = false,
    val stage: ConnectStage? = null,
    val failure: ConnectFailure? = null,
)

data class BoardsUi(
    val scanState: ScanState = ScanState.Idle,
    val rows: List<BoardRow> = emptyList(),
    val nowMs: Long = 0L,
    val scanStartedMs: Long = 0L,
    val error: ScanError? = null,
    val notice: String? = null,
    val sheet: ConnectSheet? = null,
) {
    val view: BoardsView
        get() = when {
            error != null -> BoardsView.Error
            rows.isNotEmpty() -> BoardsView.Content
            scanState == ScanState.Scanning -> BoardsView.Loading
            else -> BoardsView.Empty
        }
}

/** 스캔 오류 문구 (무엇 · 왜 · 다음 행동). 예외 원문은 넣지 않는다. */
object ScanErrorText {
    fun title(error: ScanError): String = when (error) {
        is ScanError.Permission -> "근처 기기(블루투스) 권한이 없어 보드를 찾을 수 없습니다."
        ScanError.BluetoothOff -> "블루투스가 꺼져 있습니다. 블루투스를 켜야 보드를 찾을 수 있습니다."
        ScanError.LocationOff -> "Android 11 이하는 위치 서비스가 켜져 있어야 BLE 스캔이 됩니다."
        ScanError.Failed -> "블루투스 스캔을 시작하지 못했습니다. 잠시 후 다시 시도하세요."
    }
}

/** 스캔 목록이 비었을 때 안내 (BOOT 버튼 · 설정 모드). */
object BoardsHint {
    const val BOOT_HINT = "안 보이면: 보드의 BOOT 버튼을 3초 누르면 설정 모드가 열립니다. 첫 설치 보드는 전원만 넣으면 열립니다."
    const val SETUP_WINDOW_HINT = "설정 모드는 휴대폰 없이 Wi-Fi 연결 상태로 5분이 지나면 닫힙니다."
}
