package kr.agv.cfgmanager.core.bridge

import kr.agv.cfgmanager.core.util.TerminalText

/**
 * USB 콘솔 응답 정리 (docs/DEVELOPMENT_PLAN.md 3.3). 콘솔은 dumb 모드라 보낸 줄이 맨 앞에 echo 되고,
 * 응답 끝에 `bridge> ` 프롬프트가 붙는다. 둘 다 떼어 BLE 응답과 같은 본문을 돌려준다.
 */
object UsbConsole {
    private val leadingPrompt = Regex("^bridge>\\s*")
    private val trailingPrompt = Regex("bridge>\\s*$")

    /** [raw] 는 보낸 [sentLine] 에 대한 원문 응답이다. 프롬프트만 온 응답이면 빈 문자열이다. */
    fun replyBody(raw: String, sentLine: String): String {
        val text = trailingPrompt.replace(leadingPrompt.replace(TerminalText.clean(raw), ""), "")
        val lines = text.split('\n').toMutableList()
        if (lines.isNotEmpty() && lines.first().trim() == sentLine.trim()) lines.removeAt(0)
        return lines.joinToString("\n").trimEnd()
    }
}
