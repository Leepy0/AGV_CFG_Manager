package kr.agv.cfgmanager.ui.boards

import kr.agv.cfgmanager.session.BoardRow
import kr.agv.cfgmanager.session.ConnectFailure
import kr.agv.cfgmanager.session.ConnectStage

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

/** 프로필 고르기 한 항목. */
data class ProfileOption(val id: String, val name: String)

/** 연결 시트. [stage] 는 진행 중 단계, [failure] 는 마지막 실패. [password] 는 활성 프로필의 BLE 비밀번호로 시작한다. */
data class ConnectSheet(
    val row: BoardRow,
    val password: String = "",
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
    /** 연속 세팅에서 이미 끝낸 보드의 BLE 주소(대문자). "완료" 표식. */
    val doneAddresses: Set<String> = emptySet(),
    /** BLE 주소(대문자) → 이력에 마지막으로 저장된 호기. "이력: 07호기" 표식. */
    val historyAgv: Map<String, Int> = emptyMap(),
    /** 연속 세팅이 켜져 있을 때 상단 배너 ("다음: 8호기 · 본사 1공장"). */
    val batchBanner: String? = null,
    /** 현장 프로필 선택 (보드를 찾기 전에 고른다: 연결 로그인 비밀번호와 채울 값이 여기서 정해진다). */
    val profiles: List<ProfileOption> = emptyList(),
    val selectedProfileId: String? = null,
    /** 고른 프로필의 이력 기준 다음 호기 번호 제안. */
    val nextAgv: Int? = null,
) {
    /** 목록이 있으면 오류는 위 배너로 보인다 (USB 행은 블루투스가 꺼져도 나온다). 목록이 없고 오류면 Error. */
    val view: BoardsView
        get() = when {
            rows.isNotEmpty() -> BoardsView.Content
            error != null -> BoardsView.Error
            scanState == ScanState.Scanning -> BoardsView.Loading
            else -> BoardsView.Empty
        }

    /** 보이는 순서: 끝낸 보드는 맨 아래, 설정 없는 보드(01 이고 이력 없음)는 위, 그다음 신호 강한 순. */
    fun arrangedRows(): List<BoardRow> = rows.sortedWith(
        compareBy<BoardRow>(
            { it.address.uppercase() in doneAddresses },
            { !isUnset(it) },
            { -it.rssi },
        ),
    )

    fun isUnset(row: BoardRow): Boolean =
        historyAgv[row.address.uppercase()] == null && (row.agvNumber == null || row.agvNumber == 1)
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
