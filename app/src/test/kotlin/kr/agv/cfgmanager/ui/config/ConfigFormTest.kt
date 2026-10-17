package kr.agv.cfgmanager.ui.config

import kr.agv.cfgmanager.core.bridge.BridgeConfig
import kr.agv.cfgmanager.core.bridge.ConfigKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigFormTest {

    private fun device(dns: String? = "192.168.10.1", ip: String? = "192.168.10.107") = BridgeConfig(
        agv = 7,
        ssid = "AGV_WIFI",
        pass = null,
        passLength = 8,
        ip = ip,
        mask = "255.255.255.0",
        gw = "192.168.10.1",
        dns = dns,
        dns2 = null,
        server = "192.168.10.10",
        serverPort = 50000,
        localPort = 50000,
        rfidDst = null,
        rfidPort = 50001,
        mainBaud = 115200,
        rfidBaud = 9600,
        hbMs = 1000,
    )

    @Test
    fun `장치 값으로 만든 초안은 바뀐 게 없고 검증도 통과한다`() {
        val dev = device()
        val draft = ConfigForm.fromDevice(dev)
        assertEquals(emptySet(), ConfigForm.changedKeys(draft, dev, dnsSupported = true))
        val check = ConfigForm.check(draft)
        assertEquals(0, check.errorCount)
        assertNotNull(check.config)
    }

    @Test
    fun `서버 IP 만 고치면 변경 1건이고 BridgeConfig diff 와 같다`() {
        val dev = device()
        val draft = ConfigForm.fromDevice(dev).with(ConfigKey.SERVER, "192.168.10.20")
        val changed = ConfigForm.changedKeys(draft, dev, dnsSupported = true)
        assertEquals(setOf(ConfigKey.SERVER), changed)
        val config = ConfigForm.check(draft).config
        assertNotNull(config)
        assertEquals(config.diff(dev), changed)
    }

    @Test
    fun `비밀번호를 입력하면 변경으로 세고 비우면 유지다`() {
        val dev = device()
        val base = ConfigForm.fromDevice(dev)
        assertFalse(ConfigKey.PASS in ConfigForm.changedKeys(base, dev, true))
        val typed = base.with(ConfigKey.PASS, "newpass123")
        assertTrue(ConfigKey.PASS in ConfigForm.changedKeys(typed, dev, true))
        assertEquals("newpass123".length, ConfigForm.check(typed).config?.passLength)
    }

    @Test
    fun `dns 를 지원하지 않는 장치에서는 dns 값이 달라도 변경으로 세지 않는다`() {
        val dev = device(dns = null)
        val draft = ConfigForm.fromDevice(dev).with(ConfigKey.DNS, "8.8.8.8")
        assertFalse(ConfigKey.DNS in ConfigForm.changedKeys(draft, dev, dnsSupported = false))
        assertTrue(ConfigKey.DNS in ConfigForm.changedKeys(draft, dev, dnsSupported = true))
    }

    @Test
    fun `IP 모드를 DHCP 로 바꾸면 ip 는 dhcp 가 되고 변경으로 센다`() {
        val dev = device()
        val draft = ConfigForm.fromDevice(dev).withIpMode(IpMode.Dhcp)
        assertEquals("dhcp", draft.raw(ConfigKey.IP))
        assertEquals(setOf(ConfigKey.IP), ConfigForm.changedKeys(draft, dev, true))
        assertNull(ConfigForm.check(draft).config?.ip)
    }

    @Test
    fun `IP 를 직접 고치면 모드가 직접 입력이 된다`() {
        val draft = ConfigForm.fromDevice(device(ip = null)).also { assertEquals(IpMode.Dhcp, it.ipMode) }
            .with(ConfigKey.IP, "192.168.10.108")
        assertEquals(IpMode.Manual, draft.ipMode)
    }

    @Test
    fun `잘못된 IP 는 항목 아래 오류 문구가 되고 적용 설정은 만들지 않는다`() {
        val draft = ConfigForm.fromDevice(device()).with(ConfigKey.IP, "192.168.10.256")
        val check = ConfigForm.check(draft)
        val message = check.messageFor(ConfigKey.IP)
        assertNotNull(message)
        assertTrue(message.isError)
        assertNull(check.config)
        assertTrue(check.errorCount >= 1)
    }

    @Test
    fun `IP 가 게이트웨이나 서버와 같으면 오류, 마지막 옥텟 255 는 경고만`() {
        val sameAsGw = ConfigForm.fromDevice(device()).with(ConfigKey.IP, "192.168.10.1")
        assertTrue(ConfigForm.check(sameAsGw).messageFor(ConfigKey.IP)!!.isError)
        assertNull(ConfigForm.check(sameAsGw).config)

        val broadcastLike = ConfigForm.fromDevice(device()).with(ConfigKey.IP, "192.168.10.255")
        val check = ConfigForm.check(broadcastLike)
        val warning = check.messageFor(ConfigKey.IP)
        assertNotNull(warning)
        assertFalse(warning.isError)
        assertNotNull(check.config)
    }

    @Test
    fun `호기 번호 0 은 검증 오류 문구로 나온다`() {
        val check = ConfigForm.check(ConfigForm.fromDevice(device()).with(ConfigKey.AGV, "0"))
        val message = check.messageFor(ConfigKey.AGV)
        assertNotNull(message)
        assertTrue(message.isError)
        assertTrue(message.text.contains("1~99"))
    }

    @Test
    fun `SSID 앞뒤 공백은 검증이 알리고 값은 그대로 둔다`() {
        val draft = ConfigForm.fromDevice(device()).with(ConfigKey.SSID, " AGV ")
        assertEquals(" AGV ", ConfigForm.validatorInput(draft)[ConfigKey.SSID])
        assertTrue(ConfigForm.check(draft).messageFor(ConfigKey.SSID)!!.isError)
    }

    @Test
    fun `프로필 값 주입은 호기 번호를 바꾸지 않는다`() {
        val draft = ConfigForm.fromDevice(device()).withProfileValues(
            mapOf(ConfigKey.AGV to "99", ConfigKey.GW to "192.168.10.254"),
        )
        assertEquals("7", draft.raw(ConfigKey.AGV))
        assertEquals("192.168.10.254", draft.raw(ConfigKey.GW))
    }
}
