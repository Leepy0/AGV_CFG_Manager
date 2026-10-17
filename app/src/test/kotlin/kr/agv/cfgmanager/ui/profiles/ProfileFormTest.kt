package kr.agv.cfgmanager.ui.profiles

import kr.agv.cfgmanager.core.profiles.IpRule
import kr.agv.cfgmanager.core.profiles.ProfileTemplate
import kr.agv.cfgmanager.core.util.PhoneWifiFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class ProfileFormTest {

    private val profile = ProfileTemplate.headquarters("2026-10-17T09:00:00Z")

    @Test
    fun `프로필 왕복은 값을 보존한다`() {
        val form = ProfileForm.of(profile)
        val back = form.toProfile("2026-10-17T10:00:00Z")
        assertEquals(profile.copy(updatedAt = "2026-10-17T10:00:00Z"), back)
        assertTrue(form.issues("2026-10-17T10:00:00Z").none { it.isError })
    }

    @Test
    fun `예시 IP 는 호기 7 을 규칙대로 계산하고 범위 밖이면 null 이다`() {
        val form = ProfileForm.of(profile)
        assertEquals("192.168.10.107", form.exampleIp(7))
        assertNull(form.copy(offset = "250").exampleIp(7))
        assertNull(form.copy(ruleMode = RuleMode.Dhcp).exampleIp(7))
    }

    @Test
    fun `숫자 칸이 잘못되면 저장을 막는 안내를 낸다`() {
        val form = ProfileForm.of(profile).copy(serverPort = "abc")
        assertEquals(listOf(kr.agv.cfgmanager.core.profiles.ProfileField.ServerPort), form.numericErrors().map { it.field })
        assertTrue(form.issues("t").single().isError)
    }

    @Test
    fun `휴대폰 Wi-Fi 값은 빈 칸을 덮지 않고 기준 네트워크를 맞춘다`() {
        val fields = PhoneWifiFields(
            ssid = "AGV WIFI 2F", mask = null, gateway = "192.168.30.1", dns = null, dns2 = null,
            networkBase = "192.168.30.0", fiveGhz = true, missing = listOf("마스크", "DNS"),
        )
        val updated = ProfileForm.of(profile).copy(ruleMode = RuleMode.Dhcp).withPhoneWifi(fields)
        assertEquals("AGV WIFI 2F", updated.ssid)
        assertEquals("255.255.255.0", updated.mask)
        assertEquals("192.168.30.1", updated.gw)
        assertEquals("auto", updated.dns)
        assertEquals("192.168.30.0", updated.baseNetwork)
        assertEquals(RuleMode.Offset, updated.ruleMode)
        assertFalse(updated.toProfile("t").ipRule == IpRule.Dhcp)
    }
}
