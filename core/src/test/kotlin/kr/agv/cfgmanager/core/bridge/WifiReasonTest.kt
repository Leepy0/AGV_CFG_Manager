package kr.agv.cfgmanager.core.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WifiReasonTest {

    @Test
    fun `표에 있는 사유는 이름과 조치를 준다`() {
        val info = WifiReason.describe(201)
        assertEquals("NO_AP_FOUND", info.name)
        assertTrue(info.message.contains("AP"))
        assertEquals("4WAY_HANDSHAKE_TIMEOUT", WifiReason.describe(15).name)
        assertTrue(WifiReason.describe(15).message.contains("비밀번호"))
    }

    @Test
    fun `0 은 정상이고 이름이 없다`() {
        val info = WifiReason.describe(0)
        assertEquals("정상", info.message)
        assertNull(info.name)
    }

    @Test
    fun `표에 없는 코드는 코드 그대로 콘솔 확인을 안내한다`() {
        val info = WifiReason.describe(999)
        assertNull(info.name)
        assertTrue(info.message.contains("999"))
    }
}
