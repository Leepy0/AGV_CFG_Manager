package kr.agv.cfgmanager.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneNetworkTest {

    @Test
    fun `prefix 길이를 마스크로 바꾼다`() {
        assertEquals("255.255.255.0", PhoneNetwork.maskFromPrefix(24))
        assertEquals("255.255.0.0", PhoneNetwork.maskFromPrefix(16))
        assertEquals("255.255.255.252", PhoneNetwork.maskFromPrefix(30))
        assertEquals("0.0.0.0", PhoneNetwork.maskFromPrefix(0))
        assertEquals("255.255.255.255", PhoneNetwork.maskFromPrefix(32))
        assertNull(PhoneNetwork.maskFromPrefix(33))
    }

    @Test
    fun `5GHz 대역을 판정하고 값이 없으면 null 이다`() {
        assertTrue(PhoneNetwork.isFiveGhz(5180) == true)
        assertFalse(PhoneNetwork.isFiveGhz(2437) == true)
        assertFalse(PhoneNetwork.isFiveGhz(2437) == null)
        assertNull(PhoneNetwork.isFiveGhz(null))
        assertNull(PhoneNetwork.isFiveGhz(0))
    }

    @Test
    fun `SSID 는 따옴표를 떼고 알 수 없음은 null 이다`() {
        assertEquals("AGV WIFI 2F", PhoneNetwork.cleanSsid("\"AGV WIFI 2F\""))
        assertNull(PhoneNetwork.cleanSsid("<unknown ssid>"))
        assertNull(PhoneNetwork.cleanSsid("\"\""))
        assertNull(PhoneNetwork.cleanSsid(null))
    }

    @Test
    fun `IP 와 마스크로 네트워크 주소를 구한다`() {
        assertEquals("192.168.10.0", PhoneNetwork.networkBase("192.168.10.57", "255.255.255.0"))
    }

    @Test
    fun `스냅샷을 편집 값으로 바꾸고 빠진 항목을 알린다`() {
        val full = PhoneWifiSnapshot(
            ssid = "\"AGV_WIFI\"", ipv4 = "192.168.10.57", prefix = 24, gateway = "192.168.10.1",
            dns = listOf("192.168.10.1", "8.8.8.8"), frequencyMhz = 2437,
        )
        val fields = PhoneNetwork.fieldsOf(full)
        assertEquals("AGV_WIFI", fields.ssid)
        assertEquals("255.255.255.0", fields.mask)
        assertEquals("192.168.10.0", fields.networkBase)
        assertEquals("8.8.8.8", fields.dns2)
        assertFalse(fields.fiveGhz)
        assertTrue(fields.missing.isEmpty())

        val partial = PhoneWifiSnapshot(
            ssid = null, ipv4 = "192.168.10.57", prefix = 24, gateway = "192.168.10.1",
            dns = emptyList(), frequencyMhz = 5180,
        )
        val p = PhoneNetwork.fieldsOf(partial)
        assertTrue(p.fiveGhz)
        assertNull(p.ssid)
        assertEquals(2, p.missing.size)
    }
}
