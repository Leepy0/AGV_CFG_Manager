package kr.agv.cfgmanager.ble

import android.bluetooth.BluetoothAdapter
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kr.agv.cfgmanager.session.BoardRow
import kr.agv.cfgmanager.session.BoardSource
import kr.agv.cfgmanager.session.ConnectStage
import kotlin.time.Duration

/** BLE 으로 실제 보드를 찾고 연결하는 원천. */
class BleBoardSource(
    private val context: Context,
    private val adapter: BluetoothAdapter?,
    private val scanner: BleScanner,
) : BoardSource {
    override val isFake: Boolean = false

    override fun discover(duration: Duration): Flow<List<BoardRow>> = scanner.discover(duration)

    override fun channel(
        address: String,
        name: String?,
        password: String,
        onStage: (ConnectStage) -> Unit,
    ): IBridgeChannel {
        val bt = adapter ?: throw IllegalStateException("이 휴대폰은 블루투스를 사용할 수 없습니다")
        return BleBridgeChannel(context, bt, scanner, address, password, name, onStage)
    }
}
