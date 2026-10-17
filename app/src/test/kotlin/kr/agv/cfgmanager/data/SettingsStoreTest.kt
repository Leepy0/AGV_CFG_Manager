package kr.agv.cfgmanager.data

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsStoreTest {

    private class MapStore(val map: MutableMap<String, String> = mutableMapOf()) : StringStore {
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) {
            map[key] = value
        }
    }

    private class BrokenStore : StringStore {
        override fun get(key: String): String? = throw RuntimeException("disk gone")
        override fun put(key: String, value: String) = throw RuntimeException("disk gone")
    }

    @Test
    fun `바꾼 설정은 다시 열어도 같은 값으로 읽힌다`() {
        val map = MapStore()
        val first = SettingsStore(map)
        first.update {
            it.copy(
                themeMode = ThemeMode.Dark,
                scanSeconds = 20,
                batchByDefault = true,
                responseIdleMs = 400,
                autoUpdateCheck = false,
                fakeBridge = true,
            )
        }
        val reopened = SettingsStore(map).current
        assertEquals(first.current, reopened)
        assertEquals(ThemeMode.Dark, reopened.themeMode)
        assertEquals(400, reopened.responseIdleMs)
    }

    @Test
    fun `처음에는 기본값이다`() {
        assertEquals(AppSettings(), SettingsStore(MapStore()).current)
    }

    @Test
    fun `범위를 벗어난 값은 범위 안으로 고친다`() {
        val map = MapStore(
            mutableMapOf(
                AppSettingsCodec.KEY_SCAN_SECONDS to "3",
                AppSettingsCodec.KEY_RESPONSE_IDLE to "9999",
            ),
        )
        val settings = SettingsStore(map).current
        assertEquals(AppSettings.SCAN_MIN_SECONDS, settings.scanSeconds)
        assertEquals(AppSettings.IDLE_MAX_MS, settings.responseIdleMs)
    }

    @Test
    fun `깨진 값은 기본값으로 읽는다`() {
        val map = MapStore(
            mutableMapOf(
                AppSettingsCodec.KEY_THEME to "neon",
                AppSettingsCodec.KEY_RESPONSE_IDLE to "fast",
                AppSettingsCodec.KEY_FAKE_BRIDGE to "yes",
            ),
        )
        val settings = SettingsStore(map).current
        assertEquals(ThemeMode.System, settings.themeMode)
        assertEquals(AppSettings.DEFAULT_IDLE_MS, settings.responseIdleMs)
        assertEquals(false, settings.fakeBridge)
    }

    @Test
    fun `저장소가 실패해도 이번 실행에서는 값이 적용된다`() {
        val store = SettingsStore(BrokenStore())
        store.update { it.copy(themeMode = ThemeMode.Light) }
        assertEquals(ThemeMode.Light, store.current.themeMode)
    }
}
