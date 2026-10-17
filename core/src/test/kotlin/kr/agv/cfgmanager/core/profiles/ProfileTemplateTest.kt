package kr.agv.cfgmanager.core.profiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileTemplateTest {

    @Test
    fun `기본 본사 프로필은 오류 없이 검사를 통과하고 자리표시자 IP 규칙을 쓴다`() {
        val profile = ProfileTemplate.headquarters("2026-10-17T09:00:00Z")
        assertEquals("hq-default", profile.id)
        assertEquals(IpRule.Offset("192.168.10.0", 100), profile.ipRule)
        assertFalse(ProfileValidator.hasErrors(ProfileValidator.validate(profile)))
    }

    @Test
    fun `IP 규칙 예시는 호기 7 을 192 168 10 107 로 계산한다`() {
        val profile = ProfileTemplate.headquarters("2026-10-17T09:00:00Z")
        val draft = profile.draft(7, wifiPassword = null)
        assertEquals("192.168.10.107", draft[kr.agv.cfgmanager.core.bridge.ConfigKey.IP])
    }

    @Test
    fun `새 프로필은 본사 값과 BLE 비밀번호를 가져오고 이름 메모 SSID 는 새로 만든다`() {
        val hq = ProfileTemplate.headquarters("2026-10-17T09:00:00Z").copy(
            server = "10.1.2.3",
            gw = "10.1.2.1",
            blePassword = "field-pass",
            ssid = "HQ_WIFI",
        )
        val created = ProfileTemplate.newProfileFrom(hq, "new-id", "2026-10-18T09:00:00Z")
        assertEquals("new-id", created.id)
        assertEquals("새 프로필", created.name)
        assertEquals("", created.ssid)
        assertEquals("10.1.2.3", created.server)
        assertEquals("10.1.2.1", created.gw)
        assertEquals("field-pass", created.blePassword)
        assertEquals(null, created.lastUsedAt)
    }

    @Test
    fun `본사 프로필이 없으면 자리표시자 템플릿으로 새 프로필을 만든다`() {
        val created = ProfileTemplate.newProfileFrom(null, "new-id", "2026-10-18T09:00:00Z")
        assertEquals("192.168.10.10", created.server)
        assertEquals("agvsetup", created.blePassword)
    }
}
