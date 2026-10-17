package kr.agv.cfgmanager.session

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kr.agv.cfgmanager.core.fake.FakeBridgeChannel
import kr.agv.cfgmanager.core.fake.FakeDevice
import kr.agv.cfgmanager.core.fake.FakeDeviceOptions
import kotlin.time.Duration

/** 가짜 보드 한 대: 스캔 주소 · 신호 · 가짜 장치. */
data class FakeBoardEntry(val address: String, val device: FakeDevice, val rssi: Int)

/** 디버그 "가짜 브리지" 모드의 보드 3대: 설정 없음(01) 2대, 설정됨(12) 1대. */
object FakeRoster {
    fun create(): List<FakeBoardEntry> {
        val fresh = FakeDevice(FakeDeviceOptions(mac = "a1:b2:c3:d4:a1:b2"))
        val second = FakeDevice(FakeDeviceOptions(mac = "c3:d4:e5:f6:c3:d4"))
        val configured = FakeDevice(FakeDeviceOptions(mac = "e5:f6:a7:b8:e5:f6"))
        seedConfigured(configured, agv = 12)
        return listOf(
            FakeBoardEntry("00:AB:CD:00:00:01", fresh, rssi = -48),
            FakeBoardEntry("00:AB:CD:00:00:02", second, rssi = -63),
            FakeBoardEntry("00:AB:CD:00:00:03", configured, rssi = -71),
        )
    }

    /** 저장 설정이 있는 보드를 만든다: 로그인 → set agv → save → 재부팅(이름이 새 호기로 바뀜). */
    private fun seedConfigured(device: FakeDevice, agv: Int) {
        device.handle("login ${device.blePassword}")
        device.handle("set agv $agv")
        device.handle("save")
        device.reboot()
        device.onDisconnected()
    }
}

/** 가짜 원천. 스캔 목록과 채널을 [entries] 에서 만든다. */
class FakeBoardSource(private val entries: List<FakeBoardEntry>, private val clock: () -> Long) : BoardSource {
    override val isFake: Boolean = true

    override fun discover(duration: Duration): Flow<List<BoardRow>> = flow {
        val ticks = (duration.inWholeMilliseconds / TICK_MS).toInt().coerceAtLeast(1)
        for (tick in 0 until ticks) {
            val now = clock()
            val jitter = JITTER[tick % JITTER.size]
            emit(
                entries
                    .map { BoardRow(it.address, it.device.name, it.rssi + jitter, now) }
                    .sortedByDescending { it.rssi },
            )
            delay(TICK_MS)
        }
    }

    override fun channel(
        address: String,
        name: String?,
        password: String,
        onStage: (ConnectStage) -> Unit,
    ): IBridgeChannel {
        val entry = entries.firstOrNull { it.address.equals(address, ignoreCase = true) }
            ?: throw IllegalArgumentException("가짜 보드 목록에 없는 주소입니다")
        return FakeLinkChannel(FakeBridgeChannel(entry.device, loginPassword = password), entry.device, password, onStage)
    }

    private companion object {
        const val TICK_MS = 2_000L
        val JITTER = listOf(0, -2, 1, -1)
    }
}

/** [FakeBridgeChannel] 에 실패 사유 보고를 붙인다 (BLE 채널과 같은 방식으로 화면에 보인다). */
private class FakeLinkChannel(
    private val inner: FakeBridgeChannel,
    private val device: FakeDevice,
    private val password: String,
    private val onStage: (ConnectStage) -> Unit,
) : IBridgeChannel by inner, LinkReport {

    private var failureValue: ConnectFailure? = null

    override val failure: ConnectFailure?
        get() = failureValue

    override suspend fun waitReady(timeout: Duration): Boolean {
        failureValue = null
        onStage(ConnectStage.Connect)
        val ready = inner.waitReady(timeout)
        if (!ready) {
            failureValue = if (password != device.blePassword) {
                ConnectFailure.WrongPassword(null)
            } else {
                ConnectFailure.Timeout
            }
        }
        return ready
    }
}
