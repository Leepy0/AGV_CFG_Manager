package kr.agv.cfgmanager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kr.agv.cfgmanager.core.bridge.BridgeConfig
import java.io.File

/**
 * 적용 직전 장치 값. [password] 는 장치에서 읽을 수 없어(길이만 나온다) 앱이 아는 값일 때만 있다.
 * 모르면 null 이고, 복원할 때 비밀번호는 보내지 않는다 (장치에 있는 값이 남는다).
 */
data class RestorePoint(
    val key: String,
    val at: String,
    val deviceName: String?,
    val config: BridgeConfig,
    val password: String?,
)

/**
 * 호기별 "적용 직전 값" (`filesDir/restore_points.json`, 장치당 마지막 1건, 최대 [MAX_POINTS] 대).
 * 사용자 결정으로 비밀번호까지 평문으로 저장한다. 앱 전용 저장소 밖으로 내보내거나 로그에 쓰지 않는다.
 */
class RestorePointStore(private val file: File) {
    private val mutex = Mutex()

    suspend fun save(point: RestorePoint) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val kept = readAll().filterNot { it.key == point.key } + point
            writeAll(kept.takeLast(MAX_POINTS))
        }
    }

    suspend fun find(key: String): RestorePoint? = mutex.withLock {
        withContext(Dispatchers.IO) { readAll().firstOrNull { it.key == key } }
    }

    private fun readAll(): List<RestorePoint> {
        if (!file.exists()) return emptyList()
        return try {
            Json.parseToJsonElement(file.readText()).jsonArray.mapNotNull { it.jsonObject.toPoint() }
        } catch (failure: Exception) {
            emptyList()
        }
    }

    private fun writeAll(points: List<RestorePoint>) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(JsonArray(points.map { it.toJson() }).toString())
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private fun RestorePoint.toJson(): JsonObject = buildJsonObject {
        put("key", key)
        put("at", at)
        put("deviceName", deviceName)
        put("password", password)
        put("agv", config.agv)
        put("ssid", config.ssid)
        put("passLength", config.passLength)
        put("ip", config.ip)
        put("mask", config.mask)
        put("gw", config.gw)
        put("dns", config.dns)
        put("dns2", config.dns2)
        put("server", config.server)
        put("serverPort", config.serverPort)
        put("localPort", config.localPort)
        put("rfidDst", config.rfidDst)
        put("rfidPort", config.rfidPort)
        put("mainBaud", config.mainBaud)
        put("rfidBaud", config.rfidBaud)
        put("hbMs", config.hbMs)
    }

    private fun JsonObject.toPoint(): RestorePoint? {
        fun text(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
        fun number(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull
        return RestorePoint(
            key = text("key") ?: return null,
            at = text("at") ?: return null,
            deviceName = text("deviceName"),
            password = text("password"),
            config = BridgeConfig(
                agv = number("agv") ?: return null,
                ssid = text("ssid") ?: return null,
                pass = null,
                passLength = number("passLength"),
                ip = text("ip"),
                mask = text("mask") ?: return null,
                gw = text("gw") ?: return null,
                dns = text("dns"),
                dns2 = text("dns2"),
                server = text("server") ?: return null,
                serverPort = number("serverPort") ?: return null,
                localPort = number("localPort") ?: return null,
                rfidDst = text("rfidDst"),
                rfidPort = number("rfidPort") ?: return null,
                mainBaud = number("mainBaud") ?: return null,
                rfidBaud = number("rfidBaud") ?: return null,
                hbMs = number("hbMs") ?: return null,
            ),
        )
    }

    private companion object {
        const val MAX_POINTS = 50
    }
}
