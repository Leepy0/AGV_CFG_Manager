package kr.agv.cfgmanager

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kr.agv.cfgmanager.apply.ApplyRunner
import kr.agv.cfgmanager.ble.BleBoardSource
import kr.agv.cfgmanager.ble.BlePreflightProbe
import kr.agv.cfgmanager.ble.BleScanner
import kr.agv.cfgmanager.data.HistoryStore
import kr.agv.cfgmanager.data.SettingsStore
import kr.agv.cfgmanager.session.BoardSource
import kr.agv.cfgmanager.session.BridgeSession
import kr.agv.cfgmanager.session.FakeBoardSource
import kr.agv.cfgmanager.session.FakeRoster
import kr.agv.cfgmanager.session.SwitchingBoardSource
import java.io.File

/**
 * 수동 DI (docs/DEVELOPMENT_PLAN.md 6.1). 앱 프로세스당 하나이며 [AgvApp] 이 쥔다.
 * 연결 세션 · 적용 실행 · 이력 저장소가 Activity 밖에 있으므로 화면 회전에도 유지된다.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** 앱 수명 범위 코루틴. 세션의 감시 · 재연결 · 유휴 확인과 적용 실행이 여기서 돈다. */
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

    /** 작업 이력 (`filesDir/history.jsonl`, 추가만). */
    val history = HistoryStore(File(appContext.filesDir, HISTORY_FILE))

    /** 적용 실행기. 진행 상태와 결과를 쥐고 있어 회전 뒤에도 진행 패널이 복원된다. */
    val apply = ApplyRunner(session, history, appScope, isFake = { boards.isFake })

    /** 현재 연결 방식이 가짜인지 (화면 문구 · 권한 검사 건너뛰기). */
    fun isFakeMode(): Boolean = boards.isFake

    private companion object {
        const val HISTORY_FILE = "history.jsonl"
    }
}
