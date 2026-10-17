package kr.agv.cfgmanager.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardNameTest {

    @Test
    fun `광고 이름에서 호기 번호를 읽는다`() {
        assertEquals(7, BoardName.agvNumber("AGVBR-07-A1B2"))
        assertEquals(12, BoardName.agvNumber("AGVBR-12-E5F6"))
        assertNull(BoardName.agvNumber("AGVBR-07"))
        assertNull(BoardName.agvNumber("AGVBR-XX-A1B2"))
    }

    @Test
    fun `AGVBR 접두어만 보드로 본다`() {
        assertTrue(BoardName.isBoardName("AGVBR-01-A1B2"))
        assertFalse(BoardName.isBoardName("Speaker"))
    }

    @Test
    fun `연결 실패 문구에 남은 횟수와 다음 행동이 들어간다`() {
        val text = ConnectFailureText.message(ConnectFailure.WrongPassword(attemptsLeft = 4))
        assertTrue(text.contains("남은 시도 4회"))
        assertTrue(text.contains("확인"))
    }

    @Test
    fun `연결 실패 문구는 예외 원문을 그대로 쓰지 않는다`() {
        val text = ConnectFailureText.message(ConnectFailure.Other("읽는 중 연결이 끊겼습니다"))
        assertTrue(text.startsWith("연결하지 못했습니다"))
        assertTrue(ConnectFailureText.message(ConnectFailure.Timeout).contains("BOOT"))
    }
}
