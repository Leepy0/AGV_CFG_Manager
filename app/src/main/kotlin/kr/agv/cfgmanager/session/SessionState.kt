package kr.agv.cfgmanager.session

import kr.agv.cfgmanager.core.bridge.CfgState
import kr.agv.cfgmanager.core.bridge.DeviceSnapshot
import kr.agv.cfgmanager.core.bridge.WifiStatus

/** 연결 상태 (상단 칩의 원천). */
enum class LinkStatus { Disconnected, Connecting, Connected, Rebooting, Reconnecting }

/** 앱 전체가 보는 연결 상태. 화면 회전과 무관하게 세션이 쥐고 있다. */
data class SessionState(
    val link: LinkStatus = LinkStatus.Disconnected,
    val scanning: Boolean = false,
    val address: String? = null,
    val name: String? = null,
    val fw: String? = null,
    val snapshot: DeviceSnapshot? = null,
    val reconnectLeftSec: Int? = null,
    val notice: String? = null,
)

enum class ChipTone { Neutral, Ok, Warn }

/** 상단 연결 칩 문구. 색은 [tone] 으로만 정하고 글자는 항상 함께 보인다. */
data class ChipUi(val label: String, val tone: ChipTone, val detail: String?)

object ConnectionChipMapper {
    fun map(state: SessionState, nowMs: Long, gatewayUp: Boolean = false): ChipUi = when (state.link) {
        LinkStatus.Connected -> ChipUi(
            label = "연결됨 · ${state.name ?: "이름 미확인"} · fw ${state.fw ?: "?"} · cfg ${cfgLabel(state.snapshot?.cfgState)}",
            tone = ChipTone.Ok,
            detail = connectedDetail(state, nowMs, gatewayUp),
        )
        LinkStatus.Connecting -> ChipUi("연결 중", ChipTone.Neutral, null)
        LinkStatus.Rebooting -> ChipUi("재부팅 대기", ChipTone.Neutral, "보드가 다시 켜질 때까지 기다립니다")
        LinkStatus.Reconnecting -> ChipUi(
            label = "끊김 — 다시 연결 중 (${state.reconnectLeftSec ?: 0}초)",
            tone = ChipTone.Warn,
            detail = null,
        )
        LinkStatus.Disconnected -> when {
            state.scanning -> ChipUi("찾는 중", ChipTone.Neutral, null)
            // 아직 연결한 적이 없거나 사용자가 끊은 평상시 상태는 경고가 아니다 (평상시는 중립). 끊긴 사유가 있을 때만 경고다.
            state.notice != null -> ChipUi("끊김", ChipTone.Warn, state.notice)
            else -> ChipUi("연결된 보드 없음", ChipTone.Neutral, null)
        }
    }

    /**
     * 연결됨 칩의 둘째 줄: Wi-Fi 상태(마지막 stat) · 게이트웨이 응답 · 마지막 읽기.
     * 게이트웨이는 응답이 올 때만 적는다 (휴대폰이 다른 망에 있으면 응답이 없는 것이 정상이라 부정 표시는 하지 않는다).
     */
    fun connectedDetail(state: SessionState, nowMs: Long, gatewayUp: Boolean): String? {
        val parts = listOfNotNull(
            wifiText(state.snapshot?.status?.wifi),
            if (gatewayUp) "게이트웨이 응답" else null,
            state.snapshot?.readAt?.let { lastReadText(it, nowMs) },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun wifiText(wifi: WifiStatus?): String? = when {
        wifi == null -> null
        wifi.up -> "Wi-Fi 연결됨" + (wifi.rssi?.let { " ${it}dBm" } ?: "")
        else -> "Wi-Fi 끊김"
    }

    fun cfgLabel(state: CfgState?): String = when (state) {
        CfgState.SAVED -> "저장됨"
        CfgState.DEFAULT -> "기본값"
        CfgState.TRIAL -> "확인 대기"
        null -> "미확인"
    }

    fun lastReadText(readAtMs: Long, nowMs: Long): String {
        val seconds = ((nowMs - readAtMs) / 1000).coerceAtLeast(0)
        return "마지막 읽기 ${seconds}초 전"
    }
}
