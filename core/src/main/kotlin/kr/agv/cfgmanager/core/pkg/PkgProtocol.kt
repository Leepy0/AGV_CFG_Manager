package kr.agv.cfgmanager.core.pkg

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.util.Base64
import java.util.zip.CRC32

enum class PkgState { Staged, Applied, Old }

data class PkgSlot(
    val index: Int,
    val state: PkgState,
    val id: String,
    val ver: String,
    val size: Long,
    val seq: Long,
    val sha256: String,
)

sealed interface PkgList {
    data class Ok(val slots: List<PkgSlot>) : PkgList

    /** 펌웨어가 `dsp pkg json` 을 모른다 (0.9.1 미만), 또는 해석할 수 없는 응답. */
    data object Unsupported : PkgList
    data object NoPartition : PkgList
    data object Busy : PkgList
}

sealed interface PkgChunk {
    class Data(val id: String, val off: Long, val bytes: ByteArray) : PkgChunk
    data class Refused(val code: String) : PkgChunk
    data object Bad : PkgChunk
}

/** `dsp pkg json` 응답. `{` 로 시작하지 않거나 깨졌으면 [PkgList.Unsupported]. */
fun parsePkgList(text: String): PkgList {
    val body = text.trim()
    if (!body.startsWith("{")) return PkgList.Unsupported
    val root = try {
        Json.parseToJsonElement(body).jsonObject
    } catch (_: Exception) {
        return PkgList.Unsupported
    }
    (root["err"] as? JsonPrimitive)?.let {
        return when (it.contentOrNull) {
            "nopart" -> PkgList.NoPartition
            "busy" -> PkgList.Busy
            else -> PkgList.Unsupported
        }
    }
    val arr = try {
        root["slots"]?.jsonArray
    } catch (_: Exception) {
        null
    } ?: return PkgList.Unsupported
    return PkgList.Ok(arr.mapNotNull { (it as? JsonObject)?.let(::slotOrNull) })
}

private fun slotOrNull(o: JsonObject): PkgSlot? {
    fun str(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    fun num(k: String) = (o[k] as? JsonPrimitive)?.longOrNull
    val state = when (str("st")) {
        "STAGED" -> PkgState.Staged
        "APPLIED" -> PkgState.Applied
        "OLD" -> PkgState.Old
        else -> return null
    }
    return PkgSlot(
        index = (o["i"] as? JsonPrimitive)?.intOrNull ?: return null,
        state = state,
        id = str("id") ?: return null,
        ver = str("ver") ?: return null,
        size = num("size") ?: return null,
        seq = num("seq") ?: return null,
        sha256 = str("sha256") ?: return null,
    )
}

/** APPLIED 슬롯, 없으면 seq 가 가장 큰 STAGED. */
fun defaultPkgSlot(slots: List<PkgSlot>): PkgSlot? =
    slots.firstOrNull { it.state == PkgState.Applied }
        ?: slots.filter { it.state == PkgState.Staged }.maxByOrNull { it.seq }

/** `dsp pkg read` 응답. 다른 줄이 섞여도 `P ` 줄(없으면 `E ` 줄)을 찾는다. */
fun parsePkgChunk(text: String): PkgChunk {
    val lines = text.lineSequence().map { it.trim() }
    lines.firstOrNull { it.startsWith("P ") }?.let { return parseP(it) }
    lines.firstOrNull { it.startsWith("E ") }?.let { return PkgChunk.Refused(it.substring(2).trim()) }
    return PkgChunk.Bad
}

private fun parseP(line: String): PkgChunk {
    val t = line.split(' ')
    if (t.size != 6) return PkgChunk.Bad
    val off = t[2].toLongOrNull() ?: return PkgChunk.Bad
    val n = t[3].toIntOrNull() ?: return PkgChunk.Bad
    val crc = t[4].toLongOrNull(16) ?: return PkgChunk.Bad
    val bytes = try {
        Base64.getDecoder().decode(t[5])
    } catch (_: IllegalArgumentException) {
        return PkgChunk.Bad
    }
    if (bytes.size != n || CRC32().apply { update(bytes) }.value != crc) return PkgChunk.Bad
    return PkgChunk.Data(t[1], off, bytes)
}
