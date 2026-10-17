package kr.agv.cfgmanager.session

import kr.agv.cfgmanager.core.util.Masking

/** 콘솔 줄 종류. */
enum class ConsoleKind { Sent, Received, System }

data class ConsoleLine(val kind: ConsoleKind, val text: String, val atMs: Long)

/**
 * 콘솔 원문 버퍼 (F-D1). 보낸 명령은 비밀번호 값을 `***` 로 가려서 넣는다.
 * 최근 명령 목록에는 비밀번호가 들어간 명령을 넣지 않는다(다시 불러올 수 없게).
 */
class ConsoleLog(private val capacity: Int = DEFAULT_CAPACITY) {
    private val lines = ArrayDeque<ConsoleLine>()
    private val recent = ArrayDeque<String>()

    @Synchronized
    fun sent(line: String, atMs: Long) {
        val masked = Masking.redactLine(line)
        add(ConsoleLine(ConsoleKind.Sent, "> $masked", atMs))
        if (masked == line) {
            recent.remove(line)
            recent.addFirst(line)
            while (recent.size > RECENT_MAX) recent.removeLast()
        }
    }

    /** 받은 원문을 줄 단위로 넣는다. CR 은 버리고 끝의 빈 줄은 무시한다. */
    @Synchronized
    fun received(text: String, atMs: Long) {
        text.replace("\r", "")
            .split('\n')
            .dropLastWhile { it.isEmpty() }
            .forEach { add(ConsoleLine(ConsoleKind.Received, it, atMs)) }
    }

    @Synchronized
    fun system(text: String, atMs: Long) {
        add(ConsoleLine(ConsoleKind.System, text, atMs))
    }

    @Synchronized
    fun snapshot(): List<ConsoleLine> = lines.toList()

    @Synchronized
    fun recentCommands(): List<String> = recent.toList()

    @Synchronized
    fun clear() {
        lines.clear()
        recent.clear()
    }

    private fun add(line: ConsoleLine) {
        lines.addLast(line)
        while (lines.size > capacity) lines.removeFirst()
    }

    companion object {
        const val DEFAULT_CAPACITY = 800
        const val RECENT_MAX = 5
    }
}
