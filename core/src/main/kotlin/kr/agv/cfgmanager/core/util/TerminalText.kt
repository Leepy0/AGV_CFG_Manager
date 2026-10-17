package kr.agv.cfgmanager.core.util

/**
 * USB 콘솔 출력 정리: ANSI 색 코드 제거, 브리지 로그 줄(`I (123) tag: ...`) 제외, 프롬프트 판정.
 * 브리지 콘솔 프롬프트는 `bridge> ` 이다 (docs/DEVELOPMENT_PLAN.md 3.3).
 */
object TerminalText {
    private val ansi = Regex("\u001B\\[[0-9;]*[A-Za-z]")
    private val logLine = Regex("[IWEDV] \\(\\d+\\) .*")
    private val prompt = Regex("bridge>\\s*$")

    /** CRLF 를 LF 로 맞추고, ANSI 를 지우고, 로그 줄을 뺀다. */
    fun clean(raw: String): String {
        val withoutAnsi = ansi.replace(raw, "")
        return withoutAnsi
            .split('\n')
            .map { it.trimEnd('\r') }
            .filterNot { logLine.matches(it) }
            .joinToString("\n")
    }

    /** 이미 정리된 텍스트가 프롬프트로 끝나는지. */
    fun endsWithPrompt(cleaned: String): Boolean = prompt.containsMatchIn(cleaned)
}
