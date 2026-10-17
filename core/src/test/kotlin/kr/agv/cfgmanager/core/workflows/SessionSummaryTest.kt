package kr.agv.cfgmanager.core.workflows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionSummaryTest {

    @Test
    fun `끝낸 호기를 표로 쓰고 성공 여부를 글로 적는다`() {
        val text = SessionSummary.toText(
            listOf(
                BatchDone(agv = 7, bleAddress = "AA:BB", deviceName = "AGVBR-07-A1B2", result = "ok"),
                BatchDone(agv = 8, bleAddress = "CC:DD", deviceName = null, result = "wifi_down:201"),
            ),
            date = "2026-10-17",
        )
        assertTrue(text.startsWith("AGV 세팅 요약 · 2026-10-17\n설정한 호기 2대"))
        assertTrue(text.contains("07호기\tAGVBR-07-A1B2\t성공"))
        assertTrue(text.contains("08호기\t-\t확인 필요 (wifi_down:201)"))
    }

    @Test
    fun `요약에 비밀번호나 주소를 넣지 않는다`() {
        val text = SessionSummary.toText(
            listOf(BatchDone(agv = 1, bleAddress = "AA:BB:CC:DD:EE:FF", deviceName = "AGVBR-01", result = "ok")),
            date = "2026-10-17",
        )
        assertFalse(text.contains("AA:BB"))
        assertFalse(text.contains("agvsetup"))
    }

    @Test
    fun `끝낸 호기가 없으면 표 없이 0대라고 쓴다`() {
        assertEquals("AGV 세팅 요약 · 2026-10-17\n설정한 호기 0대\n", SessionSummary.toText(emptyList(), "2026-10-17"))
    }
}
