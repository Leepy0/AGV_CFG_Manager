package kr.agv.cfgmanager.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱 설정 저장소 (docs/DEVELOPMENT_PLAN.md F-G1). 값은 메모리에 두고 바뀔 때마다 [StringStore] 에 쓴다.
 * 저장 실패는 이번 실행에서 적용되는 것으로 두고 무시한다.
 */
class SettingsStore(private val store: StringStore) {

    private val flow = MutableStateFlow(
        try {
            AppSettingsCodec.read(store)
        } catch (failure: RuntimeException) {
            AppSettings()
        },
    )

    val settings: StateFlow<AppSettings> = flow.asStateFlow()

    val current: AppSettings get() = flow.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(flow.value)
        flow.value = next
        try {
            AppSettingsCodec.write(store, next)
        } catch (failure: RuntimeException) {
            // 저장하지 못해도 이번 실행에서는 값이 적용된다
        }
    }
}

/** SharedPreferences 기반 [StringStore]. 읽기 · 쓰기 오류는 기본값으로 대신한다. */
class PrefsStringStore(context: Context) : StringStore {
    private val prefs = try {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    } catch (failure: RuntimeException) {
        null
    }

    override fun get(key: String): String? = try {
        prefs?.getString(key, null)
    } catch (failure: RuntimeException) {
        // 예전 형식(boolean 으로 저장된 키)이면 여기로 온다. 기본값을 쓴다.
        null
    }

    override fun put(key: String, value: String) {
        try {
            prefs?.edit()?.putString(key, value)?.apply()
        } catch (failure: RuntimeException) {
            // 저장 실패는 무시한다
        }
    }

    private companion object {
        const val FILE_NAME = "agv_settings"
    }
}
