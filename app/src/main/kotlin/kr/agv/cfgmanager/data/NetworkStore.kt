package kr.agv.cfgmanager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

/** SSID 라이브러리 한 줄 (화면용). 비밀번호 자체는 들어 있지 않다. */
data class WifiEntry(
    val ssid: String,
    val hasPassword: Boolean,
    val open: Boolean,
    val lastUsedAt: String?,
    val useCount: Int,
)

/**
 * SSID ↔ 비밀번호 라이브러리 (`filesDir/networks.json`, 부록 B-2).
 * 비밀번호는 [SecretCipher] 로 암호화해 `passwordEnc` {iv, data} 로만 쓴다. 평문은 파일에 남지 않는다.
 * 복호화에 실패하면(키가 사라짐) 저장된 비밀번호가 없는 것으로 본다.
 */
class NetworkStore(
    private val file: File,
    private val cipher: SecretCipher,
    private val nowIso: () -> String = { Instant.now().truncatedTo(ChronoUnit.SECONDS).toString() },
) {
    private class Record(
        val ssid: String,
        var sealed: SealedBytes?,
        var open: Boolean,
        var lastUsedAt: String?,
        var useCount: Int,
        val extras: JsonObject,
    )

    private val mutex = Mutex()
    private var records: MutableList<Record>? = null
    private val entriesFlow = MutableStateFlow<List<WifiEntry>>(emptyList())

    /** 목록 (비밀번호 제외). 최근 사용 순. */
    val entries: StateFlow<List<WifiEntry>> = entriesFlow.asStateFlow()

    suspend fun load() {
        mutex.withLock { ensureLoadedLocked() }
    }

    /**
     * SSID 의 비밀번호. 저장된 비밀번호면 그 값, 오픈 AP 면 빈 문자열, 모르면 null.
     * 복호화 실패도 null 이다.
     */
    suspend fun password(ssid: String): String? = mutex.withLock {
        val record = ensureLoadedLocked().firstOrNull { it.ssid == ssid } ?: return@withLock null
        when {
            record.open -> ""
            record.sealed == null -> null
            else -> try {
                cipher.open(record.sealed!!).toString(Charsets.UTF_8)
            } catch (failure: Exception) {
                null
            }
        }
    }

    /**
     * 적용 성공 뒤 기록한다. [password] null 은 "비밀번호는 그대로" (장치에 보내지 않은 경우), 빈 문자열은 오픈 AP.
     * 사용 시각과 사용 횟수는 항상 올린다.
     */
    suspend fun remember(ssid: String, password: String?, at: String = nowIso()) = mutex.withLock {
        val list = ensureLoadedLocked()
        val existing = list.firstOrNull { it.ssid == ssid }
        val record = existing ?: Record(ssid, null, false, null, 0, JsonObject(emptyMap())).also { list += it }
        if (password != null) {
            record.open = password.isEmpty()
            record.sealed = if (password.isEmpty()) null else cipher.seal(password.toByteArray(Charsets.UTF_8))
        }
        record.lastUsedAt = at
        record.useCount += 1
        saveLocked()
    }

    suspend fun delete(ssid: String) = mutex.withLock {
        ensureLoadedLocked().removeAll { it.ssid == ssid }
        saveLocked()
    }

    private suspend fun ensureLoadedLocked(): MutableList<Record> {
        records?.let { return it }
        val loaded = withContext(Dispatchers.IO) { readFile() }
        records = loaded
        entriesFlow.value = loaded.toEntries()
        return loaded
    }

    private suspend fun saveLocked() {
        val list = records ?: return
        withContext(Dispatchers.IO) { writeAtomic(file, encode(list)) }
        entriesFlow.value = list.toEntries()
    }

    private fun List<Record>.toEntries(): List<WifiEntry> = sortedByDescending { it.lastUsedAt.orEmpty() }.map {
        WifiEntry(
            ssid = it.ssid,
            hasPassword = it.sealed != null,
            open = it.open,
            lastUsedAt = it.lastUsedAt,
            useCount = it.useCount,
        )
    }

    private fun readFile(): MutableList<Record> {
        if (!file.exists()) return mutableListOf()
        return try {
            val root = Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
            (root["networks"] as? JsonArray).orEmpty().map { decodeRecord(it.jsonObject) }.toMutableList()
        } catch (broken: Exception) {
            // 깨진 파일은 덮어쓰지 않도록 옆으로 옮긴다.
            file.renameTo(File(file.parentFile, "networks.corrupt-${System.currentTimeMillis()}.json"))
            mutableListOf()
        }
    }

    private fun decodeRecord(obj: JsonObject): Record {
        val ssid = obj["ssid"]?.jsonPrimitive?.contentOrNull ?: ""
        val enc = obj["passwordEnc"] as? JsonObject
        val sealed = enc?.let {
            SealedBytes(
                iv = Base64.getDecoder().decode(it["iv"]!!.jsonPrimitive.content),
                data = Base64.getDecoder().decode(it["data"]!!.jsonPrimitive.content),
            )
        }
        val known = setOf("ssid", "passwordEnc", "open", "lastUsedAt", "useCount")
        return Record(
            ssid = ssid,
            sealed = sealed,
            open = (obj["open"] as? JsonPrimitive)?.booleanOrNull == true,
            lastUsedAt = (obj["lastUsedAt"] as? JsonPrimitive)?.contentOrNull,
            useCount = (obj["useCount"] as? JsonPrimitive)?.intOrNull ?: 0,
            extras = JsonObject(obj.filterKeys { it !in known }),
        )
    }

    private fun encode(list: List<Record>): String {
        val root = buildJsonObject {
            put("version", VERSION)
            put(
                "networks",
                JsonArray(
                    list.map { record ->
                        val merged = LinkedHashMap<String, JsonElement>()
                        record.extras.forEach { (k, v) -> merged[k] = v }
                        merged["ssid"] = JsonPrimitive(record.ssid)
                        merged["passwordEnc"] = record.sealed?.let { sealed ->
                            buildJsonObject {
                                put("iv", Base64.getEncoder().encodeToString(sealed.iv))
                                put("data", Base64.getEncoder().encodeToString(sealed.data))
                            }
                        } ?: JsonNull
                        merged["open"] = JsonPrimitive(record.open)
                        merged["lastUsedAt"] = record.lastUsedAt?.let { JsonPrimitive(it) } ?: JsonNull
                        merged["useCount"] = JsonPrimitive(record.useCount)
                        JsonObject(merged)
                    },
                ),
            )
        }
        return PRETTY.encodeToString(JsonElement.serializer(), root)
    }

    private companion object {
        const val VERSION = 1
        val PRETTY = Json { prettyPrint = true }
    }
}
