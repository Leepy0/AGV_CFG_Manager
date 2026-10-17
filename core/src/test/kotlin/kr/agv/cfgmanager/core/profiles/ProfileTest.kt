package kr.agv.cfgmanager.core.profiles

import kr.agv.cfgmanager.core.bridge.ConfigKey
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTest {

    /** 부록 B-1 의 예시 (비밀번호 없는 형태에 blePassword 를 더함). */
    private val sample = """
        {
          "version": 1,
          "lastProfileId": "hq-f1",
          "profiles": [
            {
              "id": "hq-f1",
              "name": "본사 1공장",
              "notes": "DNS 는 사내 공통",
              "createdAt": "2026-10-17T09:00:00Z",
              "updatedAt": "2026-10-17T09:00:00Z",
              "lastUsedAt": null,
              "wifi": { "ssid": "AGV_WIFI" },
              "ipRule": { "mode": "offset", "baseNetwork": "192.168.10.0", "offset": 100 },
              "mask": "255.255.255.0",
              "gw": "192.168.10.1",
              "dns": "192.168.10.1",
              "dns2": "8.8.8.8",
              "server": "192.168.10.10",
              "serverPort": 50000,
              "localPort": 50000,
              "rfidDst": "255.255.255.255",
              "rfidPort": 50001,
              "mainBaud": 115200,
              "rfidBaud": 9600,
              "hbMs": 1000,
              "blePassword": "agvsetup",
              "futureField": { "keep": true }
            }
          ],
          "futureRoot": [1, 2]
        }
    """.trimIndent()

    @Test
    fun `부록 B-1 형식을 읽고 다시 쓰면 같은 프로필이 된다`() {
        val doc = ProfileCodec.decodeDocument(sample)
        val profile = doc.profiles.single()
        assertEquals("본사 1공장", profile.name)
        assertEquals(IpRule.Offset("192.168.10.0", 100), profile.ipRule)
        assertEquals("AGV_WIFI", profile.ssid)
        assertNull(profile.lastUsedAt)
        assertEquals("hq-f1", doc.lastProfileId)

        val again = ProfileCodec.decodeDocument(ProfileCodec.encodeDocument(doc))
        assertEquals(doc.profiles, again.profiles)
        assertEquals(doc.lastProfileId, again.lastProfileId)
    }

    @Test
    fun `모르는 필드는 프로필과 루트 모두 보존된다`() {
        val doc = ProfileCodec.decodeDocument(sample)
        val text = ProfileCodec.encodeDocument(doc)
        assertTrue(text.contains("futureField"))
        assertTrue(text.contains("futureRoot"))
        val futureField = doc.profiles.single().extras["futureField"] as JsonObject
        assertEquals(JsonPrimitive(true), futureField["keep"])
    }

    @Test
    fun `내보내기에서 BLE 비밀번호를 빼면 파일에 없다`() {
        val profile = ProfileCodec.decodeDocument(sample).profiles.single()
        assertFalse(ProfileCodec.encodeProfile(profile, includeSecrets = false).containsKey("blePassword"))
        assertTrue(ProfileCodec.encodeProfile(profile, includeSecrets = true).containsKey("blePassword"))
    }

    @Test
    fun `IP 규칙 세 가지 모드를 읽는다 알 수 없는 모드는 직접 입력으로 본다`() {
        val offsetRule = "\"mode\": \"offset\", \"baseNetwork\": \"192.168.10.0\", \"offset\": 100"
        val dhcp = sample.replace(offsetRule, "\"mode\": \"dhcp\"")
        assertEquals(IpRule.Dhcp, ProfileCodec.decodeDocument(dhcp).profiles.single().ipRule)
        val manual = sample.replace(offsetRule, "\"mode\": \"future\"")
        assertEquals(IpRule.Manual, ProfileCodec.decodeDocument(manual).profiles.single().ipRule)
    }

    @Test
    fun `필수 필드가 없으면 예외`() {
        val noServer = sample.replace("\"server\": \"192.168.10.10\",", "")
        assertFailsWith<IllegalArgumentException> { ProfileCodec.decodeDocument(noServer) }
        assertFailsWith<IllegalArgumentException> { ProfileCodec.decodeDocument("not json") }
    }

    @Test
    fun `프로필 초깃값은 호기 번호로 IP 를 계산하고 비밀번호는 넘긴 값을 쓴다`() {
        val profile = ProfileCodec.decodeDocument(sample).profiles.single()
        val draft = profile.draft(agv = 7, wifiPassword = "agv12345")
        assertEquals("7", draft[ConfigKey.AGV])
        assertEquals("192.168.10.107", draft[ConfigKey.IP])
        assertEquals("agv12345", draft[ConfigKey.PASS])
        assertEquals("8.8.8.8", draft[ConfigKey.DNS2])
        assertEquals("", profile.draft(agv = 7, wifiPassword = null)[ConfigKey.PASS])
    }

    @Test
    fun `IP 규칙 계산은 범위와 기준 네트워크를 검사한다`() {
        val rule = IpRule.Offset("192.168.10.0", 100)
        assertEquals(IpComputation.Ok("192.168.10.107"), IpRuleCalculator.compute(rule, 7))
        assertEquals(IpComputation.Ok("192.168.10.199"), IpRuleCalculator.compute(rule, 99))
        assertEquals(IpComputation.OutOfRange(255), IpRuleCalculator.compute(IpRule.Offset("192.168.10.0", 100), 155))
        assertEquals(IpComputation.OutOfRange(-9), IpRuleCalculator.compute(IpRule.Offset("192.168.10.0", -10), 1))
        assertEquals(IpComputation.BadBase, IpRuleCalculator.compute(IpRule.Offset("192.168.10", 100), 7))
        assertEquals(IpComputation.NotComputed, IpRuleCalculator.compute(IpRule.Dhcp, 7))
        assertEquals(IpComputation.NotComputed, IpRuleCalculator.compute(IpRule.Manual, 7))
        assertIs<IpComputation.Ok>(IpRuleCalculator.compute(IpRule.Offset("10.0.0.0", 1), 1))
    }
}
