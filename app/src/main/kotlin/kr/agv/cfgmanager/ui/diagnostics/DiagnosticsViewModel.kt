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
import kr.agv.cfgmanager.apply.ApplyUi
import kr.agv.cfgmanager.apply.ResetRun
import kr.agv.cfgmanager.apply.ResetTarget
import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.BridgeCommands
import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.core.util.utf8Length
import kr.agv.cfgmanager.core.workflows.FactoryResetOutcome
import kr.agv.cfgmanager.net.GatewayProbe
import kr.agv.cfgmanager.net.GatewayUrl
import kr.agv.cfgmanager.net.GatewayResult
import kr.agv.cfgmanager.session.LinkStatus

/** 진단 탭의 입력 · 자동 새로고침 · 게이트웨이 · 초기화 상태와 문구. */
data class DiagnosticsUi(
    val input: String = "",
    val inputError: String? = null,
    val autoRefresh: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val gateway: GatewayResult? = null,
    val gatewayBusy: Boolean = false,
    val resetNotice: String? = null,
    val resetBusy: Boolean = false,
)

/** 명령 실패를 사람이 읽는 문구로. 예외 원문은 쓰지 않는다. */
fun commandErrorText(failure: Throwable): String = when (failure) {
    is BridgeChannelException.Timeout -> "응답이 없습니다. 보드가 멀거나 설정 모드가 닫혔을 수 있습니다."
    is BridgeChannelException.Disconnected -> "연결이 끊겼습니다. 보드 탭에서 다시 연결하세요."
    is BridgeChannelException.NotConnected -> "연결된 장치가 없습니다. 보드 탭에서 연결하세요."
    else -> "명령을 보내지 못했습니다. 다시 시도하세요."
}

/** 빠른 명령 칩 (F-D3). 눌러서 바로 보낸다. */
val QUICK_COMMANDS: List<String> = listOf("stat", "cfg", "setup off", "dsp", "dsp ver", "help")

/** 초기화 결과 문구 (F-A5). 설정을 지웠는지와 재부팅 상태를 함께 말한다. */
fun resetNoticeText(outcome: FactoryResetOutcome): String = when (outcome) {
    FactoryResetOutcome.Rebooting ->
        "저장된 설정을 지우고 재부팅을 요청했습니다. 보드가 다시 시작하면 자동으로 다시 연결합니다."
    is FactoryResetOutcome.RebootRefused ->
        "설정은 지웠지만 메인보드 굽기 중이라 재부팅이 거부됐습니다. 굽기가 끝난 뒤 콘솔에서 reboot 를 보내세요."
    is FactoryResetOutcome.RebootUnknown ->
        "설정은 지웠지만 재부팅 응답을 확인하지 못했습니다 (${outcome.reason}). 보드 탭에서 다시 찾아 보세요."
    is FactoryResetOutcome.EraseFailed ->
        "설정을 지우지 못했습니다 (${outcome.reason}). 보드 설정은 그대로입니다."
}

class DiagnosticsViewModel(
    private val container: AppContainer,
    private val probe: GatewayProbe = GatewayProbe(),
) : ViewModel() {

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

    /** 빠른 명령 칩: 입력줄을 거치지 않고 바로 보낸다. */
    fun runQuick(command: String) {
        viewModelScope.launch {
            session.sendConsole(command)
                .onFailure { failure -> _ui.update { it.copy(error = commandErrorText(failure)) } }
        }
    }

    /** 최근 명령을 입력줄로 불러온다 (보내지는 않는다). */
    fun recall(command: String) = setInput(command)

    /** 장치의 server 주소로 게이트웨이 응답을 본다 (F-D4). 장치를 읽은 뒤에만 쓸 수 있다. */
    fun checkGateway() {
        if (_ui.value.gatewayBusy) return
        val server = session.state.value.snapshot?.config?.canonical(ConfigKey.SERVER)
        if (server == null) {
            _ui.update { it.copy(gateway = GatewayResult.InvalidServer) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(gatewayBusy = true) }
            val result = probe.check(server)
            _ui.update { it.copy(gateway = result, gatewayBusy = false) }
        }
    }

    /** 게이트웨이 화면 주소. 서버 주소를 모르면 null. */
    fun gatewayUrl(): String? {
        val server = session.state.value.snapshot?.config?.canonical(ConfigKey.SERVER) ?: return null
        return GatewayUrl.root(server)
    }

    /** 공장 초기화 대상. 읽은 장치 값을 그대로 쓴다. */
    fun resetTarget(): ResetTarget? {
        val state = session.state.value
        val snapshot = state.snapshot ?: return null
        return ResetTarget(
            agv = snapshot.config?.agv,
            bleAddress = state.address,
            mac = snapshot.mac,
            deviceName = snapshot.name,
            fw = snapshot.fw,
        )
    }

    /** 공장 초기화를 실행한다. 적용 중이면 막는다. 결과 문구는 [DiagnosticsUi.resetNotice] 에 남는다. */
    fun factoryReset(target: ResetTarget) {
        if (_ui.value.resetBusy) return
        if (container.apply.state.value is ApplyUi.Running) {
            _ui.update { it.copy(resetNotice = "적용 중에는 초기화할 수 없습니다. 끝난 뒤 다시 시도하세요.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(resetBusy = true, resetNotice = null, error = null) }
            val run: ResetRun = container.factoryReset.run(target)
            val notice = buildString {
                append(resetNoticeText(run.outcome))
                if (run.historyFailed) append(" (이력 파일에 쓰지 못했습니다)")
            }
            _ui.update { it.copy(resetBusy = false, resetNotice = notice) }
        }
    }

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
