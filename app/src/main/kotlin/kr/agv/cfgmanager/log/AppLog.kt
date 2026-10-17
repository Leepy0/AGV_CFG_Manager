package kr.agv.cfgmanager.log

import kr.agv.cfgmanager.core.util.Masking
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors

/** 로그 파일 보관 규칙 (docs/DEVELOPMENT_PLAN.md F-G2): 파일 이름은 `app-YYYYMMDD.log`, 오늘을 포함해 7일 보관. */
object LogRetention {
    const val KEEP_DAYS = 7

    private val fileDate = Regex("""app-(\d{8})\.log""")
    private val nameFormat = DateTimeFormatter.BASIC_ISO_DATE

    fun fileName(day: LocalDate): String = "app-${day.format(nameFormat)}.log"

    /** 파일 이름에서 날짜를 읽는다. 로그 파일 이름이 아니면 null. */
    fun dayOf(fileName: String): LocalDate? {
        val digits = fileDate.matchEntire(fileName)?.groupValues?.get(1) ?: return null
        return runCatching { LocalDate.parse(digits, nameFormat) }.getOrNull()
    }

    /** [day] 가 보관 기간 밖이면 true. 오늘과 그 전 6일은 남긴다. */
    fun isExpired(day: LocalDate, today: LocalDate): Boolean = ChronoUnit.DAYS.between(day, today) >= KEEP_DAYS
}

/**
 * 앱 로그 (docs/DEVELOPMENT_PLAN.md F-G2). 파일 쓰기는 전용 스레드에서 하므로 메인 스레드에서 불러도 된다.
 * 비밀번호는 [redact] 가 가린다. IP 주소도 사내 망 정보라 남기지 않는다 (`x.x.x.x` 로 바꾼다).
 */
class AppLog(
    private val dir: File,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Instant = Instant::now,
) {
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "app-log").apply { isDaemon = true } }
    private val lock = Any()

    fun info(tag: String, message: String) = write("I", tag, message)

    fun warn(tag: String, message: String) = write("W", tag, message)

    private fun write(level: String, tag: String, message: String) {
        val now = clock()
        worker.execute {
            val local = now.atZone(zone)
            val line = "${TIME.format(local)} $level $tag ${redact(message)}\n"
            synchronized(lock) {
                try {
                    dir.mkdirs()
                    prune(local.toLocalDate())
                    File(dir, LogRetention.fileName(local.toLocalDate())).appendText(line, Charsets.UTF_8)
                } catch (failure: IOException) {
                    // 로그 쓰기 실패로 앱 동작을 바꾸지 않는다
                }
            }
        }
    }

    /** 쓰기 대기열이 비도록 기다린다 (테스트용). */
    internal fun flush() {
        worker.submit { }.get()
    }

    /** 보관 기간이 지난 로그 파일을 지운다. 지운 개수. */
    fun prune(today: LocalDate): Int = synchronized(lock) {
        dir.listFiles().orEmpty().count { file ->
            val day = LogRetention.dayOf(file.name) ?: return@count false
            LogRetention.isExpired(day, today) && file.delete()
        }
    }

    /** 보관 기간 안의 로그를 날짜 순으로 이어 붙인 텍스트 (공유용). */
    fun exportText(today: LocalDate): String = synchronized(lock) {
        dir.listFiles().orEmpty()
            .mapNotNull { file -> LogRetention.dayOf(file.name)?.let { it to file } }
            .filterNot { (day, _) -> LogRetention.isExpired(day, today) }
            .sortedBy { (day, _) -> day }
            .joinToString("") { (day, file) -> "== ${day} ==\n" + file.readText(Charsets.UTF_8) }
    }

    companion object {
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        private val IPV4 = Regex("""\b\d{1,3}(\.\d{1,3}){3}\b""")

        /** 줄마다 명령 접두(`> `)를 떼고 비밀번호 인자를 가린 뒤 IPv4 를 가린다. */
        fun redact(message: String): String = message.lineSequence().joinToString(" / ") { raw ->
            val line = raw.trim().removePrefix("> ")
            IPV4.replace(Masking.redactLine(line), "x.x.x.x")
        }
    }
}
