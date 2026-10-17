package kr.agv.cfgmanager.ui.diagnostics

import kr.agv.cfgmanager.core.bridge.BridgeStatus
import kr.agv.cfgmanager.core.bridge.CfgState
import kr.agv.cfgmanager.core.bridge.SetupMode
import kr.agv.cfgmanager.core.bridge.WifiReason
import kr.agv.cfgmanager.session.ConnectionChipMapper
import kr.agv.cfgmanager.ble.SignalGrade

/** 카드 한 줄의 강조. 색이 아니라 글자로도 같은 뜻이 전달돼야 한다. */
enum class Emphasis { Normal, Ok, Warn, Alarm }

data class StatusItem(val label: String, val value: String, val emphasis: Emphasis = Emphasis.Normal)

/** 진단 상태 카드 (docs/DEVELOPMENT_PLAN.md F-D2 · 9.3). */
data class StatusCard(
    val title: String,
    val emphasis: Emphasis,
    val items: List<StatusItem>,
    val hints: List<String>,
)

object StatusCardBuilder {

    /** [status] 가 없으면 아직 읽지 않은 상태다. */
    fun build(status: BridgeStatus?, cfgState: CfgState?, fwFallback: String?): StatusCard {
        if (status == null) {
            return StatusCard(
                title = "상태를 아직 읽지 않았습니다",
                emphasis = Emphasis.Normal,
                items = emptyList(),
                hints = listOf("[새로고침]을 누르면 stat 을 다시 읽습니다."),
            )
        }
        val wifi = status.wifi
        val net = status.net
        val wifiUp = wifi?.up
        val title = when (wifiUp) {
            true -> "Wi-Fi 연결됨"
            false -> "Wi-Fi 끊김"
            null -> "Wi-Fi 정보 없음"
        }
        val emphasis = when (wifiUp) {
            true -> Emphasis.Ok
            false -> Emphasis.Alarm
            null -> Emphasis.Warn
        }

        val items = mutableListOf<StatusItem>()
        items += StatusItem("펌웨어", "${status.fw ?: fwFallback ?: "?"} · 슬롯 ${status.part ?: "?"} · OTA ${status.ota ?: "?"}")
        items += StatusItem("설정", cfgText(status.cfgState ?: cfgState))
        items += StatusItem(
            "Wi-Fi",
            if (wifiUp == true) "UP" else if (wifiUp == false) "DOWN" else "?",
            if (wifiUp == true) Emphasis.Ok else if (wifiUp == false) Emphasis.Alarm else Emphasis.Warn,
        )
        if (wifi != null && wifi.up) {
            val rssi = wifi.rssi
            items += StatusItem(
                "신호",
                if (rssi != null) "$rssi dBm · ${SignalGrade.of(rssi).label}" else "—",
            )
        }
        wifi?.ip?.let { items += StatusItem("IP", it) }
        wifi?.channel?.let { items += StatusItem("채널", it.toString()) }
        if (net != null) {
            items += StatusItem("송신", "${net.tx} (오류 ${net.txErr} · 오프라인 ${net.txOffline})")
            items += StatusItem("수신", "${net.rx} (잘못 ${net.rxBad} · 다른 서버 ${net.rxForeign})")
            items += StatusItem("끊김 횟수", net.disconnects.toString())
            val reason = if (net.lastReason == 0) {
                "없음"
            } else {
                val info = WifiReason.describe(net.lastReason)
                "${net.lastReason}${info.name?.let { " $it" } ?: ""} — ${info.message}"
            }
            items += StatusItem("마지막 끊김 사유", reason, if (net.lastReason == 0) Emphasis.Normal else Emphasis.Warn)
        }
        status.setup?.let { setup ->
            val mode = when (setup.mode) {
                SetupMode.On -> "열림"
                SetupMode.Off -> "닫힘"
                SetupMode.Opening -> "여는 중"
                SetupMode.Unavailable -> "이 빌드에서 없음"
            }
            items += StatusItem("설정 모드", listOfNotNull(mode, setup.name).joinToString(" · "))
        }
        status.dsp?.let { dsp ->
            items += StatusItem(
                "메인보드",
                dsp.state,
                if (dsp.isNoPartition) Emphasis.Warn else Emphasis.Normal,
            )
        }

        val hints = mutableListOf<String>()
        if (wifiUp == false && net != null) {
            when (net.lastReason) {
                201 -> hints += "AP 를 찾지 못했습니다. SSID 철자, 2.4GHz 인지, 채널 1~13 인지 확인하세요."
                15, 204 -> hints += "비밀번호가 맞는지 확인하세요."
            }
        }
        if (wifiUp == true && net != null && net.rx == 0L) {
            hints += "게이트웨이에서 응답이 없습니다. 게이트웨이 화면에서 PING 을 보내 보세요."
        }
        if (net != null && net.rxForeign > 0L) {
            hints += "다른 서버(PC)의 패킷이 들어옵니다. 브리지 server IP 가 게이트웨이 PC 와 같은지 확인하세요."
        }
        if (status.dsp?.isNoPartition == true) {
            hints += "구 파티션 보드입니다. PC 에서 USB 로 파티션 포함 굽기가 필요합니다 (FLASH_GUIDE 3-2)."
        }
        if ((status.cfgState ?: cfgState) == CfgState.TRIAL) {
            hints += "서버 원격 설정이 확인 대기 중입니다. 여기서 저장하면 즉시 확정됩니다."
        }
        return StatusCard(title, emphasis, items, hints)
    }

    private fun cfgText(state: CfgState?): String = ConnectionChipMapper.cfgLabel(state)
}
