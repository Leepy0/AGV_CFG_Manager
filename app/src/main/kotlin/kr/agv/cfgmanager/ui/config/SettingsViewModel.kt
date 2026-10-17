package kr.agv.cfgmanager.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.apply.ApplyUi
import kr.agv.cfgmanager.core.bridge.BridgeConfig
import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.core.workflows.ApplyOutcome
import kr.agv.cfgmanager.session.LinkStatus
import kr.agv.cfgmanager.session.SessionState
import kr.agv.cfgmanager.session.BridgeSession
import kr.agv.cfgmanager.apply.ApplyRunner
import kr.agv.cfgmanager.session.RebootOutcome
import kr.agv.cfgmanager.core.workflows.ApplyResult

/** 접히는 그룹. Wi-Fi · 네트워크 · 서버는 항상 펼쳐 두고, DNS 와 고급 항목만 접는다 (5.3). */
enum class FormGroup { Dns, Advanced }

/** 설정 탭이 그릴 상태. */
data class SettingsUi(
    val draft: ConfigDraft?,
    val device: BridgeConfig?,
    val dnsSupported: Boolean,
    val linked: Boolean,
    val linkNote: String?,
    val changed: Set<ConfigKey>,
    val check: FormCheck?,
    val canApply: Boolean,
    val expanded: Set<FormGroup>,
    val showPassword: Boolean,
    val saveOnly: Boolean,
    val apply: ApplyUi,
    val notice: String?,
    val link: LinkStatus,
    val deviceName: String?,
    val fw: String?,
)

/**
 * 설정 탭의 폼 상태와 동작. 폼 초안은 이 ViewModel 이 쥐고, 적용 진행은 [ApplyRunner] 가 쥔다.
 * 장치 값이 새로 읽히면(다른 보드 연결, 적용 성공 뒤) 초안을 장치 값으로 맞춘다.
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val session: BridgeSession = container.session
    private val apply: ApplyRunner = container.apply

    private data class Editing(
        val draft: ConfigDraft? = null,
        val sourceAddress: String? = null,
        val expanded: Set<FormGroup> = emptySet(),
        val showPassword: Boolean = false,
        val saveOnly: Boolean = false,
        val notice: String? = null,
    )

    private val editing = MutableStateFlow(Editing())

    val ui: StateFlow<SettingsUi> = combine(editing, session.state, apply.state) { e, s, a ->
        build(e, s, a)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        build(editing.value, session.state.value, apply.state.value),
    )

    init {
        viewModelScope.launch {
            session.state.collect { state ->
                val config = state.snapshot?.config ?: return@collect
                val e = editing.value
                if (e.draft == null || e.sourceAddress != state.address) {
                    resetFrom(state.address, config)
                }
            }
        }
        viewModelScope.launch {
            apply.state.collect { a ->
                if (a is ApplyUi.Done && isSuccess(a.result)) {
                    session.state.value.snapshot?.config?.let { resetFrom(session.state.value.address, it) }
                }
            }
        }
    }

    fun setValue(key: ConfigKey, text: String) {
        editing.update { e -> e.copy(draft = e.draft?.with(key, text), notice = null) }
    }

    /** 호기 번호를 1~99 안에서 바꾼다. Phase 4 의 IP 재계산 · 이력 제안은 이 함수에 붙인다. */
    fun setAgv(agv: Int) {
        setValue(ConfigKey.AGV, agv.coerceIn(AGV_MIN, AGV_MAX).toString())
    }

    fun stepAgv(delta: Int) {
        val current = editing.value.draft?.raw(ConfigKey.AGV)?.toIntOrNull() ?: return
        setAgv(current + delta)
    }

    fun setIpMode(mode: IpMode) {
        editing.update { e -> e.copy(draft = e.draft?.withIpMode(mode), notice = null) }
    }

    /** 프로필 값을 초안에 채운다 (Phase 4 자동 채움 자리). 호기 번호는 건드리지 않는다. */
    fun applyProfileValues(values: Map<ConfigKey, String>) {
        editing.update { e -> e.copy(draft = e.draft?.withProfileValues(values)) }
    }

    fun toggleGroup(group: FormGroup) {
        editing.update { e ->
            e.copy(expanded = if (group in e.expanded) e.expanded - group else e.expanded + group)
        }
    }

    fun togglePassword() {
        editing.update { it.copy(showPassword = !it.showPassword) }
    }

    fun setSaveOnly(saveOnly: Boolean) {
        editing.update { it.copy(saveOnly = saveOnly) }
    }

    /** 초안을 장치 값으로 되돌린다 (F-E8). */
    fun resetToDevice() {
        val s = session.state.value
        val config = s.snapshot?.config ?: return
        resetFrom(s.address, config)
    }

    fun startApply() {
        val draft = editing.value.draft ?: return
        val config = ConfigForm.check(draft).config ?: return
        apply.start(config, editing.value.saveOnly)
    }

    fun cancelApply() = apply.cancel()

    fun retryApply() {
        apply.retry()
    }

    fun dismissResult() = apply.dismiss()

    /** 설정 모드 닫기 (`setup off`). 성공하면 결과 카드를 닫는다. */
    fun closeSetupMode() {
        viewModelScope.launch {
            session.closeSetupMode()
                .onSuccess { apply.dismiss() }
                .onFailure { failure -> setNotice(failure.message ?: "설정 모드를 닫지 못했습니다.") }
        }
    }

    /** 재부팅 요청 (결과 카드의 [재부팅]). 거부되면 이유를 카드에 남긴다. */
    fun reboot() {
        viewModelScope.launch {
            when (val outcome = session.reboot()) {
                is RebootOutcome.Rebooting -> apply.dismiss()
                is RebootOutcome.Refused -> setNotice(outcome.text)
                is RebootOutcome.Failed -> setNotice(outcome.failure.message ?: "재부팅 요청을 보내지 못했습니다.")
            }
        }
    }

    private fun setNotice(text: String) {
        editing.update { it.copy(notice = text) }
    }

    private fun resetFrom(address: String?, config: BridgeConfig) {
        editing.update { e ->
            e.copy(draft = ConfigForm.fromDevice(config), sourceAddress = address, notice = null)
        }
    }

    private fun build(e: Editing, s: SessionState, a: ApplyUi): SettingsUi {
        val device = s.snapshot?.config
        val dnsSupported = s.snapshot?.capabilities?.dnsKeys == true
        val linked = s.link == LinkStatus.Connected && s.snapshot != null
        val draft = e.draft
        val check = draft?.let { ConfigForm.check(it) }
        val changed = if (draft != null && device != null) {
            ConfigForm.changedKeys(draft, device, dnsSupported)
        } else {
            emptySet()
        }
        val running = a is ApplyUi.Running
        return SettingsUi(
            draft = draft,
            device = device,
            dnsSupported = dnsSupported,
            linked = linked,
            linkNote = linkNote(s.link, draft != null),
            changed = changed,
            check = check,
            canApply = linked && !running && check?.config != null && changed.isNotEmpty(),
            expanded = e.expanded,
            showPassword = e.showPassword,
            saveOnly = e.saveOnly,
            apply = a,
            notice = e.notice,
            link = s.link,
            deviceName = s.name,
            fw = s.snapshot?.fw ?: s.fw,
        )
    }

    private fun linkNote(link: LinkStatus, hasDraft: Boolean): String? = when {
        link == LinkStatus.Connected -> null
        link == LinkStatus.Rebooting || link == LinkStatus.Reconnecting ->
            "보드를 다시 찾는 중입니다. 연결되면 자동으로 이어집니다."
        link == LinkStatus.Connecting -> "보드에 연결하는 중입니다."
        hasDraft -> "연결이 끊겼습니다. 보드 탭에서 다시 연결하세요."
        else -> "보드 탭에서 연결하세요."
    }

    private fun isSuccess(result: ApplyResult): Boolean =
        result.outcome == ApplyOutcome.Applied || result.outcome == ApplyOutcome.SavedOnly

    companion object {
        const val AGV_MIN = 1
        const val AGV_MAX = 99
    }
}
