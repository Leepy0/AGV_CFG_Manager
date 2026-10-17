package kr.agv.cfgmanager.ui.config

import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.core.bridge.ValidationContext
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormHintsTest {

    private val draft = ConfigDraft(
        values = mapOf(
            ConfigKey.AGV to "7", ConfigKey.SSID to "AGV_WIFI", ConfigKey.PASS to "",
            ConfigKey.IP to "192.168.10.107", ConfigKey.MASK to "255.255.255.0", ConfigKey.GW to "192.168.10.1",
            ConfigKey.DNS to "auto", ConfigKey.DNS2 to "auto", ConfigKey.SERVER to "192.168.10.10",
            ConfigKey.SERVER_PORT to "50000", ConfigKey.LOCAL_PORT to "50000", ConfigKey.RFID_DST to "off",
            ConfigKey.RFID_PORT to "50001", ConfigKey.MAIN_BAUD to "115200", ConfigKey.RFID_BAUD to "9600",
            ConfigKey.HB_MS to "1000",
        ),
        ipMode = IpMode.Manual,
    )

    @Test
    fun `이력에서 다른 보드가 쓴 호기는 경고만 내고 적용은 막지 않는다`() {
        val check = ConfigForm.check(draft, ValidationContext(mapOf(7 to "2026-10-02 에 다른 보드에 설정했습니다")))
        val warning = assertNotNull(check.messageFor(ConfigKey.AGV))
        assertTrue(!warning.isError)
        assertTrue(warning.text.contains("07호기는 2026-10-02"))
        assertNotNull(check.config)
    }

    @Test
    fun `장치와 다른 SSID 에 비밀번호가 필요하면 비밀번호 칸에 오류를 붙인다`() {
        val other = draft.with(ConfigKey.SSID, "NEW_WIFI")
        val check = ConfigForm.check(other, passwordRequired = true)
        val message = assertNotNull(check.messageFor(ConfigKey.PASS))
        assertTrue(message.isError)
        assertNull(check.config)
    }
}
