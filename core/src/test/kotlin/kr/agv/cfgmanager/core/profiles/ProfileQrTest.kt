package kr.agv.cfgmanager.core.profiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileQrTest {

    private val profile = ProfileCodec.decodeDocument(
        """
        {"version":1,"lastProfileId":null,"profiles":[{"id":"p1","name":"본사",
        "notes":"","createdAt":"2026-10-17T09:00:00Z","updatedAt":"2026-10-17T09:00:00Z","lastUsedAt":null,
        "wifi":{"ssid":"AGV_WIFI"},"ipRule":{"mode":"offset","baseNetwork":"192.168.10.0","offset":100},
        "mask":"255.255.255.0","gw":"192.168.10.1","dns":"auto","dns2":"auto","server":"192.168.10.10",
        "serverPort":50000,"localPort":50000,"rfidDst":"off","rfidPort":50001,"mainBaud":115200,
        "rfidBaud":9600,"hbMs":1000,"blePassword":"agvsetup"}]}
        """.trimIndent(),
    ).profiles.single()

    @Test
    fun `기본은 비밀번호를 뺀 한 줄이고 한 프로필로 되돌아온다`() {
        val text = ProfileQr.encode(profile, includePasswords = false)
        assertFalse(text.contains("agvsetup"))
        assertFalse(text.contains("\n"), "QR 은 한 줄")
        val decoded = ProfileQr.decode(text)
        assertEquals(listOf("본사"), decoded.bundle.profiles.map { it.name })
        assertEquals("", decoded.bundle.profiles.single().blePassword)
        assertTrue(decoded.bundle.networks.isEmpty(), "QR 에는 라이브러리를 싣지 않는다")
    }

    @Test
    fun `비밀번호 포함을 고르면 BLE 비밀번호가 함께 돌아온다`() {
        val text = ProfileQr.encode(profile, includePasswords = true)
        assertTrue(ProfileExport.containsPasswords(text))
        assertEquals("agvsetup", ProfileQr.decode(text).bundle.profiles.single().blePassword)
    }

    @Test
    fun `QR 한 장을 넘는 프로필은 만들지 않는다`() {
        val big = profile.copy(notes = "가".repeat(900))
        assertFailsWith<IllegalArgumentException> { ProfileQr.encode(big, includePasswords = false) }
    }

    @Test
    fun `JSON 이 아닌 글은 가져오기 형식 오류가 된다`() {
        assertFailsWith<IllegalArgumentException> { ProfileQr.decode("https://example.com/not-a-profile") }
    }
}
