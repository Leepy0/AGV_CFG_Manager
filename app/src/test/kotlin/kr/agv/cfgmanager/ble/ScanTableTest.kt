package kr.agv.cfgmanager.ble

import kr.agv.cfgmanager.session.BoardRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanTableTest {

    @Test
    fun `AGVBR 접두어가 없는 기기는 표에 넣지 않는다`() {
        val table = ScanTable()
        table.record("AA:01", "Headphones", -40, nowMs = 1_000)
        table.record("AA:02", "AGVBR-01-A1B2", -50, nowMs = 1_000)
        assertEquals(listOf("AGVBR-01-A1B2"), table.snapshot(1_000).map { it.name })
    }

    @Test
    fun `같은 주소는 최신 RSSI 한 줄만 남긴다`() {
        val table = ScanTable()
        table.record("AA:01", "AGVBR-01-A1B2", -80, nowMs = 1_000)
        table.record("AA:01", "AGVBR-01-A1B2", -48, nowMs = 2_000)
        val rows = table.snapshot(2_000)
        assertEquals(1, rows.size)
        assertEquals(-48, rows.single().rssi)
    }

    @Test
    fun `신호가 강한 순서로 정렬한다`() {
        val table = ScanTable()
        table.record("AA:01", "AGVBR-01-A1B2", -63, nowMs = 0)
        table.record("AA:02", "AGVBR-12-E5F6", -71, nowMs = 0)
        table.record("AA:03", "AGVBR-01-C3D4", -48, nowMs = 0)
        assertEquals(listOf(-48, -63, -71), table.snapshot(0).map { it.rssi })
    }

    @Test
    fun `5초 이상 안 보이면 흐리게 표시한다`() {
        val row = BoardRow("AA:01", "AGVBR-01-A1B2", -48, lastSeenMs = 10_000)
        assertFalse(row.isStale(nowMs = 14_999))
        assertTrue(row.isStale(nowMs = 15_000))
    }

    @Test
    fun `30초 넘게 안 보인 기기는 표에서 뺀다`() {
        val table = ScanTable()
        table.record("AA:01", "AGVBR-01-A1B2", -48, nowMs = 0)
        assertEquals(1, table.snapshot(ScanTable.KEEP_MS).size)
        assertEquals(0, table.snapshot(ScanTable.KEEP_MS + 1).size)
    }

    @Test
    fun `신호 등급은 막대 수와 글자가 함께 바뀐다`() {
        assertEquals(SignalGrade.Strong, SignalGrade.of(-60))
        assertEquals(SignalGrade.Medium, SignalGrade.of(-61))
        assertEquals(SignalGrade.Medium, SignalGrade.of(-75))
        assertEquals(SignalGrade.Weak, SignalGrade.of(-76))
        assertEquals(3, SignalGrade.Strong.bars)
        assertEquals(1, SignalGrade.Weak.bars)
    }
}
