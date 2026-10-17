package kr.agv.cfgmanager.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UtilTest {

    @Test
    fun `UTF-8 청크가 문자 중간에서 잘려도 완성된 문자만 나온다`() {
        val text = "본사 1공장 WIFI — 가나다"
        val bytes = text.toByteArray(Charsets.UTF_8)
        for (size in 1..7) {
            val stream = Utf8Stream()
            val out = StringBuilder()
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + size, bytes.size)
                out.append(stream.push(bytes.copyOfRange(offset, end)))
                offset = end
            }
            out.append(stream.flush())
            assertEquals(text, out.toString(), "조각 크기 $size")
        }
    }

    @Test
    fun `미완성 꼬리는 flush 에서만 나온다`() {
        val stream = Utf8Stream()
        val han = "가".toByteArray(Charsets.UTF_8)
        assertEquals("", stream.push(han.copyOfRange(0, 2)))
        assertEquals("가", stream.push(han.copyOfRange(2, 3)))
        assertEquals("", stream.flush())
    }

    @Test
    fun `바이트 길이는 UTF-8 기준이다`() {
        assertEquals(5, "AGV_W".utf8Length())
        assertEquals(9, "가나다".utf8Length())
    }

    @Test
    fun `터미널 정리는 ANSI 와 로그 줄을 지우고 CRLF 를 맞춘다`() {
        val raw = "I (1) x: y\r\n\u001B[0;32mwifi: UP\u001B[0m\r\nbridge> "
        assertEquals("wifi: UP\nbridge> ", TerminalText.clean(raw))
        assertTrue(TerminalText.endsWithPrompt(TerminalText.clean(raw)))
        assertFalse(TerminalText.endsWithPrompt("wifi: UP\n"))
    }

    @Test
    fun `IPv4 서브넷 판정과 연속형 마스크`() {
        assertEquals(true, Ipv4.sameSubnet("192.168.10.107", "192.168.10.10", "255.255.255.0"))
        assertEquals(false, Ipv4.sameSubnet("192.168.10.107", "192.168.20.10", "255.255.255.0"))
        assertNull(Ipv4.sameSubnet("x", "192.168.10.10", "255.255.255.0"))
        assertEquals(true, Ipv4.isContiguousMask("255.255.255.0"))
        assertEquals(true, Ipv4.isContiguousMask("255.255.240.0"))
        assertEquals(false, Ipv4.isContiguousMask("255.0.255.0"))
        assertEquals("192.168.10.1", Ipv4.canonical("192.168.10.1"))
        assertNull(Ipv4.canonical("192.168.010.1"))
    }

    @Test
    fun `비밀번호 인자만 가린다`() {
        assertEquals("login ***", Masking.redactLine("login agvsetup"))
        assertEquals("set ssid X", Masking.redactLine("set ssid X"))
    }
}
