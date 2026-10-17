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
}
