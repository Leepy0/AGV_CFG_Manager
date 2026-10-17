package kr.agv.cfgmanager.core.util

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ipv4Test {

    @Test
    fun `정상 주소는 옥텟으로 파싱된다`() {
        assertContentEquals(intArrayOf(192, 168, 10, 107), Ipv4.parse("192.168.10.107"))
        assertContentEquals(intArrayOf(0, 0, 0, 0), Ipv4.parse("0.0.0.0"))
        assertContentEquals(intArrayOf(255, 255, 255, 255), Ipv4.parse("255.255.255.255"))
    }

    @Test
    fun `형식이 어긋난 주소는 거부한다`() {
        assertNull(Ipv4.parse(""))
        assertNull(Ipv4.parse("192.168.10"))
        assertNull(Ipv4.parse("192.168.10.1.1"))
        assertNull(Ipv4.parse("192.168.10.256"))
        assertNull(Ipv4.parse("192.168.10.-1"))
        assertNull(Ipv4.parse("192.168.10.a"))
        assertNull(Ipv4.parse("192.168.10."))
    }

    @Test
    fun `앞자리 0 이 붙은 옥텟은 거부한다`() {
        assertFalse(Ipv4.isValid("192.168.010.1"))
        assertTrue(Ipv4.isValid("192.168.0.1"))
    }
}
