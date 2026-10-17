package kr.agv.cfgmanager.usb

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kr.agv.cfgmanager.session.BoardRow
import kr.agv.cfgmanager.session.BoardSource
import kr.agv.cfgmanager.session.ConnectStage
import kr.agv.cfgmanager.session.USB_ADDRESS_PREFIX
import kotlin.time.Duration

/**
 * OTG 로 꽂힌 시리얼 콘솔을 보드 목록에 올린다 (docs/DEVELOPMENT_PLAN.md 8.6).
 * 권한은 연결할 때 요청한다. 목록에서는 장치를 열지 않는다.
 */
class UsbBoardSource(
    private val context: Context,
    private val usb: UsbManager?,
    private val prober: UsbSerialProber = UsbSerialProber.getDefaultProber(),
) : BoardSource {
    override val isFake: Boolean = false

    override fun discover(duration: Duration): Flow<List<BoardRow>> = flow {
        val manager = usb
        val endAt = System.currentTimeMillis() + duration.inWholeMilliseconds
        while (true) {
            emit(if (manager == null) emptyList() else consoles(manager))
            if (System.currentTimeMillis() >= endAt) break
            delay(POLL_MS)
        }
    }

    override fun channel(
        address: String,
        name: String?,
        password: String,
        onStage: (ConnectStage) -> Unit,
    ): IBridgeChannel {
        val manager = usb ?: throw IllegalStateException("이 휴대폰은 USB 호스트 기능을 쓸 수 없습니다")
        return UsbSerialBridgeChannel(context, manager, prober, address, onStage)
    }

    private fun consoles(manager: UsbManager): List<BoardRow> {
        val now = System.currentTimeMillis()
        return prober.findAllDrivers(manager).map { driver ->
            val device = driver.device
            BoardRow(
                address = USB_ADDRESS_PREFIX + device.deviceName,
                name = "USB 콘솔 · ${device.productName ?: "시리얼"}",
                rssi = 0,
                lastSeenMs = now,
            )
        }
    }

    private companion object {
        const val POLL_MS = 2_000L
    }
}
