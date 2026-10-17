package kr.agv.cfgmanager.core.history

import kotlinx.serialization.json.JsonObject

/**
 * 작업 이력 한 건 (부록 B-3, `history.jsonl` 한 줄).
 * [result]: `ok` · `wifi_down:<reason>` · `invalid:<key>` · `save_failed` · `reboot_refused` ·
 * `reconnect_timeout` · `cancelled` · `disconnected` (그 밖에 `no_change` · `timeout` · `readback_mismatch` 등).
 */
data class HistoryEntry(
    val at: String,
    val profileId: String?,
    val profileName: String?,
    val agv: Int?,
    val bleAddress: String?,
    val mac: String?,
    val deviceName: String?,
    val ip: String?,
    val ssid: String?,
    val fw: String?,
    val channel: String,
    val action: String,
    val result: String,
    val rssi: Int?,
    val note: String,
    val extras: JsonObject = JsonObject(emptyMap()),
)
