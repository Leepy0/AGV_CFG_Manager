package kr.agv.cfgmanager.session

import kotlin.test.Test
import kotlin.test.assertEquals

class FakeRosterTest {

    @Test
    fun `가짜 보드는 설정 없음 두 대와 12호기 한 대이고 이름 규칙을 따른다`() {
        val names = FakeRoster.create().map { it.device.name }
        assertEquals(listOf("AGVBR-01-A1B2", "AGVBR-01-C3D4", "AGVBR-12-E5F6"), names)
    }
}
