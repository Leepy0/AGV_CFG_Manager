package kr.agv.cfgmanager.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateVersionTest {

    @Test
    fun `X·Y·Z 를 버전 코드로 바꾼다`() {
        assertEquals(100, UpdateVersion.parse("0.1.0")?.code)
        assertEquals(10_203, UpdateVersion.parse("1.2.3")?.code)
        assertEquals(10_000 * 10 + 99 * 100 + 99, UpdateVersion.parse("10.99.99")?.code)
    }

    @Test
    fun `앞의 v 와 양쪽 공백을 허용한다`() {
        assertEquals(UpdateVersion(0, 1, 0), UpdateVersion.parse("v0.1.0"))
        assertEquals(UpdateVersion(0, 1, 0), UpdateVersion.parse("  v0.1.0 "))
    }

    @Test
    fun `형식이 맞지 않으면 null 이다`() {
        assertNull(UpdateVersion.parse(""))
        assertNull(UpdateVersion.parse("1.2"))
        assertNull(UpdateVersion.parse("1.2.3.4"))
        assertNull(UpdateVersion.parse("abc"))
        assertNull(UpdateVersion.parse("01.2.3"))
        assertNull(UpdateVersion.parse("1.2.3-dev"))
    }

    @Test
    fun `Y 나 Z 가 100 이상이면 버전 코드가 겹치므로 null 이다`() {
        assertNull(UpdateVersion.parse("1.100.0"))
        assertNull(UpdateVersion.parse("1.0.100"))
    }

    @Test
    fun `0_0_0 은 버전 코드 0 이라 null 이다`() {
        assertNull(UpdateVersion.parse("0.0.0"))
    }

    @Test
    fun `버전 코드 순서로 비교한다`() {
        val old = UpdateVersion.parse("0.1.9")!!
        val next = UpdateVersion.parse("0.2.0")!!
        val major = UpdateVersion.parse("1.0.0")!!
        assertTrue(next > old)
        assertTrue(major > next)
        assertEquals("0.2.0", next.toString())
    }

    @Test
    fun `채널 규칙 - dev 와 local 만 덮어 설치 안내 대상이다`() {
        assertTrue(UpdateChannel.isPreview("dev"))
        assertTrue(UpdateChannel.isPreview("local"))
        assertTrue(!UpdateChannel.isPreview("release"))
    }
}
