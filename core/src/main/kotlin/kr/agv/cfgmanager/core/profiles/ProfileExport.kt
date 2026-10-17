package kr.agv.cfgmanager.core.profiles

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull

/**
 * SSID 라이브러리 한 항목 (평문). 저장소에서는 Keystore 로 암호화하고, 여기서는 내보내기 형식만 다룬다.
 */
data class WifiNetwork(
    val ssid: String,
    val password: String?,
    val open: Boolean,
    val lastUsedAt: String?,
    val useCount: Int,
)

data class ExportBundle(
    val profiles: List<Profile>,
    val networks: List<WifiNetwork>,
)

data class ImportedBundle(
    val bundle: ExportBundle,
    val containsPasswords: Boolean,
)

/**
 * 프로필 · SSID 라이브러리 내보내기 파일(`*.agvcfg.json`) (docs/DEVELOPMENT_PLAN.md 부록 B-2).
 * 비밀번호 포함 여부를 파일 머리의 `containsPasswords` 로 표시한다.
 */
object ProfileExport {

    private const val FORMAT_VERSION = 1
    private val json = Json { prettyPrint = true }
    private val compactJson = Json

    /**
     * [includePasswords] = false 면 BLE 비밀번호 · Wi-Fi 비밀번호를 모두 뺀다.
     * [pretty] = false 면 한 줄로 쓴다 (QR 용, 부록 B-2).
     */
    fun export(bundle: ExportBundle, includePasswords: Boolean, pretty: Boolean = true): String {
        val root = buildJsonObject {
            put("version", FORMAT_VERSION)
            put("containsPasswords", includePasswords)
            put(
                "profiles",
                JsonArray(bundle.profiles.map { ProfileCodec.encodeProfile(it, includeSecrets = includePasswords) }),
            )
            put(
                "networks",
                JsonArray(bundle.networks.map { encodeNetwork(it, includePasswords) }),
            )
        }
        return (if (pretty) json else compactJson).encodeToString(JsonElement.serializer(), root)
    }

    /** 파일 머리의 `containsPasswords` 가 true 인지. 내용은 읽지 않는다. */
    fun containsPasswords(text: String): Boolean {
        val root = ProfileCodec.parseObject(text)
        return (root["containsPasswords"] as? JsonPrimitive)?.booleanOrNull == true
    }

    fun import(text: String): ImportedBundle {
        val root = ProfileCodec.parseObject(text)
        val profiles = (root["profiles"] as? JsonArray)
            ?.map { ProfileCodec.decodeProfile(it.jsonObject) }
            .orEmpty()
        val networks = (root["networks"] as? JsonArray)
            ?.map { decodeNetwork(it.jsonObject) }
            .orEmpty()
        return ImportedBundle(
            bundle = ExportBundle(profiles, networks),
            containsPasswords = (root["containsPasswords"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }

    private fun encodeNetwork(network: WifiNetwork, includePasswords: Boolean): JsonObject = buildJsonObject {
        put("ssid", network.ssid)
        if (includePasswords && network.password != null) put("password", network.password)
        put("open", network.open)
        put("lastUsedAt", network.lastUsedAt?.let { JsonPrimitive(it) } ?: JsonNull)
        put("useCount", network.useCount)
    }

    private fun decodeNetwork(obj: JsonObject): WifiNetwork = WifiNetwork(
        ssid = (obj["ssid"] as? JsonPrimitive)?.contentOrNull
            ?: throw IllegalArgumentException("ssid 가 없습니다."),
        password = (obj["password"] as? JsonPrimitive)?.contentOrNull,
        open = (obj["open"] as? JsonPrimitive)?.booleanOrNull == true,
        lastUsedAt = (obj["lastUsedAt"] as? JsonPrimitive)?.contentOrNull,
        useCount = (obj["useCount"] as? JsonPrimitive)?.intOrNull ?: 0,
    )
}
