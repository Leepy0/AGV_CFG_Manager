package kr.agv.cfgmanager.core.profiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileValidatorTest {

    private val base = ProfileTemplate.headquarters("2026-10-17T09:00:00Z")

    private fun errorsOf(profile: Profile): Set<ProfileField> =
        ProfileValidator.validate(profile).filter { it.isError }.map { it.field }.toSet()

    @Test
    fun `이름이 비면 오류다`() {
        assertEquals(setOf(ProfileField.Name), errorsOf(base.copy(name = " ")))
    }

    @Test
    fun `IP 규칙 기준 네트워크가 잘못되면 오류, 호기 범위가 넘치면 경고다`() {
        assertEquals(setOf(ProfileField.IpRule), errorsOf(base.copy(ipRule = IpRule.Offset("192.168.10", 100))))
        val wide = ProfileValidator.validate(base.copy(ipRule = IpRule.Offset("192.168.10.0", 200)))
        val ipIssue = wide.single { it.field == ProfileField.IpRule }
        assertFalse(ipIssue.isError)
    }

    @Test
    fun `DNS 는 auto 또는 IP 만 허용한다`() {
        assertTrue(errorsOf(base.copy(dns = "auto")).isEmpty())
        assertEquals(setOf(ProfileField.Dns), errorsOf(base.copy(dns = "auto2")))
    }

    @Test
    fun `포트와 보드레이트 범위를 검사한다`() {
        assertEquals(setOf(ProfileField.ServerPort), errorsOf(base.copy(serverPort = 65536)))
        assertEquals(setOf(ProfileField.MainBaud), errorsOf(base.copy(mainBaud = 1199)))
        assertTrue(errorsOf(base.copy(mainBaud = 1200, rfidBaud = 1000000)).isEmpty())
    }

    @Test
    fun `BLE 비밀번호가 비면 경고만 낸다`() {
        val issues = ProfileValidator.validate(base.copy(blePassword = ""))
        assertEquals(listOf(ProfileField.BlePassword), issues.map { it.field })
        assertFalse(issues.single().isError)
    }
}
