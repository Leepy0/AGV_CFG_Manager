package kr.agv.cfgmanager.log

import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppLogTest {

    private lateinit var dir: File
    private val today = LocalDate.of(2026, 10, 17)

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("applog-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `오늘 포함 7일은 남기고 여드레째부터 지운다`() {
        assertFalse(LogRetention.isExpired(today.minusDays(6), today))
        assertTrue(LogRetention.isExpired(today.minusDays(7), today))
    }

    @Test
    fun `로그 파일 이름에서 날짜를 읽는다`() {
        assertEquals(today, LogRetention.dayOf("app-20261017.log"))
        assertNull(LogRetention.dayOf("history.jsonl"))
        assertEquals("app-20261017.log", LogRetention.fileName(today))
    }

    @Test
    fun `보관 기간이 지난 로그만 지우고 다른 파일은 둔다`() {
        File(dir, "app-20261001.log").writeText("old")
        File(dir, "app-20261012.log").writeText("kept")
        File(dir, "notes.txt").writeText("other")
        val log = AppLog(dir)
        assertEquals(1, log.prune(today))
        assertFalse(File(dir, "app-20261001.log").exists())
        assertTrue(File(dir, "app-20261012.log").exists())
        assertTrue(File(dir, "notes.txt").exists())
    }

    @Test
    fun `비밀번호 인자와 IP 주소는 기록하지 않는다`() {
        assertEquals("login ***", AppLog.redact("> login agvsetup"))
        assertEquals("set pass ***", AppLog.redact("set pass agv12345"))
        assertEquals("server x.x.x.x ok", AppLog.redact("server 192.168.10.10 ok"))
        assertEquals("wifi: UP", AppLog.redact("wifi: UP"))
    }

    @Test
    fun `쓴 줄은 가려진 채로 날짜 파일에 남고 내보내기에 들어간다`() {
        val log = AppLog(dir, zone = ZoneOffset.UTC, clock = { Instant.parse("2026-10-17T01:02:03Z") })
        log.info("세션", "login secret-pass")
        log.info("적용", "적용 결과: ok")
        log.flush()
        val file = File(dir, "app-20261017.log")
        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.contains("01:02:03.000 I 세션 login ***"))
        assertFalse(text.contains("secret-pass"))
        val exported = log.exportText(today)
        assertTrue(exported.contains("== 2026-10-17 =="))
        assertTrue(exported.contains("적용 결과: ok"))
    }
}
