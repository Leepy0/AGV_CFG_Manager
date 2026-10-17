package kr.agv.cfgmanager.ui.diagnostics

import kr.agv.cfgmanager.core.bridge.BridgeStatus
import kr.agv.cfgmanager.core.bridge.CfgState
import kr.agv.cfgmanager.core.bridge.DspStatus
import kr.agv.cfgmanager.core.bridge.NetStatus
import kr.agv.cfgmanager.core.bridge.StatSource
import kr.agv.cfgmanager.core.bridge.WifiStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusCardBuilderTest {

    private fun status(
        up: Boolean,
        rssi: Int = -55,
        lastReason: Int = 0,
        rx: Long = 56,
        rxForeign: Long = 0,
        dspState: String = "idle",
    ) = BridgeStatus(
        source = StatSource.Ble,
        fw = "0.5.5",
        part = "ota_0",
        ota = "valid",
        cfgState = CfgState.SAVED,
        wifi = WifiStatus(up = up, mac = "a1:b2:c3:d4:a1:b2", ip = "192.168.10.107", rssi = rssi, channel = 6, bssid = null),
        net = NetStatus(
            tx = 1234, txErr = 0, txOffline = 0, rx = rx, rxBad = 0, rxForeign = rxForeign,
            disconnects = 3, lastReason = lastReason,
        ),
        setup = null,
        dsp = DspStatus(app = "1.0.3", boot = "1.1.0", build = "2026-10-01", state = dspState, staged = null, stagedJob = 0, pan = "?"),
        heapBytes = null,
    )

    @Test
    fun `Wi-Fi 가 끊기고 사유 201 이면 SSID 와 채널 안내를 보인다`() {
        val card = StatusCardBuilder.build(status(up = false, lastReason = 201), CfgState.SAVED, fwFallback = null)
        assertEquals("Wi-Fi 끊김", card.title)
        assertEquals(Emphasis.Alarm, card.emphasis)
        assertTrue(card.hints.any { it.contains("SSID") && it.contains("2.4GHz") })
        assertTrue(card.items.any { it.label == "마지막 끊김 사유" && it.value.contains("NO_AP_FOUND") })
    }

    @Test
    fun `Wi-Fi 연결 중에는 RSSI 등급을 글자로 보인다`() {
        val card = StatusCardBuilder.build(status(up = true, rssi = -55), CfgState.SAVED, fwFallback = null)
        assertEquals("Wi-Fi 연결됨", card.title)
        assertTrue(card.items.any { it.label == "신호" && it.value == "-55 dBm · 강함" })
    }

    @Test
    fun `구 파티션 보드는 USB 굽기 안내를 보인다`() {
        val card = StatusCardBuilder.build(status(up = true, dspState = "nopart"), CfgState.SAVED, fwFallback = null)
        assertTrue(card.hints.any { it.contains("USB") && it.contains("FLASH_GUIDE 3-2") })
        assertTrue(card.items.any { it.label == "메인보드" && it.emphasis == Emphasis.Warn })
    }

    @Test
    fun `게이트웨이 패킷이 없으면 PING 안내를, 다른 서버 패킷이 있으면 server IP 안내를 보인다`() {
        val silent = StatusCardBuilder.build(status(up = true, rx = 0), CfgState.SAVED, null)
        assertTrue(silent.hints.any { it.contains("PING") })
        val foreign = StatusCardBuilder.build(status(up = true, rxForeign = 4), CfgState.SAVED, null)
        assertTrue(foreign.hints.any { it.contains("server IP") })
    }

    @Test
    fun `아직 안 읽었으면 새로고침을 안내한다`() {
        val card = StatusCardBuilder.build(null, null, null)
        assertEquals("상태를 아직 읽지 않았습니다", card.title)
        assertTrue(card.hints.single().contains("새로고침"))
    }
}
