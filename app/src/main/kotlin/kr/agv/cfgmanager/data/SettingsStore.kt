package kr.agv.cfgmanager.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱 설정 (SharedPreferences, docs/DEVELOPMENT_PLAN.md F-G1). Phase 2 에서는 "가짜 브리지" 하나만 쓴다.
 * 읽기 · 쓰기 실패는 기본값으로 대신한다.
 */
class SettingsStore(context: Context) {
    private val prefs = try {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    } catch (failure: RuntimeException) {
        null
    }

    private val fakeBridgeFlow = MutableStateFlow(readBoolean(KEY_FAKE_BRIDGE))

    /** 디버그 빌드의 개발자 옵션. 릴리스 빌드에서는 호출 측이 무시한다. */
    val fakeBridge: StateFlow<Boolean> = fakeBridgeFlow.asStateFlow()

    fun setFakeBridge(enabled: Boolean) {
        try {
            prefs?.edit()?.putBoolean(KEY_FAKE_BRIDGE, enabled)?.apply()
        } catch (failure: RuntimeException) {
            // 저장하지 못해도 이번 실행에서는 값이 적용된다
        }
        fakeBridgeFlow.value = enabled
    }

    private fun readBoolean(key: String): Boolean = try {
        prefs?.getBoolean(key, false) ?: false
    } catch (failure: RuntimeException) {
        false
    }

    private companion object {
        const val FILE_NAME = "agv_settings"
        const val KEY_FAKE_BRIDGE = "fake_bridge"
    }
}
