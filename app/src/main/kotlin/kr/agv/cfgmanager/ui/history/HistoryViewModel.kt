package kr.agv.cfgmanager.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.core.history.HistoryCodec
import kr.agv.cfgmanager.core.history.HistoryEntry
import kr.agv.cfgmanager.core.history.HistoryView
import kr.agv.cfgmanager.prefill.PrefillRequest
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 기간 필터 (5.5). [days] 가 null 이면 기간 제한 없음. */
enum class HistoryPeriod(val label: String, val days: Long?) {
    All("전체", null),
    Day("24시간", 1),
    Week("7일", 7),
    Month("30일", 30),
}

data class HistoryUi(
    val loaded: Boolean = false,
    val all: List<HistoryEntry> = emptyList(),
    val query: String = "",
    val period: HistoryPeriod = HistoryPeriod.All,
    val selected: HistoryEntry? = null,
    val failed: Boolean = false,
) {
    /** 검색 · 기간을 적용한 목록 (최신 순). */
    val visible: List<HistoryEntry>
        get() = HistoryView.filter(all, query, period.days?.let { Instant.now().minus(it, ChronoUnit.DAYS).toString() })
}

/** 이력 탭 상태. 목록은 이력 파일을 한 번 읽어 메모리에 둔다. */
class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    private val history = container.history
    private val prefill = container.prefill
    private val uiFlow = MutableStateFlow(HistoryUi())
    val ui: StateFlow<HistoryUi> = uiFlow.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) { history.readAll() }
                uiFlow.update { it.copy(loaded = true, all = entries, failed = false) }
            } catch (failure: IOException) {
                uiFlow.update { it.copy(loaded = true, failed = true) }
            }
        }
    }

    fun setQuery(text: String) = uiFlow.update { it.copy(query = text) }

    fun setPeriod(period: HistoryPeriod) = uiFlow.update { it.copy(period = period) }

    fun select(entry: HistoryEntry?) = uiFlow.update { it.copy(selected = entry) }

    /** [이 값으로 폼 채우기]: 설정 탭 폼에 이 건의 호기 · SSID · IP · 프로필을 넘긴다. */
    fun fillForm(entry: HistoryEntry) {
        prefill.offer(PrefillRequest(agv = entry.agv, ssid = entry.ssid, ip = entry.ip, profileId = entry.profileId))
    }

    /** CSV 내용 (보이는 건만). 엑셀에서 한글이 깨지지 않게 BOM 을 붙인다. */
    fun csvOfVisible(): String = HistoryCodec.encodeCsv(uiFlow.value.visible)
}
