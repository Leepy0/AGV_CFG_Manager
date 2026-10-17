package kr.agv.cfgmanager.core.workflows

import kotlin.test.Test
import kotlin.test.assertEquals

class BootNoteTest {

    @Test
    fun `재연결 성공은 걸린 시간과 첫 stat 없음을 한 줄로 적는다`() {
        assertEquals("재연결 1.5초 · 첫 stat 응답 없음", BootNote.ok(1_500, null))
    }

    @Test
    fun `재연결 시간 초과는 걸린 시간을 적는다`() {
        assertEquals("재연결 시간 초과 (12.3초)", BootNote.timeout(12_345))
    }
}
