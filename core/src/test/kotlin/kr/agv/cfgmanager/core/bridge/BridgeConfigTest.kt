package kr.agv.cfgmanager.core.bridge

import kr.agv.cfgmanager.core.testsupport.validTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BridgeConfigTest {

    private fun deviceConfig(): BridgeConfig = validTarget().copy(pass = null, passLength = 9)

    @Test
    fun `같은 설정이면 차이가 없다`() {
        assertTrue(deviceConfig().diff(deviceConfig()).isEmpty())
    }

    @Test
    fun `바뀐 IP 만 차이로 잡힌다 (비밀번호 입력이 없을 때)`() {
        val target = validTarget(ConfigKey.IP to "192.168.10.108").copy(pass = null, passLength = null)
        assertEquals(setOf(ConfigKey.IP), target.diff(deviceConfig()))
    }

    @Test
    fun `비밀번호는 입력이 있을 때만 변경으로 본다`() {
        val keep = validTarget().copy(pass = null, passLength = null)
        assertTrue(ConfigKey.PASS !in keep.diff(deviceConfig()))
        assertTrue(ConfigKey.PASS in validTarget().diff(deviceConfig()))
    }

    @Test
    fun `DHCP 와 null 은 같은 표기로 비교된다`() {
        val dhcp = validTarget(ConfigKey.IP to "dhcp")
        assertNull(dhcp.ip)
        assertTrue(ConfigKey.IP !in dhcp.diff(dhcp.copy(passLength = 9)))
    }

    @Test
    fun `와이어 값은 비밀번호 없음을 dash 로, 자동과 끔을 auto 와 off 로 보낸다`() {
        val config = validTarget(ConfigKey.PASS to "-", ConfigKey.DNS to "auto", ConfigKey.RFID_DST to "off")
        assertEquals("-", config.wireValue(ConfigKey.PASS))
        assertEquals("auto", config.wireValue(ConfigKey.DNS))
        assertEquals("off", config.wireValue(ConfigKey.RFID_DST))
        assertEquals("dhcp", validTarget(ConfigKey.IP to "dhcp").wireValue(ConfigKey.IP))
        assertNull(deviceConfig().wireValue(ConfigKey.PASS))
    }

    @Test
    fun `키 순서는 7_1 의 전송 순서를 따른다`() {
        assertEquals(
            listOf(
                "agv", "ssid", "pass", "ip", "mask", "gw", "dns", "dns2",
                "server", "server_port", "local_port", "rfid_dst", "rfid_port",
                "main_baud", "rfid_baud", "hb_ms",
            ),
            ConfigKey.entries.map { it.wire },
        )
    }
}
