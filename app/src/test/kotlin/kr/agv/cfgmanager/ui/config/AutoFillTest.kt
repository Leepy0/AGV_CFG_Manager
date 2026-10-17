package kr.agv.cfgmanager.ui.config

import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.core.profiles.IpRule
import kr.agv.cfgmanager.core.profiles.ProfileTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class AutoFillTest {

    private val profile = ProfileTemplate.headquarters("2026-10-17T09:00:00Z")

    @Test
    fun `프로필을 고르면 호기 번호 외 값을 채우고 IP 는 규칙으로 계산한다`() {
        val (draft, state) = AutoFill.applyProfile(null, profile, agv = 7, libraryPassword = "agvpass12")
        assertEquals("7", draft.raw(ConfigKey.AGV))
        assertEquals("192.168.10.107", draft.raw(ConfigKey.IP))
        assertEquals("AGV_TEST", draft.raw(ConfigKey.SSID))
        assertEquals("agvpass12", draft.raw(ConfigKey.PASS))
        assertEquals("255.255.255.0", draft.raw(ConfigKey.MASK))
        assertEquals(IpMode.Rule, draft.ipMode)
        assertFalse(state.overridden)
    }

    @Test
    fun `라이브러리에 없으면 기존 비밀번호 입력을 유지하고 오픈 AP 는 대시로 보낸다`() {
        val base = ConfigDraft(mapOf(ConfigKey.PASS to "typed1234"), IpMode.Manual)
        assertEquals("typed1234", AutoFill.applyProfile(base, profile, 7, null).first.raw(ConfigKey.PASS))
        assertEquals("-", AutoFill.applyProfile(base, profile, 7, "").first.raw(ConfigKey.PASS))
    }

    @Test
    fun `호기 번호를 바꾸면 규칙 모드 IP 를 다시 계산한다`() {
        val (draft, state) = AutoFill.applyProfile(null, profile, 7, null)
        val next = AutoFill.changeAgv(draft, state, 8)
        assertEquals("8", next.raw(ConfigKey.AGV))
        assertEquals("192.168.10.108", next.raw(ConfigKey.IP))
    }

    @Test
    fun `IP 를 직접 고친 뒤에는 호기를 바꿔도 IP 를 다시 계산하지 않고 규칙과 다름을 알린다`() {
        val (draft, state) = AutoFill.applyProfile(null, profile, 7, null)
        val typed = draft.with(ConfigKey.IP, "192.168.10.200")
        val overridden = state.copy(overridden = true)

        val next = AutoFill.changeAgv(typed, overridden, 8)
        assertEquals("192.168.10.200", next.raw(ConfigKey.IP))
        assertEquals("192.168.10.108", AutoFill.mismatchIp(next, overridden, 8))

        val restored = AutoFill.useRule(next, overridden, 8)!!
        assertEquals("192.168.10.108", restored.first.raw(ConfigKey.IP))
        assertEquals(IpMode.Rule, restored.first.ipMode)
        assertNull(AutoFill.mismatchIp(restored.first, restored.second, 8))
    }

    @Test
    fun `DHCP 와 직접 입력 규칙은 호기 번호로 IP 를 만들지 않는다`() {
        val dhcp = profile.copy(ipRule = IpRule.Dhcp)
        val (draft, state) = AutoFill.applyProfile(null, dhcp, 7, null)
        assertEquals("dhcp", draft.raw(ConfigKey.IP))
        assertEquals(IpMode.Dhcp, draft.ipMode)
        assertEquals("dhcp", AutoFill.changeAgv(draft, state, 9).raw(ConfigKey.IP))

        val manual = profile.copy(ipRule = IpRule.Manual)
        val base = ConfigDraft(mapOf(ConfigKey.IP to "192.168.10.55"), IpMode.Manual)
        assertEquals("192.168.10.55", AutoFill.applyProfile(base, manual, 7, null).first.raw(ConfigKey.IP))
    }

    @Test
    fun `규칙 범위를 벗어나면 IP 를 비워 검증이 알리게 한다`() {
        val far = profile.copy(ipRule = IpRule.Offset("192.168.10.0", 250))
        val (draft, state) = AutoFill.applyProfile(null, far, 7, null)
        assertEquals("", draft.raw(ConfigKey.IP))
        assertEquals("", AutoFill.changeAgv(draft, state, 60).raw(ConfigKey.IP))
    }

    @Test
    fun `장치와 다른 SSID 에 비밀번호가 없고 라이브러리에도 없으면 비밀번호가 필요하다`() {
        val draft = ConfigDraft(mapOf(ConfigKey.SSID to "NEW_WIFI", ConfigKey.PASS to ""), IpMode.Manual)
        assertTrue(AutoFill.passwordRequired(draft, deviceSsid = "AGV_WIFI", libraryHasSecret = false))
        assertFalse(AutoFill.passwordRequired(draft, deviceSsid = "AGV_WIFI", libraryHasSecret = true))
        assertFalse(AutoFill.passwordRequired(draft, deviceSsid = null, libraryHasSecret = false))
        assertFalse(AutoFill.passwordRequired(draft.with(ConfigKey.SSID, "AGV_WIFI"), "AGV_WIFI", false))
    }
}
