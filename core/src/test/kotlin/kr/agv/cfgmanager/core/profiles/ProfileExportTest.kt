package kr.agv.cfgmanager.core.profiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileExportTest {

    private val profile = ProfileCodec.decodeDocument(
        """
        {"version":1,"lastProfileId":null,"profiles":[{"id":"p1","name":"현장","notes":"",
        "createdAt":"2026-10-17T09:00:00Z","updatedAt":"2026-10-17T09:00:00Z","lastUsedAt":null,
        "wifi":{"ssid":"AGV_WIFI"},"ipRule":{"mode":"dhcp"},"mask":"255.255.255.0","gw":"192.168.10.1",
        "dns":"auto","dns2":"auto","server":"192.168.10.10","serverPort":50000,"localPort":50000,
        "rfidDst":"off","rfidPort":50001,"mainBaud":115200,"rfidBaud":9600,"hbMs":1000,
        "blePassword":"agvsetup"}]}
        """.trimIndent(),
    ).profiles.single()

    private val bundle = ExportBundle(
        profiles = listOf(profile),
        networks = listOf(WifiNetwork("AGV_WIFI", "agv12345", open = false, lastUsedAt = null, useCount = 2)),
    )

    @Test
    fun `비밀번호를 뺀 내보내기에는 BLE 와 Wi-Fi 비밀번호가 없다`() {
        val text = ProfileExport.export(bundle, includePasswords = false)
        assertFalse(text.contains("agvsetup"))
        assertFalse(text.contains("agv12345"))
        assertFalse(ProfileExport.containsPasswords(text))
    }

    @Test
    fun `비밀번호를 넣은 내보내기는 머리 표시가 true 이고 값을 담는다`() {
        val text = ProfileExport.export(bundle, includePasswords = true)
        assertTrue(text.contains("agvsetup"))
        assertTrue(text.contains("agv12345"))
        assertTrue(ProfileExport.containsPasswords(text))
    }

    @Test
    fun `가져오기는 프로필과 라이브러리를 되돌린다`() {
        val imported = ProfileExport.import(ProfileExport.export(bundle, includePasswords = true))
        assertTrue(imported.containsPasswords)
        assertEquals(bundle.profiles, imported.bundle.profiles)
        assertEquals("agv12345", imported.bundle.networks.single().password)
        assertEquals(2, imported.bundle.networks.single().useCount)
    }

    @Test
    fun `비밀번호 없는 파일을 가져오면 비밀번호는 비어 있다`() {
        val imported = ProfileExport.import(ProfileExport.export(bundle, includePasswords = false))
        assertFalse(imported.containsPasswords)
        assertEquals("", imported.bundle.profiles.single().blePassword)
        assertEquals(null, imported.bundle.networks.single().password)
    }
}
