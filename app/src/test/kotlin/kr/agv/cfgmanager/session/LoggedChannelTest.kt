package kr.agv.cfgmanager.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kr.agv.cfgmanager.core.bridge.ChannelKind
import kr.agv.cfgmanager.core.bridge.ChannelState
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

class LoggedChannelTest {

    private class StubChannel : IBridgeChannel {
        override val kind = ChannelKind.Fake
        override val state: StateFlow<ChannelState> = MutableStateFlow(ChannelState.Disconnected)
        override val raw: SharedFlow<String> = MutableSharedFlow()
        override suspend fun exec(line: String, timeout: Duration): String = "reply:$line"
        override suspend fun execThenDisconnect(line: String): String = "reply:$line"
        override suspend fun waitReady(timeout: Duration): Boolean = true
        override suspend fun disconnect() = Unit
    }

    @Test
    fun `대량 읽기는 콘솔 기록과 변경 알림을 건너뛰고 응답은 돌려준다`() = runTest {
        val log = ConsoleLog()
        var changes = 0
        val channel = LoggedChannel(StubChannel(), log, { 1L }, onChange = { changes++ })
        val reply = channel.exec("dsp pkg read 0 0 512")
        assertEquals("reply:dsp pkg read 0 0 512", reply)
        assertEquals(emptyList(), log.snapshot())
        assertEquals(0, changes)
    }

    @Test
    fun `다른 명령은 그대로 기록하고 알린다`() = runTest {
        val log = ConsoleLog()
        var changes = 0
        val channel = LoggedChannel(StubChannel(), log, { 1L }, onChange = { changes++ })
        channel.exec("stat")
        assertEquals(listOf("> stat"), log.snapshot().map { it.text })
        assertEquals(1, changes)
    }
}
