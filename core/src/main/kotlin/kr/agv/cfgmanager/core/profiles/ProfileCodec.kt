package kr.agv.cfgmanager.core.profiles

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * 프로필 JSON 코덱 (부록 B-1). 모르는 필드는 [Profile.extras] · [ProfileDocument.extras] 에 보관해 다시 쓸 때 보존한다.
 * 필수 필드가 없거나 형식이 틀리면 [IllegalArgumentException].
 */
object ProfileCodec {

    const val VERSION = 1

    private val json = Json { prettyPrint = true }

    private val profileKeys = setOf(
        "id", "name", "notes", "createdAt", "updatedAt", "lastUsedAt", "wifi", "ipRule", "mask", "gw",
        "dns", "dns2", "server", "serverPort", "localPort", "rfidDst", "rfidPort", "mainBaud", "rfidBaud",
        "hbMs", "blePassword",
    )

    private val documentKeys = setOf("version", "lastProfileId", "profiles")

    fun encodeDocument(document: ProfileDocument): String {
        val obj = buildJsonObject {
            document.extras.forEach { (k, v) -> if (k !in documentKeys) put(k, v) }
            put("version", document.version)
            put("lastProfileId", document.lastProfileId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("profiles", JsonArray(document.profiles.map { encodeProfile(it, includeSecrets = true) }))
        }
        return json.encodeToString(JsonElement.serializer(), obj)
    }

    fun decodeDocument(text: String): ProfileDocument {
        val root = parseObject(text)
        val profiles = (root["profiles"] ?: throw IllegalArgumentException("profiles 가 없습니다."))
            .let { element ->
                (element as? JsonArray)
                    ?: throw IllegalArgumentException("profiles 는 배열이어야 합니다.")
            }
            .map { decodeProfile(it.jsonObject) }
        return ProfileDocument(
            version = root.int("version") ?: VERSION,
            lastProfileId = root.string("lastProfileId"),
            profiles = profiles,
            extras = root.unknown(documentKeys),
        )
    }

    /** 프로필 하나를 JSON 객체로. [includeSecrets] = false 면 BLE 비밀번호를 뺀다(내보내기 기본). */
    fun encodeProfile(profile: Profile, includeSecrets: Boolean): JsonObject {
        val known = buildJsonObject {
            put("id", profile.id)
            put("name", profile.name)
            put("notes", profile.notes)
            put("createdAt", profile.createdAt)
            put("updatedAt", profile.updatedAt)
            put("lastUsedAt", profile.lastUsedAt?.let { JsonPrimitive(it) } ?: JsonNull)
            put("wifi", buildJsonObject { put("ssid", profile.ssid) })
            put("ipRule", encodeIpRule(profile.ipRule))
            put("mask", profile.mask)
            put("gw", profile.gw)
            put("dns", profile.dns)
            put("dns2", profile.dns2)
            put("server", profile.server)
            put("serverPort", profile.serverPort)
            put("localPort", profile.localPort)
            put("rfidDst", profile.rfidDst)
            put("rfidPort", profile.rfidPort)
            put("mainBaud", profile.mainBaud)
            put("rfidBaud", profile.rfidBaud)
            put("hbMs", profile.hbMs)
            if (includeSecrets) put("blePassword", profile.blePassword)
        }
        val merged = LinkedHashMap<String, JsonElement>()
        profile.extras.forEach { (k, v) -> if (k !in profileKeys) merged[k] = v }
        merged.putAll(known)
        return JsonObject(merged)
    }

    fun decodeProfile(obj: JsonObject): Profile {
        val wifi = obj["wifi"]?.let { it as? JsonObject } ?: throw IllegalArgumentException("wifi 가 없습니다.")
        return Profile(
            id = obj.string("id") ?: throw IllegalArgumentException("id 가 없습니다."),
            name = obj.string("name") ?: throw IllegalArgumentException("name 이 없습니다."),
            notes = obj.string("notes").orEmpty(),
            createdAt = obj.string("createdAt").orEmpty(),
            updatedAt = obj.string("updatedAt").orEmpty(),
            lastUsedAt = obj.string("lastUsedAt"),
            ssid = wifi.string("ssid") ?: throw IllegalArgumentException("wifi.ssid 가 없습니다."),
            ipRule = decodeIpRule(obj["ipRule"]?.let { it as? JsonObject }
                ?: throw IllegalArgumentException("ipRule 이 없습니다.")),
            mask = obj.string("mask") ?: throw IllegalArgumentException("mask 가 없습니다."),
            gw = obj.string("gw") ?: throw IllegalArgumentException("gw 가 없습니다."),
            dns = obj.string("dns") ?: "auto",
            dns2 = obj.string("dns2") ?: "auto",
            server = obj.string("server") ?: throw IllegalArgumentException("server 가 없습니다."),
            serverPort = obj.int("serverPort") ?: 50000,
            localPort = obj.int("localPort") ?: 50000,
            rfidDst = obj.string("rfidDst") ?: "255.255.255.255",
            rfidPort = obj.int("rfidPort") ?: 50001,
            mainBaud = obj.int("mainBaud") ?: 115200,
            rfidBaud = obj.int("rfidBaud") ?: 9600,
            hbMs = obj.int("hbMs") ?: 1000,
            blePassword = obj.string("blePassword").orEmpty(),
            extras = obj.unknown(profileKeys),
        )
    }

    private fun encodeIpRule(rule: IpRule): JsonObject = when (rule) {
        is IpRule.Offset -> buildJsonObject {
            put("mode", "offset")
            put("baseNetwork", rule.baseNetwork)
            put("offset", rule.offset)
        }
        IpRule.Dhcp -> buildJsonObject { put("mode", "dhcp") }
        IpRule.Manual -> buildJsonObject { put("mode", "manual") }
    }

    /** 알 수 없는 mode 는 직접 입력(manual)으로 읽는다 — 앞으로 생길 규칙을 망가뜨리지 않으려고. */
    private fun decodeIpRule(obj: JsonObject): IpRule = when (obj.string("mode")) {
        "offset" -> IpRule.Offset(
            baseNetwork = obj.string("baseNetwork") ?: throw IllegalArgumentException("baseNetwork 가 없습니다."),
            offset = obj.int("offset") ?: throw IllegalArgumentException("offset 이 없습니다."),
        )
        "dhcp" -> IpRule.Dhcp
        else -> IpRule.Manual
    }

    internal fun parseObject(text: String): JsonObject =
        try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: SerializationException) {
            throw IllegalArgumentException("JSON 형식이 아닙니다.", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("JSON 형식이 아닙니다.", e)
        }

    internal fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    internal fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    internal fun JsonObject.unknown(knownKeys: Set<String>): JsonObject =
        JsonObject(filterKeys { it !in knownKeys })
}
