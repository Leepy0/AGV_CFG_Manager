package kr.agv.cfgmanager.core.history

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.SerializationException

/**
 * 이력 JSON Lines 와 CSV. 한 줄 = 한 건, 추가 전용. 깨진 줄은 건너뛴다.
 * 모르는 필드는 [HistoryEntry.extras] 에 보관해 되돌려 쓴다.
 */
object HistoryCodec {

    private val json = Json

    private val knownKeys = setOf(
        "at", "profileId", "profileName", "agv", "bleAddress", "mac", "deviceName", "ip", "ssid", "fw",
        "channel", "action", "result", "rssi", "note",
    )

    private val csvColumns = listOf(
        "at", "profileName", "agv", "deviceName", "ip", "ssid", "fw", "mac",
        "channel", "action", "result", "rssi", "note",
    )

    /** 한 건을 한 줄 JSON 으로 (줄바꿈 없음). */
    fun encodeLine(entry: HistoryEntry): String {
        val known = buildJsonObject {
            put("at", entry.at)
            put("profileId", entry.profileId.nullable())
            put("profileName", entry.profileName.nullable())
            put("agv", entry.agv.nullableInt())
            put("bleAddress", entry.bleAddress.nullable())
            put("mac", entry.mac.nullable())
            put("deviceName", entry.deviceName.nullable())
            put("ip", entry.ip.nullable())
            put("ssid", entry.ssid.nullable())
            put("fw", entry.fw.nullable())
            put("channel", entry.channel)
            put("action", entry.action)
            put("result", entry.result)
            put("rssi", entry.rssi.nullableInt())
            put("note", entry.note)
        }
        val merged = LinkedHashMap<String, JsonElement>()
        entry.extras.forEach { (k, v) -> if (k !in knownKeys) merged[k] = v }
        merged.putAll(known)
        return json.encodeToString(JsonElement.serializer(), JsonObject(merged))
    }

    /** 한 줄을 읽는다. 빈 줄이거나 형식이 틀리면 null. */
    fun decodeLine(line: String): HistoryEntry? {
        if (line.isBlank()) return null
        val obj = try {
            json.parseToJsonElement(line).jsonObject
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        val at = obj.string("at") ?: return null
        val channel = obj.string("channel") ?: return null
        val action = obj.string("action") ?: return null
        val result = obj.string("result") ?: return null
        return HistoryEntry(
            at = at,
            profileId = obj.string("profileId"),
            profileName = obj.string("profileName"),
            agv = obj.int("agv"),
            bleAddress = obj.string("bleAddress"),
            mac = obj.string("mac"),
            deviceName = obj.string("deviceName"),
            ip = obj.string("ip"),
            ssid = obj.string("ssid"),
            fw = obj.string("fw"),
            channel = channel,
            action = action,
            result = result,
            rssi = obj.int("rssi"),
            note = obj.string("note").orEmpty(),
            extras = JsonObject(obj.filterKeys { it !in knownKeys }),
        )
    }

    /** 여러 줄 전체를 디코드. 깨진 줄은 무시. */
    fun decodeLines(text: String): List<HistoryEntry> = text.lineSequence().mapNotNull { decodeLine(it) }.toList()

    /** 전체를 줄 단위 jsonl 로 (끝 줄바꿈 포함). */
    fun encodeLines(entries: List<HistoryEntry>): String =
        entries.joinToString(separator = "") { encodeLine(it) + "\n" }

    /**
     * CSV (헤더 포함, CRLF). [withBom] = true 면 UTF-8 BOM 을 붙여 엑셀에서 한글이 깨지지 않게 한다.
     */
    fun encodeCsv(entries: List<HistoryEntry>, withBom: Boolean = true): String {
        val rows = mutableListOf(csvColumns.joinToString(","))
        for (e in entries) {
            val cells = listOf(
                e.at, e.profileName.orEmpty(), e.agv?.toString().orEmpty(), e.deviceName.orEmpty(),
                e.ip.orEmpty(), e.ssid.orEmpty(), e.fw.orEmpty(), e.mac.orEmpty(), e.channel, e.action,
                e.result, e.rssi?.toString().orEmpty(), e.note,
            )
            rows += cells.joinToString(",") { csvCell(it) }
        }
        val body = rows.joinToString("\r\n") + "\r\n"
        return if (withBom) "﻿$body" else body
    }

    /** 쉼표 · 따옴표 · 줄바꿈이 있으면 따옴표로 감싸고 안의 따옴표를 두 번 쓴다. */
    internal fun csvCell(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    private fun String?.nullable(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull

    private fun Int?.nullableInt(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull

    private fun JsonObject.string(key: String): String? {
        val element = this[key] as? JsonPrimitive ?: return null
        return if (element.isString) element.content else null
    }

    private fun JsonObject.int(key: String): Int? {
        val element = this[key] as? JsonPrimitive ?: return null
        return if (element.isString) null else element.intOrNull
    }
}
