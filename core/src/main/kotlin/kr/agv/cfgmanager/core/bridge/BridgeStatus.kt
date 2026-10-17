package kr.agv.cfgmanager.core.bridge

/** `stat` 응답이 온 경로. 콘솔은 fw 줄이 없고 main · rfid · heap 줄이 있다. */
enum class StatSource { Ble, Console }

/** `wifi:` 줄. */
data class WifiStatus(
    val up: Boolean,
    val mac: String?,
    val ip: String?,
    val rssi: Int?,
    val channel: Int?,
    val bssid: String?,
)

/** `net :` 줄 카운터. `lastReason` 은 마지막 Wi-Fi 끊김 사유 코드(9.3). */
data class NetStatus(
    val tx: Long,
    val txErr: Long,
    val txOffline: Long,
    val rx: Long,
    val rxBad: Long,
    val rxForeign: Long,
    val disconnects: Long,
    val lastReason: Int,
)

enum class SetupMode { On, Off, Opening, Unavailable }

/** `setup:` 줄. */
data class SetupStatus(
    val mode: SetupMode,
    val name: String?,
    val ble: String?,
    val apClients: Int?,
    val closing: Boolean,
)

/** `dsp :` 줄. 구 파티션 보드는 [state] 가 `nopart` 이다. */
data class DspStatus(
    val app: String,
    val boot: String,
    val build: String,
    val state: String,
    val staged: String?,
    val stagedJob: Long,
    val pan: String,
) {
    val isNoPartition: Boolean get() = state == "nopart"
}

/** `stat` 응답 전체. 줄이 없으면 해당 필드는 null. */
data class BridgeStatus(
    val source: StatSource,
    val fw: String?,
    val part: String?,
    val ota: String?,
    val cfgState: CfgState?,
    val wifi: WifiStatus?,
    val net: NetStatus?,
    val setup: SetupStatus?,
    val dsp: DspStatus?,
    val heapBytes: Long?,
    val uart: Map<String, Map<String, Long>> = emptyMap(),
)
