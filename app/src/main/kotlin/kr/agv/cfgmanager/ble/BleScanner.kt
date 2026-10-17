package kr.agv.cfgmanager.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kr.agv.cfgmanager.session.BoardRow
import kotlin.time.Duration

/** 블루투스가 꺼져 있거나 스캐너를 얻지 못했다. */
class ScanUnavailableException : Exception("블루투스 스캐너를 사용할 수 없습니다")

/**
 * BLE 스캐너 (docs/DEVELOPMENT_PLAN.md 8.2).
 * - 보드 목록: NUS 서비스 UUID 필터 + 이름 `AGVBR-` 접두어. 10초 뒤 자동 중지.
 * - 주소 찾기: 재부팅 뒤 같은 주소를 기다릴 때 쓴다.
 * - 스캔 시작은 6초 간격 이상 (Android 의 30초 5회 제한).
 */
@SuppressLint("MissingPermission")
class BleScanner(
    private val adapter: BluetoothAdapter?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val gate = Mutex()
    private var lastStartMs = 0L

    /** [duration] 동안 스캔하고 약 2초마다 목록을 보낸다. 시간이 지나면 흐름이 닫힌다. */
    fun discover(duration: Duration): Flow<List<BoardRow>> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner ?: throw ScanUnavailableException()
        val table = ScanTable()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                record(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { record(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                this@callbackFlow.close(IllegalStateException("스캔 실패 코드 $errorCode"))
            }

            private fun record(result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name ?: return
                table.record(result.device.address, name, result.rssi, clock())
            }
        }
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(NusUuid.SERVICE))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        throttle()
        scanner.startScan(listOf(filter), settings, callback)

        val ticker = launch {
            while (isActive) {
                trySend(table.snapshot(clock()))
                delay(TICK_MS)
            }
        }
        launch {
            delay(duration)
            close()
        }
        awaitClose {
            ticker.cancel()
            runCatching { scanner.stopScan(callback) }
        }
    }

    /** 주소의 광고가 [timeout] 안에 보이면 true. 못 보면 false (연결은 호출한 쪽이 다시 시도한다). */
    suspend fun awaitAddress(address: String, timeout: Duration): Boolean {
        val scanner = adapter?.bluetoothLeScanner ?: return false
        throttle()
        val seen = CompletableDeferred<Boolean>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.device.address.equals(address, ignoreCase = true)) seen.complete(true)
            }

            override fun onScanFailed(errorCode: Int) {
                seen.complete(false)
            }
        }
        val filter = ScanFilter.Builder().setDeviceAddress(address).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), settings, callback)
        return try {
            withTimeoutOrNull(timeout) { seen.await() } ?: false
        } finally {
            runCatching { scanner.stopScan(callback) }
        }
    }

    private suspend fun throttle() {
        gate.withLock {
            val wait = lastStartMs + SCAN_GAP_MS - clock()
            if (wait > 0) delay(wait)
            lastStartMs = clock()
        }
    }

    private companion object {
        const val TICK_MS = 2_000L
        const val SCAN_GAP_MS = 6_000L
    }
}
