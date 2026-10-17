package kr.agv.cfgmanager.core.bridge

import kr.agv.cfgmanager.core.util.Masking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse

class BridgeCommandsTest {

    @Test
    fun `BLE 명령은 따옴표 없이 줄 끝까지 값을 싣는다`() {
        val line = BridgeCommands.set(ChannelKind.Ble, ConfigKey.SSID, "AGV WIFI 2F")
        assertEquals(BridgeCommands.SetLine.Ok("set ssid AGV WIFI 2F"), line)
    }

    @Test
    fun `BLE 명령 줄은 199바이트까지이고 넘으면 거부한다`() {
        // "set ssid " 는 9바이트. 값이 190바이트면 정확히 199바이트
        val fits = BridgeCommands.set(ChannelKind.Ble, ConfigKey.SSID, "a".repeat(190))
        assertIs<BridgeCommands.SetLine.Ok>(fits)
        val tooLong = BridgeCommands.set(ChannelKind.Ble, ConfigKey.SSID, "a".repeat(191))
        assertEquals(BridgeCommands.SetLine.TooLong(200, 199), tooLong)
    }

    @Test
    fun `BLE 명령은 한글을 바이트로 센다`() {
        // 한글 3바이트 x 64 = 192 바이트 값 + "set pass " 9 = 201 → 거부
        val tooLong = BridgeCommands.set(ChannelKind.Ble, ConfigKey.PASS, "가".repeat(64))
        assertIs<BridgeCommands.SetLine.TooLong>(tooLong)
    }

    @Test
    fun `콘솔 명령은 공백 값을 큰따옴표로 감싼다`() {
        val line = BridgeCommands.set(ChannelKind.UsbSerial, ConfigKey.SSID, "AGV WIFI 2F")
        assertEquals(BridgeCommands.SetLine.Ok("set ssid \"AGV WIFI 2F\""), line)
    }

    @Test
    fun `콘솔 인용은 따옴표와 역슬래시를 이스케이프한다`() {
        assertEquals("\"a\\\"b\\\\c d\"", BridgeCommands.quoteConsoleArg("a\"b\\c d"))
        assertEquals("plain-value", BridgeCommands.quoteConsoleArg("plain-value"))
        assertEquals("\"\"", BridgeCommands.quoteConsoleArg(""))
    }

    @Test
    fun `콘솔 명령 줄은 160바이트까지`() {
        // "set ssid " 9바이트 + 인용 2바이트 = 11, 값 149 바이트 → 160
        val fits = BridgeCommands.set(ChannelKind.UsbSerial, ConfigKey.SSID, "a b" + "c".repeat(146))
        assertIs<BridgeCommands.SetLine.Ok>(fits)
        val tooLong = BridgeCommands.set(ChannelKind.UsbSerial, ConfigKey.SSID, "a b" + "c".repeat(150))
        assertIs<BridgeCommands.SetLine.TooLong>(tooLong)
    }

    @Test
    fun `빈 값과 줄바꿈이 든 값은 보내지 않는다`() {
        assertIs<BridgeCommands.SetLine.Invalid>(BridgeCommands.set(ChannelKind.Ble, ConfigKey.SSID, ""))
        assertIs<BridgeCommands.SetLine.Invalid>(BridgeCommands.set(ChannelKind.Ble, ConfigKey.SSID, "a\nb"))
    }

    @Test
    fun `명령 문자열과 setup 인자`() {
        assertEquals("cfg", BridgeCommands.cfg())
        assertEquals("stat", BridgeCommands.stat())
        assertEquals("save", BridgeCommands.save())
        assertEquals("reboot", BridgeCommands.reboot())
        assertEquals("setup", BridgeCommands.setup(null))
        assertEquals("setup on", BridgeCommands.setup(true))
        assertEquals("setup off", BridgeCommands.setup(false))
        assertEquals("login agvsetup", BridgeCommands.login("agvsetup"))
    }

    @Test
    fun `로그에는 비밀번호 인자가 가려진다`() {
        assertEquals("login ***", Masking.redactLine("login agvsetup"))
        assertEquals("set pass ***", Masking.redactLine("set pass agv12345"))
        assertEquals("set pass ***", Masking.redactLine("set pass \"a b\""))
        assertEquals("set ssid AGV", Masking.redactLine("set ssid AGV"))
        assertEquals("", Masking.hide(""))
        assertEquals(Masking.HIDDEN, Masking.hide("x"))
        assertFalse(Masking.redactLine("login agvsetup").contains("agvsetup"))
    }
}
