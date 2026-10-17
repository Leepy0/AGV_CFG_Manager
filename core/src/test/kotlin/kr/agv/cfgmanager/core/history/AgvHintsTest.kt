package kr.agv.cfgmanager.core.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgvHintsTest {

    private fun entry(agv: Int?, at: String, address: String, mac: String?, result: String = "ok", profile: String? = "hq") =
        HistoryEntry(
            at = at, profileId = profile, profileName = "본사", agv = agv, bleAddress = address, mac = mac,
            deviceName = null, ip = null, ssid = null, fw = null, channel = "ble", action = "apply",
            result = result, rssi = null, note = "",
        )

    @Test
    fun `다른 보드가 쓴 호기 번호만 막힌 것으로 본다`() {
        val entries = listOf(
            entry(7, "2026-10-02T01:00:00Z", "AA:01", "a1:b2:c3:d4:a1:01"),
            entry(8, "2026-10-03T01:00:00Z", "AA:CURRENT", "a1:b2:c3:d4:a1:02"),
            entry(9, "2026-10-04T01:00:00Z", "AA:02", "a1:b2:c3:d4:a1:03", result = "invalid:server"),
        )
        val taken = AgvHints.takenAgv(entries, currentAddress = "aa:current", currentMac = null)
        assertEquals(setOf(7), taken.keys)
        assertEquals("2026-10-02 에 다른 보드에 설정했습니다", taken[7])
    }

    @Test
    fun `같은 MAC 이면 같은 보드로 보고 제외한다`() {
        val entries = listOf(entry(7, "2026-10-02T01:00:00Z", "AA:01", "a1:b2:c3:d4:a1:01"))
        assertEquals(emptyMap(), AgvHints.takenAgv(entries, "AA:99", "A1:B2:C3:D4:A1:01"))
    }

    @Test
    fun `다음 호기는 프로필 안 최대 저장 번호 + 1 이다`() {
        val entries = listOf(
            entry(7, "2026-10-02T01:00:00Z", "AA:01", null),
            entry(12, "2026-10-03T01:00:00Z", "AA:02", null, result = "wifi_down:201"),
            entry(20, "2026-10-04T01:00:00Z", "AA:03", null, result = "timeout"),
            entry(30, "2026-10-04T01:00:00Z", "AA:04", null, profile = "other"),
        )
        assertEquals(13, AgvHints.nextAgvFor("hq", entries))
        assertNull(AgvHints.nextAgvFor("none", entries))
        assertNull(AgvHints.nextAgvFor(null, entries))
    }

    @Test
    fun `보드 주소별 마지막 저장 호기를 대문자 키로 얻는다`() {
        val entries = listOf(
            entry(7, "2026-10-01T01:00:00Z", "aa:01", null),
            entry(8, "2026-10-02T01:00:00Z", "AA:01", null),
            entry(9, "2026-10-03T01:00:00Z", "AA:02", null, result = "timeout"),
        )
        assertEquals(mapOf("AA:01" to 8), AgvHints.latestAgvByAddress(entries))
    }
}
