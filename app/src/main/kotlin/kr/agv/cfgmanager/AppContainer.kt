package kr.agv.cfgmanager

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kr.agv.cfgmanager.ble.BleBoardSource
import kr.agv.cfgmanager.ble.BlePreflightProbe
import kr.agv.cfgmanager.ble.BleScanner
import kr.agv.cfgmanager.data.SettingsStore
import kr.agv.cfgmanager.session.BoardSource
import kr.agv.cfgmanager.session.BridgeSession
import kr.agv.cfgmanager.session.FakeBoardSource
import kr.agv.cfgmanager.session.FakeRoster
import kr.agv.cfgmanager.session.SwitchingBoardSource

/**
 * 수동 DI (docs/DEVELOPMENT_PLAN.md 6.1). 앱 프로세스당 하나이며 [AgvApp] 이 쥔다.
 * 연결 세션이 Activity 밖에 있으므로 화면 회전에도 연결이 유지된다.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** 앱 수명 범위 코루틴. 세션의 감시 · 재연결 · 유휴 확인이 여기서 돈다. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsStore(appContext)

    private val adapter: BluetoothAdapter? = appContext
        .getSystemService(BluetoothManager::class.java)
        ?.adapter

    val preflight = BlePreflightProbe(appContext, adapter)

    private val scanner = BleScanner(adapter)

    /** 디버그 빌드에서 "가짜 브리지" 를 켜면 가짜 원천을 쓴다. 릴리스 빌드에서는 항상 BLE. */
    val boards: BoardSource = SwitchingBoardSource(
        ble = BleBoardSource(appContext, adapter, scanner),
        fake = FakeBoardSource(FakeRoster.create()) { System.currentTimeMillis() },
        useFake = { BuildConfig.DEBUG && settings.fakeBridge.value },
    )

    val session = BridgeSession(boards, appScope)

    /** 현재 연결 방식이 가짜인지 (화면 문구 · 권한 검사 건너뛰기). */
    fun isFakeMode(): Boolean = boards.isFake
}
