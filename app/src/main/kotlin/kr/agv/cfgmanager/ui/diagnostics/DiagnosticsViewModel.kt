package kr.agv.cfgmanager.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.BridgeCommands
import kr.agv.cfgmanager.core.util.utf8Length
import kr.agv.cfgmanager.session.LinkStatus

/** 진단 탭의 입력 · 자동 새로고침 · 오류 문구. */
data class DiagnosticsUi(
    val input: String = "",
    val inputError: String? = null,
    val autoRefresh: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

/** 명령 실패를 사람이 읽는 문구로. 예외 원문은 쓰지 않는다. */
fun commandErrorText(failure: Throwable): String = when (failure) {
    is BridgeChannelException.Timeout -> "응답이 없습니다. 보드가 멀거나 설정 모드가 닫혔을 수 있습니다."
    is BridgeChannelException.Disconnected -> "연결이 끊겼습니다. 보드 탭에서 다시 연결하세요."
    is BridgeChannelException.NotConnected -> "연결된 장치가 없습니다. 보드 탭에서 연결하세요."
    else -> "명령을 보내지 못했습니다. 다시 시도하세요."
}

class DiagnosticsViewModel(private val container: AppContainer) : ViewModel() {

    private val session = container.session
    private val _ui = MutableStateFlow(DiagnosticsUi())
    val ui: StateFlow<DiagnosticsUi> = _ui.asStateFlow()

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_MS)
                if (_ui.value.autoRefresh && session.state.value.link == LinkStatus.Connected) {
                    refresh(auto = true)
                }
            }
        }
    }

    fun setAutoRefresh(enabled: Boolean) {
        _ui.update { it.copy(autoRefresh = enabled) }
    }

    fun setInput(text: String) {
        _ui.update { it.copy(input = text, inputError = validate(text)) }
    }

    /** `stat` 을 다시 읽어 상태 카드를 갱신한다. 진행 중이면 무시한다. */
    fun refresh(auto: Boolean = false) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            session.refreshStatus(auto = auto).onFailure { failure ->
                _ui.update { it.copy(error = commandErrorText(failure)) }
            }
            _ui.update { it.copy(busy = false) }
        }
    }

    /** 입력 줄을 보낸다. 성공하면 입력을 비운다. */
    fun send() {
        val line = _ui.value.input.trim()
        if (validate(line) != null || line.isEmpty()) return
        viewModelScope.launch {
            val result = session.sendConsole(line)
            if (result.isSuccess) {
                _ui.update { it.copy(input = "", inputError = null, error = null) }
            } else {
                _ui.update { it.copy(error = commandErrorText(result.exceptionOrNull() ?: IllegalStateException())) }
            }
        }
    }

    /** 최근 명령을 입력줄로 불러온다 (보내지는 않는다). */
    fun recall(command: String) = setInput(command)

    private fun validate(text: String): String? {
        val trimmed = text.trim()
        return if (trimmed.utf8Length() > BridgeCommands.BLE_LINE_MAX_BYTES) {
            "명령이 너무 깁니다 (한 줄 ${BridgeCommands.BLE_LINE_MAX_BYTES}바이트까지)"
        } else {
            null
        }
    }

    private companion object {
        const val AUTO_REFRESH_MS = 5_000L
    }
}
