package kr.agv.cfgmanager.core.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kr.agv.cfgmanager.core.testsupport.fixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResponseAssemblerTest {

    /** 텍스트를 [size] 바이트씩 잘라 채널에 넣는다. 각 조각 사이에 [gap] 만큼 기다린다. */
    private fun CoroutineScope.feed(
        text: String,
        size: Int,
        channel: Channel<ByteArray>,
        gap: Long = 0,
    ): Job = launch {
        val bytes = text.toByteArray(Charsets.UTF_8)
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + size, bytes.size)
            channel.send(bytes.copyOfRange(offset, end))
            offset = end
            if (gap > 0 && offset < bytes.size) delay(gap)
        }
    }

    @Test
    fun `한 줄 명령은 첫 줄바꿈에서 끝나고 조각이 잘려도 원문이 맞다`() = runTest {
        val text = "ok ssid - 'save' then 'reboot' to apply\r\n"
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        feed(text, size = 7, channel = channel)
        val reply = readReply(channel, ReplyRule.SingleLine)
        assertEquals(ReplyEnd.Marker, reply.end)
        assertEquals(text, reply.text)
    }

    @Test
    fun `cfg 는 state 줄 뒤 유휴 250ms 가 지나야 끝나고 differs 줄을 포함한다`() = runTest {
        val text = fixture("a2_cfg_0_5_5_differs.txt")
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        feed(text, size = 13, channel = channel)
        val reply = readReply(channel, ReplyRule.CFG)
        assertEquals(ReplyEnd.Idle, reply.end)
        assertEquals(text, reply.text)
    }

    @Test
    fun `state 줄 뒤 100ms 만에 온 differs 줄도 포함된다`() = runTest {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        launch {
            channel.send("agv=7 ssid=\"A\" pass=(8 chars)\r\nstate=saved\r\n".toByteArray())
            delay(100)
            channel.send("* differs from running config\r\n".toByteArray())
        }
        val reply = readReply(channel, ReplyRule.CFG)
        assertTrue(reply.text.contains("* differs"))
        assertEquals(ReplyEnd.Idle, reply.end)
    }

    @Test
    fun `state 줄 뒤 300ms 만에 온 줄은 이미 끝난 응답에 들어가지 않는다`() = runTest {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        launch {
            channel.send("state=saved\r\n".toByteArray())
            delay(300)
            channel.send("* differs\r\n".toByteArray())
        }
        val reply = readReply(channel, ReplyRule.CFG)
        assertFalse(reply.text.contains("differs"))
    }

    @Test
    fun `한글이 든 cfg 응답을 한 바이트씩 받아도 원문이 그대로 나온다`() = runTest {
        val text = fixture("a2_cfg_hangul_ssid.txt")
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        feed(text, size = 1, channel = channel)
        val reply = readReply(channel, ReplyRule.CFG)
        assertEquals(text, reply.text)
        assertEquals(ReplyEnd.Idle, reply.end)
    }

    @Test
    fun `stat 은 dsp 줄 뒤 유휴로 끝난다`() = runTest {
        val text = fixture("a3_stat_wifi_up.txt")
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        feed(text, size = 20, channel = channel, gap = 5)
        val reply = readReply(channel, ReplyRule.STAT)
        assertEquals(text, reply.text)
        assertEquals(ReplyEnd.Idle, reply.end)
    }

    @Test
    fun `응답이 전혀 없으면 3초 뒤 시간 초과`() = runTest {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        val reply = readReply(channel, ReplyRule.SingleLine)
        assertEquals(ReplyEnd.Timeout, reply.end)
        assertEquals("", reply.text)
    }

    @Test
    fun `알 수 없는 명령은 내용이 온 뒤 유휴로 끝난다`() = runTest {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        launch { channel.send("unknown command 'foo' - type help\r\n".toByteArray()) }
        val reply = readReply(channel, ReplyRule.IdleOnly)
        assertEquals(ReplyEnd.Idle, reply.end)
        assertTrue(reply.text.startsWith("unknown command"))
    }

    @Test
    fun `채널이 닫히면 Closed 로 끝난다`() = runTest {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        channel.send("rebooting".toByteArray())
        channel.close()
        val reply = readReply(channel, ReplyRule.SingleLine)
        assertEquals(ReplyEnd.Closed, reply.end)
        assertEquals("rebooting", reply.text)
    }

    @Test
    fun `콘솔 프롬프트 규칙은 ANSI 와 로그를 지운 뒤 bridge 프롬프트를 본다`() = runTest {
        val text = fixture("console_with_logs_and_ansi.txt")
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        feed(text, size = 30, channel = channel)
        val reply = readReply(channel, ReplyRule.Prompt)
        assertEquals(ReplyEnd.Marker, reply.end)
    }

    @Test
    fun `명령별 규칙은 cfg stat 줄 단위 명령 콘솔 과 JSON 을 나눈다`() {
        assertEquals(ReplyRule.CFG, ReplyRule.forCommand("cfg", ChannelKind.Ble))
        assertEquals(ReplyRule.STAT, ReplyRule.forCommand("stat", ChannelKind.Fake))
        assertEquals(ReplyRule.SingleLine, ReplyRule.forCommand("set ssid X", ChannelKind.Ble))
        assertEquals(ReplyRule.SingleLine, ReplyRule.forCommand("save", ChannelKind.Ble))
        assertEquals(ReplyRule.SingleLine, ReplyRule.forCommand("reboot", ChannelKind.Ble))
        assertEquals(ReplyRule.SingleLine, ReplyRule.forCommand("cfg json", ChannelKind.Ble))
        assertEquals(ReplyRule.IdleOnly, ReplyRule.forCommand("foo", ChannelKind.Ble))
        assertEquals(ReplyRule.Prompt, ReplyRule.forCommand("cfg", ChannelKind.UsbSerial))
    }

    @Test
    fun `어셈블러는 표지 줄이 완결되기 전에는 끝났다고 보지 않는다`() {
        val assembler = ResponseAssembler(ReplyRule.SingleLine)
        assembler.append("ok ssid")
        assertFalse(assembler.markerReached())
        assembler.append(" - 'save' then 'reboot' to apply\r\n")
        assertTrue(assembler.endsOnMarker())
    }

    @Test
    fun `앞 명령의 늦은 끝 표시 줄이 먼저 와도 이번 응답을 읽는다`() = runTest {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        feed(">\r\nok pass - 'save' then 'reboot' to apply\r\n>\r\n", size = 5, channel = channel)
        val reply = readReply(channel, ReplyRule.SingleLine)
        assertEquals(ReplyEnd.Marker, reply.end)
        assertEquals("ok pass - 'save' then 'reboot' to apply\r\n", reply.text)
    }

    @Test
    fun `여러 줄 응답은 끝 표시 줄에서 유휴 시간을 기다리지 않고 끝난다`() = runTest {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        feed("agv=7 ssid=\"A\" pass=(8 chars)\r\nstate=saved\r\n>\r\n", size = 9, channel = channel)
        val reply = readReply(channel, ReplyRule.CFG)
        assertEquals(ReplyEnd.Marker, reply.end)
        assertEquals("agv=7 ssid=\"A\" pass=(8 chars)\r\nstate=saved\r\n", reply.text)
    }
}
