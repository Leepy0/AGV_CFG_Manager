package kr.agv.cfgmanager.prefill

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 이력 상세 등에서 설정 탭 폼을 미리 채울 요청. 값이 null 인 항목은 건드리지 않는다. */
data class PrefillRequest(
    val agv: Int?,
    val ssid: String?,
    val ip: String?,
    val profileId: String?,
)

/** 화면 사이에서 폼 채우기 요청을 한 번 전달한다. 설정 탭이 받아 쓰고 [consume] 으로 지운다. */
class FormPrefill {
    private val requestFlow = MutableStateFlow<PrefillRequest?>(null)
    val request: StateFlow<PrefillRequest?> = requestFlow.asStateFlow()

    fun offer(value: PrefillRequest) {
        requestFlow.value = value
    }

    fun consume() {
        requestFlow.value = null
    }
}
