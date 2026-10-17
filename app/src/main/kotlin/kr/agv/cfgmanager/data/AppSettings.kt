package kr.agv.cfgmanager.data

/** 테마 선택 (F-G1). [System] 이면 휴대폰 설정을 따른다. */
enum class ThemeMode { System, Light, Dark }

/**
 * 앱 설정 값 (docs/DEVELOPMENT_PLAN.md F-G1, 14장 6). 저장 형식은 [StringStore] 의 문자열 키-값이다.
 * 범위를 벗어나거나 깨진 값은 기본값(또는 범위 안으로 고친 값)으로 읽는다.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** 시작 3초 뒤 업데이트 자동 확인. 끄면 정보 화면의 [지금 확인] 으로만 확인한다. */
    val autoUpdateCheck: Boolean = true,
    /** 보드 찾기 스캔 시간 (초). 5 ~ 30, 5 단위. */
    val scanSeconds: Int = DEFAULT_SCAN_SECONDS,
    /** 설정 탭을 열 때 연속 모드를 기본으로 켤지. */
    val batchByDefault: Boolean = false,
    /** BLE 응답 끝 판정의 유휴 시간 (ms). 150 ~ 800, 50 단위 (14장 6). */
    val responseIdleMs: Int = DEFAULT_IDLE_MS,
    /** 디버그 빌드의 가짜 브리지. 릴리스 빌드에서는 무시한다. */
    val fakeBridge: Boolean = false,
) {
    companion object {
        const val DEFAULT_SCAN_SECONDS = 10
        const val SCAN_MIN_SECONDS = 5
        const val SCAN_MAX_SECONDS = 30
        const val SCAN_STEP_SECONDS = 5

        const val DEFAULT_IDLE_MS = 250
        const val IDLE_MIN_MS = 150
        const val IDLE_MAX_MS = 800
        const val IDLE_STEP_MS = 50
    }
}

/** 문자열 키-값 저장소. Android 에서는 SharedPreferences, 테스트에서는 맵. 읽기 · 쓰기 실패는 호출 측이 흡수한다. */
interface StringStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/** [AppSettings] ↔ 문자열 키-값. 키 이름은 한 번 정하면 바꾸지 않는다 (업데이트 뒤에도 값이 남도록). */
object AppSettingsCodec {
    const val KEY_THEME = "theme_mode"
    const val KEY_AUTO_UPDATE = "auto_update_check"
    const val KEY_SCAN_SECONDS = "scan_seconds"
    const val KEY_BATCH_DEFAULT = "batch_by_default"
    const val KEY_RESPONSE_IDLE = "response_idle_ms"
    const val KEY_FAKE_BRIDGE = "fake_bridge"

    fun read(store: StringStore): AppSettings = AppSettings(
        themeMode = ThemeMode.entries.firstOrNull { it.name == store.get(KEY_THEME) } ?: ThemeMode.System,
        autoUpdateCheck = store.get(KEY_AUTO_UPDATE)?.toBooleanStrictOrNull() ?: true,
        scanSeconds = store.get(KEY_SCAN_SECONDS)?.toIntOrNull()
            ?.coerceIn(AppSettings.SCAN_MIN_SECONDS, AppSettings.SCAN_MAX_SECONDS)
            ?: AppSettings.DEFAULT_SCAN_SECONDS,
        batchByDefault = store.get(KEY_BATCH_DEFAULT)?.toBooleanStrictOrNull() ?: false,
        responseIdleMs = store.get(KEY_RESPONSE_IDLE)?.toIntOrNull()
            ?.coerceIn(AppSettings.IDLE_MIN_MS, AppSettings.IDLE_MAX_MS)
            ?: AppSettings.DEFAULT_IDLE_MS,
        fakeBridge = store.get(KEY_FAKE_BRIDGE)?.toBooleanStrictOrNull() ?: false,
    )

    fun write(store: StringStore, settings: AppSettings) {
        store.put(KEY_THEME, settings.themeMode.name)
        store.put(KEY_AUTO_UPDATE, settings.autoUpdateCheck.toString())
        store.put(KEY_SCAN_SECONDS, settings.scanSeconds.toString())
        store.put(KEY_BATCH_DEFAULT, settings.batchByDefault.toString())
        store.put(KEY_RESPONSE_IDLE, settings.responseIdleMs.toString())
        store.put(KEY_FAKE_BRIDGE, settings.fakeBridge.toString())
    }
}
