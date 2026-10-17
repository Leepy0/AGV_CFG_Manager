package kr.agv.cfgmanager.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsoleLogTest {

    @Test
    fun `login 과 set pass 의 비밀번호 값은 콘솔에서 가린다`() {
        val log = ConsoleLog()
        log.sent("login agvsetup", 1)
        log.sent("set pass hunter2hunter2", 2)
        val texts = log.snapshot().map { it.text }
        assertEquals(listOf("> login ***", "> set pass ***"), texts)
        assertTrue(texts.none { it.contains("agvsetup") || it.contains("hunter2") })
    }

    @Test
    fun `비밀번호가 든 명령은 최근 명령에 남지 않는다`() {
        val log = ConsoleLog()
        log.sent("stat", 1)
        log.sent("login agvsetup", 2)
        log.sent("cfg", 3)
        assertEquals(listOf("cfg", "stat"), log.recentCommands())
    }

    @Test
    fun `받은 원문은 줄 단위로 나누고 CR 과 끝의 빈 줄을 버린다`() {
        val log = ConsoleLog()
        log.received("fw=0.5.5 part=ota_0\r\nwifi: UP ip=192.168.10.107\r\n", 5)
        assertEquals(
            listOf("fw=0.5.5 part=ota_0", "wifi: UP ip=192.168.10.107"),
            log.snapshot().map { it.text },
        )
    }

    @Test
    fun `용량을 넘으면 오래된 줄부터 버린다`() {
        val log = ConsoleLog(capacity = 3)
        (1..5).forEach { log.system("line $it", it.toLong()) }
        assertEquals(listOf("line 3", "line 4", "line 5"), log.snapshot().map { it.text })
    }

    @Test
    fun `명령 기록은 같은 명령을 한 번만 최근 목록 앞에 둔다`() {
        val log = ConsoleLog()
        log.sent("stat", 1)
        log.sent("cfg", 2)
        log.sent("stat", 3)
        assertEquals(listOf("stat", "cfg"), log.recentCommands())
        assertFalse(log.recentCommands().contains("login ***"))
    }
}
