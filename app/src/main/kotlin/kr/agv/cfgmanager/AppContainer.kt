package kr.agv.cfgmanager

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kr.agv.cfgmanager.apply.ApplyRunner
import kr.agv.cfgmanager.apply.FactoryResetRunner
import kr.agv.cfgmanager.batch.BatchController
import kr.agv.cfgmanager.data.KeystoreCipher
import kr.agv.cfgmanager.data.NetworkStore
import kr.agv.cfgmanager.data.ProfileStore
import kr.agv.cfgmanager.data.RestorePointStore
import kr.agv.cfgmanager.prefill.FormPrefill
import kr.agv.cfgmanager.ble.BleBoardSource
import kr.agv.cfgmanager.ble.BlePreflightProbe
import kr.agv.cfgmanager.ble.BleScanner
import kr.agv.cfgmanager.core.update.BuildInfo
import kr.agv.cfgmanager.core.update.UpdateChecker
import kr.agv.cfgmanager.core.update.UpdateController
import kr.agv.cfgmanager.core.update.UpdateDownloader
import kr.agv.cfgmanager.core.update.UpdateState
import kr.agv.cfgmanager.data.HistoryStore
import kr.agv.cfgmanager.data.PrefsStringStore
import kr.agv.cfgmanager.data.SettingsStore
import kr.agv.cfgmanager.log.AppLog
import kr.agv.cfgmanager.session.BoardSource
import kr.agv.cfgmanager.session.BridgeSession
import kr.agv.cfgmanager.session.FakeBoardSource
import kr.agv.cfgmanager.session.FakeRoster
import kr.agv.cfgmanager.session.SwitchingBoardSource
import kr.agv.cfgmanager.update.HttpUrlUpdateHttp
import kr.agv.cfgmanager.update.UpdateActions
import kr.agv.cfgmanager.usb.UsbBoardSource
import java.io.File

/**
 * 수동 DI (docs/DEVELOPMENT_PLAN.md 6.1). 앱 프로세스당 하나이며 [AgvApp] 이 쥔다.
 * 연결 세션 · 적용 실행 · 이력 저장소가 Activity 밖에 있으므로 화면 회전에도 유지된다.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** 앱 수명 범위 코루틴. 세션의 감시 · 재연결 · 유휴 확인과 적용 실행이 여기서 돈다. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsStore(PrefsStringStore(appContext))

    /** 앱 로그 (`filesDir/logs/app-YYYYMMDD.log`, 7일 보관). */
    val log = AppLog(File(appContext.filesDir, LOG_DIR))

    /** 앱 내 업데이트 상태 (정보 화면 · 시작 때 자동 확인). APK 는 `cache/updates/` 에 받는다. */
    val updates = UpdateController(
        checker = UpdateChecker(HttpUrlUpdateHttp),
        downloader = UpdateDownloader(HttpUrlUpdateHttp, File(appContext.cacheDir, UPDATE_DIR)),
        build = BuildInfo(
            versionCode = BuildConfig.VERSION_CODE,
            channel = BuildConfig.CHANNEL,
            deviceSdk = Build.VERSION.SDK_INT,
        ),
        journal = { log.info("업데이트", it) },
    )

    /** 정보 화면이 부르는 확인 · 받기 · 취소 (앱 수명 scope 에서 돈다). */
    val updateActions = UpdateActions(updates, appScope)

    private val updateNoticeFlow = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 자동 확인이 새 버전을 찾았을 때 한 줄 알림. 화면이 스낵바로 보인다. */
    val updateNotices: SharedFlow<String> = updateNoticeFlow.asSharedFlow()

    private val adapter: BluetoothAdapter? = appContext
        .getSystemService(BluetoothManager::class.java)
        ?.adapter

    val preflight = BlePreflightProbe(appContext, adapter)

    private val scanner = BleScanner(adapter)

    /** OTG 시리얼 콘솔. 블루투스와 상관없이 목록을 찾고 연결한다 (docs/DECISIONS.md, Phase 7). */
    val usb = UsbBoardSource(appContext, appContext.getSystemService(UsbManager::class.java))

    /** 디버그 빌드에서 "가짜 브리지" 를 켜면 가짜 원천을 쓴다. 릴리스 빌드에서는 BLE 원천이고, USB 행은 [usb] 가 따로 낸다. */
    val boards: BoardSource = SwitchingBoardSource(
        ble = BleBoardSource(appContext, adapter, scanner) { settings.current.responseIdleMs },
        fake = FakeBoardSource(FakeRoster.create()) { System.currentTimeMillis() },
        usb = usb,
        useFake = { BuildConfig.DEBUG && settings.current.fakeBridge },
    )

    val session = BridgeSession(boards, appScope, journal = { log.info("세션", it) })

    /** 작업 이력 (`filesDir/history.jsonl`, 추가만). */
    val history = HistoryStore(File(appContext.filesDir, HISTORY_FILE))

    /** 프로필 (`filesDir/profiles.json`). 첫 실행이면 본사 기본 프로필을 만든다 ([init] 에서 읽는다). */
    val profiles = ProfileStore(File(appContext.filesDir, PROFILE_FILE))

    /** SSID 라이브러리 (`filesDir/networks.json`, 비밀번호는 Keystore 로 암호화). */
    val networks = NetworkStore(File(appContext.filesDir, NETWORK_FILE), KeystoreCipher())

    /** 적용 직전 장치 값 (`filesDir/restore_points.json`, 비밀번호 포함 평문. 되돌리기용). */
    val restorePoints = RestorePointStore(File(appContext.filesDir, RESTORE_FILE))

    /**
     * 적용 실행기. 진행 상태와 결과를 쥐고 있어 회전 뒤에도 진행 패널이 복원된다.
     * 성공하면 마지막 프로필 사용 시각을 갱신하고, 저장을 허락한 SSID · 비밀번호를 라이브러리에 남긴다.
     */
    val apply = ApplyRunner(
        restorePoints = restorePoints,
        knownPassword = { ssid -> networks.password(ssid) },
        session = session,
        history = history,
        scope = appScope,
        isFake = { boards.isFake },
        journal = { log.info("적용", it) },
        onSucceeded = { meta, target ->
            meta.profileId?.let { profiles.markUsed(it) }
            if (meta.rememberWifi) networks.remember(target.ssid, target.pass)
        },
    )

    /** 공장 초기화 (F-A5). 세션의 독점 구간에서 defaults → reboot 을 보낸다. */
    val factoryReset = FactoryResetRunner(
        exclusive = { block -> session.withExclusive { channel -> block(channel) } },
        history = history,
        isFake = { boards.isFake },
    )

    /** 연속 세팅 상태 (앱 수명 동안 유지). */
    val batch = BatchController(apply.state, appScope)

    /** 이력 등 다른 화면이 설정 탭 폼을 채울 때 쓰는 요청 전달. */
    val prefill = FormPrefill()

    init {
        appScope.launch {
            profiles.load()
            networks.load()
        }
        // 시작 3초 뒤 1회 자동 확인 (설정 autoUpdateCheck). 실패는 조용히 로그만 남긴다.
        appScope.launch {
            delay(AUTO_CHECK_DELAY_MS)
            if (settings.current.autoUpdateCheck) {
                val state = updates.check(silent = true)
                if (state is UpdateState.Available) {
                    updateNoticeFlow.tryEmit("업데이트 있음: ${state.release.version}")
                }
            }
        }
    }

    /** 현재 연결 방식이 가짜인지 (화면 문구 · 권한 검사 건너뛰기). */
    fun isFakeMode(): Boolean = boards.isFake

    private companion object {
        const val HISTORY_FILE = "history.jsonl"
        const val PROFILE_FILE = "profiles.json"
        const val NETWORK_FILE = "networks.json"
        const val RESTORE_FILE = "restore_points.json"
        const val LOG_DIR = "logs"
        const val UPDATE_DIR = "updates"
        const val AUTO_CHECK_DELAY_MS = 3_000L
    }
}
