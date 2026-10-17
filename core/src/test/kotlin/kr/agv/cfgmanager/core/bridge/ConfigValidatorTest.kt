package kr.agv.cfgmanager.core.bridge

import kr.agv.cfgmanager.core.testsupport.validRaw
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigValidatorTest {

    private fun errorsFor(key: ConfigKey, value: String): List<ValidationCode> {
        val report = ConfigValidator.validate(validRaw(key to value))
        return report.messagesFor(key)
            .filter { it.severity == ValidationSeverity.Error }
            .map { it.code }
    }

    private fun assertOk(key: ConfigKey, value: String) {
        assertTrue(errorsFor(key, value).isEmpty(), "$key=$value 은 통과해야 함: ${errorsFor(key, value)}")
    }

    private fun assertError(key: ConfigKey, value: String, code: ValidationCode) {
        assertEquals(listOf(code), errorsFor(key, value), "$key=$value")
    }

    @Test
    fun `정상 입력은 오류 없이 BridgeConfig 를 만든다`() {
        val report = ConfigValidator.validate(validRaw())
        assertTrue(!report.hasErrors, "${report.messages}")
        val config = assertNotNull(report.config)
        assertEquals(7, config.agv)
        assertEquals("agvpass12", config.pass)
        assertEquals(9, config.passLength)
        assertEquals("192.168.10.107", config.ip)
        assertEquals("192.168.10.1", config.dns)
        assertNull(config.dns2)
        assertEquals("255.255.255.255", config.rfidDst)
    }

    @Test
    fun `호기 번호는 1 부터 99 까지`() {
        assertError(ConfigKey.AGV, "0", ValidationCode.RANGE)
        assertOk(ConfigKey.AGV, "1")
        assertOk(ConfigKey.AGV, "99")
        assertError(ConfigKey.AGV, "100", ValidationCode.RANGE)
        assertError(ConfigKey.AGV, "", ValidationCode.REQUIRED)
        assertError(ConfigKey.AGV, "1a", ValidationCode.FORMAT)
        assertError(ConfigKey.AGV, "-1", ValidationCode.FORMAT)
    }

    @Test
    fun `SSID 는 바이트 기준 32 이하이고 앞뒤 공백을 허용하지 않는다`() {
        assertOk(ConfigKey.SSID, "a".repeat(32))
        assertError(ConfigKey.SSID, "a".repeat(33), ValidationCode.BYTES)
        // 한글 한 글자는 3바이트: 10자 = 30바이트 통과, 11자 = 33바이트 거부
        assertOk(ConfigKey.SSID, "가".repeat(10))
        assertError(ConfigKey.SSID, "가".repeat(11), ValidationCode.BYTES)
        assertError(ConfigKey.SSID, " AGV", ValidationCode.EDGE_SPACE)
        assertError(ConfigKey.SSID, "AGV ", ValidationCode.EDGE_SPACE)
        assertOk(ConfigKey.SSID, "AGV WIFI 2F")
        assertError(ConfigKey.SSID, "", ValidationCode.REQUIRED)
    }

    @Test
    fun `비밀번호는 빈 값이면 유지, 하이픈은 없음, 나머지는 8~64바이트`() {
        assertNull(ConfigValidator.validate(validRaw(ConfigKey.PASS to "")).config?.pass)
        val open = assertNotNull(ConfigValidator.validate(validRaw(ConfigKey.PASS to "-")).config)
        assertEquals("", open.pass)
        assertEquals(0, open.passLength)

        assertError(ConfigKey.PASS, "1234567", ValidationCode.BYTES)
        assertOk(ConfigKey.PASS, "12345678")
        assertOk(ConfigKey.PASS, "a".repeat(64))
        assertError(ConfigKey.PASS, "a".repeat(65), ValidationCode.BYTES)
        // 한글 3글자 = 9바이트: 8바이트 이상이므로 통과
        assertOk(ConfigKey.PASS, "가나다")
        assertError(ConfigKey.PASS, " abcdefgh", ValidationCode.EDGE_SPACE)
    }

    @Test
    fun `IP 는 dhcp 또는 고정 주소이고 앞자리 0 과 0 0 0 0 을 거부한다`() {
        val dhcp = assertNotNull(ConfigValidator.validate(validRaw(ConfigKey.IP to "dhcp")).config)
        assertNull(dhcp.ip)
        assertOk(ConfigKey.IP, "192.168.10.1")
        assertError(ConfigKey.IP, "192.168.010.1", ValidationCode.FORMAT)
        assertError(ConfigKey.IP, "192.168.10.256", ValidationCode.FORMAT)
        assertError(ConfigKey.IP, "192.168.10", ValidationCode.FORMAT)
        assertError(ConfigKey.IP, "0.0.0.0", ValidationCode.ZERO_ADDRESS)
        assertError(ConfigKey.IP, "", ValidationCode.REQUIRED)
    }

    @Test
    fun `마스크는 0 0 0 0 을 거부하고 연속형이 아니면 경고만 한다`() {
        assertError(ConfigKey.MASK, "0.0.0.0", ValidationCode.ZERO_ADDRESS)
        val report = ConfigValidator.validate(validRaw(ConfigKey.MASK to "255.0.255.0"))
        assertTrue(report.messagesFor(ConfigKey.MASK).any { it.code == ValidationCode.MASK_NONCONTIGUOUS })
        assertNotNull(report.config)
    }

    @Test
    fun `게이트웨이는 0 0 0 0 을 허용한다`() {
        assertOk(ConfigKey.GW, "0.0.0.0")
        assertError(ConfigKey.GW, "x", ValidationCode.FORMAT)
    }

    @Test
    fun `DNS 는 auto 또는 빈 값이 자동이고 0 0 0 0 은 거부한다`() {
        assertNull(ConfigValidator.validate(validRaw(ConfigKey.DNS to "auto")).config?.dns)
        assertNull(ConfigValidator.validate(validRaw(ConfigKey.DNS to "")).config?.dns)
        assertOk(ConfigKey.DNS, "8.8.8.8")
        assertError(ConfigKey.DNS, "0.0.0.0", ValidationCode.ZERO_ADDRESS)
        assertError(ConfigKey.DNS2, "1.2.3", ValidationCode.FORMAT)
    }

    @Test
    fun `서버 IP 는 0 0 0 0 을 거부한다`() {
        assertError(ConfigKey.SERVER, "0.0.0.0", ValidationCode.ZERO_ADDRESS)
    }

    @Test
    fun `포트는 1 부터 65535 까지`() {
        for (key in listOf(ConfigKey.SERVER_PORT, ConfigKey.LOCAL_PORT, ConfigKey.RFID_PORT)) {
            assertError(key, "0", ValidationCode.RANGE)
            assertOk(key, "1")
            assertOk(key, "65535")
            assertError(key, "65536", ValidationCode.RANGE)
        }
    }

    @Test
    fun `rfid_dst 는 off 가 보내지 않음, 0 0 0 0 은 거부`() {
        assertNull(ConfigValidator.validate(validRaw(ConfigKey.RFID_DST to "off")).config?.rfidDst)
        assertOk(ConfigKey.RFID_DST, "255.255.255.255")
        assertError(ConfigKey.RFID_DST, "0.0.0.0", ValidationCode.ZERO_ADDRESS)
    }

    @Test
    fun `보드레이트는 1200 부터 1000000 까지`() {
        for (key in listOf(ConfigKey.MAIN_BAUD, ConfigKey.RFID_BAUD)) {
            assertError(key, "1199", ValidationCode.RANGE)
            assertOk(key, "1200")
            assertOk(key, "1000000")
            assertError(key, "1000001", ValidationCode.RANGE)
        }
    }

    @Test
    fun `heartbeat 은 0 또는 100 부터 2000 까지`() {
        assertOk(ConfigKey.HB_MS, "0")
        assertError(ConfigKey.HB_MS, "1", ValidationCode.RANGE)
        assertError(ConfigKey.HB_MS, "99", ValidationCode.RANGE)
        assertOk(ConfigKey.HB_MS, "100")
        assertOk(ConfigKey.HB_MS, "2000")
        assertError(ConfigKey.HB_MS, "2001", ValidationCode.RANGE)
    }

    @Test
    fun `서버와 IP 가 다른 서브넷이면 경고하고 설정은 만든다`() {
        val report = ConfigValidator.validate(validRaw(ConfigKey.SERVER to "192.168.20.10"))
        val warning = report.messagesFor(ConfigKey.SERVER).single()
        assertEquals(ValidationSeverity.Warning, warning.severity)
        assertEquals(ValidationCode.SUBNET_MISMATCH, warning.code)
        assertNotNull(report.config)
    }

    @Test
    fun `게이트웨이가 IP 와 다른 네트워크면 경고한다`() {
        val report = ConfigValidator.validate(validRaw(ConfigKey.GW to "192.168.11.1"))
        assertTrue(report.messagesFor(ConfigKey.GW).any { it.code == ValidationCode.GW_OUTSIDE_SUBNET })
    }

    @Test
    fun `이미 쓰인 호기 번호는 경고한다`() {
        val report = ConfigValidator.validate(
            validRaw(),
            ValidationContext(takenAgv = mapOf(7 to "2026-10-02 에 다른 보드에 설정됐습니다")),
        )
        val warning = report.messagesFor(ConfigKey.AGV).single()
        assertEquals(ValidationCode.DUPLICATE_AGV, warning.code)
        assertTrue(warning.text.contains("07호기"))
        assertNotNull(report.config)
    }

    @Test
    fun `오류가 하나라도 있으면 설정을 만들지 않는다`() {
        val report = ConfigValidator.validate(validRaw(ConfigKey.HB_MS to "5"))
        assertTrue(report.hasErrors)
        assertNull(report.config)
    }

    @Test
    fun `키가 통째로 빠져 있으면 필수 오류`() {
        val raw = validRaw().toMutableMap().apply { remove(ConfigKey.MAIN_BAUD) }
        val report = ConfigValidator.validate(raw)
        assertTrue(report.messagesFor(ConfigKey.MAIN_BAUD).any { it.code == ValidationCode.REQUIRED })
        assertNull(report.config)
    }
}
