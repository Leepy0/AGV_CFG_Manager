package kr.agv.cfgmanager.core.bridge

import kotlin.test.Test
import kotlin.test.assertEquals

class UsbConsoleTest {

    @Test
    fun `응답의 echo 와 끝 프롬프트를 떼고 본문만 남긴다`() {
        val raw = "cfg\r\nstate=SAVED\r\nssid=AGV WIFI\r\n\u001B[0;32mbridge> \u001B[0m"
        assertEquals("state=SAVED\nssid=AGV WIFI", UsbConsole.replyBody(raw, "cfg"))
    }

    @Test
    fun `로그 줄은 응답에서 빠진다`() {
        val raw = "stat\r\nI (1234) wifi: connected\r\ndsp : ok\r\nbridge> "
        assertEquals("dsp : ok", UsbConsole.replyBody(raw, "stat"))
    }

    @Test
    fun `앞에 프롬프트가 붙은 echo 도 떼어 낸다`() {
        val raw = "bridge> set ssid \"AGV WIFI\"\r\nok - 'save' then 'reboot' to apply\r\nbridge> "
        assertEquals("ok - 'save' then 'reboot' to apply", UsbConsole.replyBody(raw, "set ssid \"AGV WIFI\""))
    }

    @Test
    fun `프롬프트만 온 응답은 빈 본문이다`() {
        assertEquals("", UsbConsole.replyBody("\r\nbridge> ", "\n"))
    }
}
