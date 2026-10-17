package kr.agv.cfgmanager.core.fake

import kr.agv.cfgmanager.core.bridge.ResponseParsers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeDeviceTest {

    @Test
    fun `환영 문구는 부록 A-1 과 같은 모양이다`() {
        val device = FakeDevice()
        assertEquals("AGV Wi-Fi Bridge AGVBR-01-A1B2 (fw 0.5.5)\nlogin <password>\n", device.welcome())
        val open = FakeDevice(FakeDeviceOptions(blePassword = ""))
        assertTrue(open.welcome().endsWith("type help\n"))
    }

    @Test
    fun `로그인 전 명령은 거부되고 올바른 비밀번호로 열린다`() {
        val device = FakeDevice()
        assertEquals("login required: login <password>\n", device.handle("cfg").text)
        assertEquals("ok - type help\n", device.handle("login agvsetup").text)
        assertTrue(device.handle("cfg").text.startsWith("agv=1 "))
    }

    @Test
    fun `틀린 비밀번호는 지연 뒤 거절되고 다섯 번째에 끊는다`() {
        val device = FakeDevice()
        repeat(4) {
            val reply = device.handle("login wrong")
            assertEquals("wrong password\n", reply.text)
            assertEquals(1000L, reply.delayMs)
            assertEquals(FakeAction.None, reply.action)
        }
        val fifth = device.handle("login wrong")
        assertEquals(FakeAction.Disconnect, fifth.action)
        assertEquals("wrong password - too many failures, disconnecting\n", fifth.text)
    }

    @Test
    fun `set 은 편집 사본만 바꾸고 cfg 가 차이를 알린다`() {
        val device = FakeDevice()
        device.handle("login agvsetup")
        assertEquals("ok ssid - 'save' then 'reboot' to apply\n", device.handle("set ssid AGV WIFI 2F").text)
        val cfg = device.handle("cfg").text
        assertTrue(cfg.contains("ssid=\"AGV WIFI 2F\""))
        assertTrue(cfg.contains("state=default"))
        assertTrue(cfg.contains("* differs from running config"))
        assertFalse(device.saved != null)
    }

    @Test
    fun `set 규칙은 브리지와 같다 앞자리 0 허용 0 0 0 0 dhcp 허용 범위 밖 거부`() {
        val device = FakeDevice()
        device.handle("login agvsetup")
        assertEquals("ok ip - 'save' then 'reboot' to apply\n", device.handle("set ip 192.168.010.1").text)
        assertEquals("invalid key or value: agv\n", device.handle("set agv 100").text)
        assertEquals("ok ip - 'save' then 'reboot' to apply\n", device.handle("set ip dhcp").text)
        assertEquals("invalid key or value: mask\n", device.handle("set mask 0.0.0.0").text)
        assertEquals("invalid key or value: pass\n", device.handle("set pass short").text)
        assertEquals("ok pass - 'save' then 'reboot' to apply\n", device.handle("set pass -").text)
        assertEquals("invalid key or value: ssid\n", device.handle("set ssid 123456789012345678901234567890123").text)
    }

    @Test
    fun `인자가 없는 set 은 사용법 세 줄을 낸다`() {
        val device = FakeDevice()
        device.handle("login agvsetup")
        val usage = device.handle("set").text.trimEnd('\n').split('\n')
        assertEquals(3, usage.size)
        assertTrue(usage[0].startsWith("usage: set <key> <value>"))
    }

    @Test
    fun `save 와 reboot 는 저장 설정을 실행 설정으로 올린다`() {
        val device = FakeDevice()
        device.handle("login agvsetup")
        device.handle("set agv 7")
        assertEquals("saved - 'reboot' to apply\n", device.handle("save").text)
        assertEquals(7, device.saved?.agv)
        assertEquals(FakeAction.Reboot, device.handle("reboot").action)
        device.reboot()
        assertEquals(7, device.running.agv)
        assertTrue(device.setupOpen)
        assertEquals("login required: login <password>\n", device.handle("cfg").text, "재부팅하면 로그인부터")
        device.handle("login agvsetup")
        assertTrue(device.handle("cfg").text.contains("state=saved"))
    }

    @Test
    fun `메인보드 작업 중에는 재부팅을 거부한다`() {
        val device = FakeDevice(FakeDeviceOptions(busy = true))
        device.handle("login agvsetup")
        val reply = device.handle("reboot")
        assertEquals("busy: mainboard programming - reboot refused, retry when done\n", reply.text)
        assertEquals(FakeAction.None, reply.action)
    }

    @Test
    fun `저장 실패 시 저장된 설정은 그대로다`() {
        val device = FakeDevice(FakeDeviceOptions(saveFails = true))
        device.handle("login agvsetup")
        assertEquals("save failed: ESP_ERR_NVS_NOT_ENOUGH_SPACE\n", device.handle("save").text)
        assertEquals(null, device.saved)
    }

    @Test
    fun `stat 은 파서로 읽히고 Wi-Fi DOWN 사유를 낸다`() {
        val device = FakeDevice(FakeDeviceOptions(wifi = FakeWifi.Down(201)))
        device.handle("login agvsetup")
        val status = assertNotNull(ResponseParsers.parseStat(device.handle("stat").text))
        assertFalse(assertNotNull(status.wifi).up)
        assertEquals(201, assertNotNull(status.net).lastReason)
        assertEquals(3, status.net?.disconnects)
    }

    @Test
    fun `구버전 펌웨어는 dns 줄과 mac 토큰이 없다`() {
        val old = FakeDevice(FakeDeviceOptions(fw = "0.5.2"))
        old.handle("login agvsetup")
        assertFalse(old.handle("cfg").text.contains("dns="))
        assertFalse(old.handle("stat").text.contains("mac="))
    }

    @Test
    fun `help 는 선택 기능 명령을 덧붙이고 defaults 는 맨 뒤다`() {
        val device = FakeDevice(FakeDeviceOptions(helpExtras = listOf("discard")))
        device.handle("login agvsetup")
        assertEquals(
            "cfg | set <key> <value> | save | reboot | stat | setup [on|off] | dsp [ver|apply] | discard | defaults\n",
            device.handle("help").text,
        )
    }

    @Test
    fun `setup off 는 닫는 중 문구를 내고 광고를 닫으며 연결을 끊는다`() {
        val device = FakeDevice()
        device.handle("login agvsetup")
        val reply = device.handle("setup off")
        assertEquals("setup: on name=AGVBR-01-A1B2 ble=connected (closing)\n", reply.text)
        assertEquals(FakeAction.Disconnect, reply.action)
        assertFalse(device.setupOpen)
    }

    @Test
    fun `알 수 없는 명령은 안내를 낸다`() {
        val device = FakeDevice()
        device.handle("login agvsetup")
        assertEquals("unknown command 'foo' - type help\n", device.handle("foo bar").text)
    }

    @Test
    fun `IP 파서는 10진 네 옥텟만 받는다`() {
        assertEquals(0xC0A80A6BL, parseIpLike("192.168.10.107"))
        assertEquals(null, parseIpLike("192.168.10.256"))
        assertEquals(null, parseIpLike("192.168.10"))
    }
}
