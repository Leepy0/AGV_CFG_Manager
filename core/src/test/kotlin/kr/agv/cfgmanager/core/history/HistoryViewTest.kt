package kr.agv.cfgmanager.core.history

import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryViewTest {

    private fun entry(agv: Int, at: String, name: String, ip: String) = HistoryEntry(
        at = at, profileId = "hq", profileName = name, agv = agv, bleAddress = "AA:$agv", mac = null,
        deviceName = "AGVBR-%02d-A1B2".format(agv), ip = ip, ssid = "AGV_WIFI", fw = "0.5.5",
        channel = "ble", action = "apply", result = "ok", rssi = null, note = "",
    )

    private val entries = listOf(
        entry(7, "2026-10-01T10:00:00Z", "본사 1공장", "192.168.10.107"),
        entry(8, "2026-10-17T10:00:00Z", "본사 1공장", "192.168.10.108"),
        entry(12, "2026-10-16T10:00:00Z", "OO공장", "192.168.20.112"),
    )

    @Test
    fun `결과 문구는 코드마다 사람이 읽을 말로 바뀐다`() {
        assertEquals("성공", HistoryView.resultLabel("ok"))
        assertEquals("Wi-Fi 끊김 (201)", HistoryView.resultLabel("wifi_down:201"))
        assertEquals("값 거부 (server)", HistoryView.resultLabel("invalid:server"))
        assertEquals("기타 (weird)", HistoryView.resultLabel("weird"))
    }

    @Test
    fun `검색은 호기 번호 두 자리 표기와 IP 와 프로필 이름을 맞춘다`() {
        assertEquals(listOf(7), HistoryView.filter(entries, "07", null).map { it.agv })
        assertEquals(listOf(12), HistoryView.filter(entries, "192.168.20", null).map { it.agv })
        assertEquals(listOf(8, 12, 7), HistoryView.filter(entries, "공장", null).map { it.agv })
    }

    @Test
    fun `기간 필터는 시작 시각 이후만 남기고 최신순으로 정렬한다`() {
        val recent = HistoryView.filter(entries, "", "2026-10-10T00:00:00Z")
        assertEquals(listOf(8, 12), recent.map { it.agv })
    }
}
