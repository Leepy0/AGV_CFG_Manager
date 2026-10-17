package kr.agv.cfgmanager.ui.diagnostics

import kr.agv.cfgmanager.session.ConsoleKind
import kr.agv.cfgmanager.session.ConsoleLine
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentCommandsTest {

    private fun sent(text: String) = ConsoleLine(ConsoleKind.Sent, "> $text", 0)
    private fun received(text: String) = ConsoleLine(ConsoleKind.Received, text, 0)

    @Test
    fun `최근 명령은 보낸 줄만 최신순으로 모으고 비밀 값이 든 줄은 뺀다`() {
        val lines = listOf(
            sent("stat"),
            received("fw=0.5.5"),
            sent("login ***"),
            sent("cfg"),
            sent("stat"),
        )
        assertEquals(listOf("stat", "cfg"), recentCommands(lines))
    }

    @Test
    fun `최근 명령은 다섯 개까지만 보인다`() {
        val lines = (1..7).map { sent("cmd$it") }
        assertEquals(listOf("cmd7", "cmd6", "cmd5", "cmd4", "cmd3"), recentCommands(lines))
    }
}
