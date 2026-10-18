package kr.agv.cfgmanager.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.apply.ApplyMeta
import kr.agv.cfgmanager.apply.ApplyRunner
import kr.agv.cfgmanager.apply.ApplyUi
import kr.agv.cfgmanager.batch.BatchController
import kr.agv.cfgmanager.batch.BatchUi
import kr.agv.cfgmanager.core.bridge.BridgeConfig
import kr.agv.cfgmanager.core.bridge.CfgState
import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.core.bridge.ValidationContext
import kr.agv.cfgmanager.core.history.AgvHints
import kr.agv.cfgmanager.core.history.HistoryEntry
import kr.agv.cfgmanager.core.profiles.IpRule
import kr.agv.cfgmanager.core.profiles.Profile
import kr.agv.cfgmanager.core.profiles.ProfileDocument
import kr.agv.cfgmanager.core.workflows.ApplyOutcome
import kr.agv.cfgmanager.core.workflows.ApplyResult
import kr.agv.cfgmanager.core.workflows.BatchState
import kr.agv.cfgmanager.core.workflows.ReadyAdvice
import kr.agv.cfgmanager.data.HistoryStore
import kr.agv.cfgmanager.data.NetworkStore
import kr.agv.cfgmanager.data.ProfileStore
import kr.agv.cfgmanager.data.WifiEntry
import kr.agv.cfgmanager.prefill.FormPrefill
import kr.agv.cfgmanager.prefill.PrefillRequest
import kr.agv.cfgmanager.session.BridgeSession
import kr.agv.cfgmanager.session.LinkStatus
import kr.agv.cfgmanager.session.RebootOutcome
import kr.agv.cfgmanager.session.SessionState
import java.io.IOException

/** 접히는 그룹. 세부 값(Wi-Fi 비밀번호 · 네트워크 · 서버)은 기본으로 접고 요약 카드만 보인다. DNS 와 고급 항목은 그 안에서 한 번 더 접는다. */
enum class FormGroup { Details, Dns, Advanced }

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
    val profiles: List<Profile>,
    val selectedProfile: Profile?,
    val ruleAvailable: Boolean,
    val ruleIp: String?,
    val ruleMismatchIp: String?,
    val nextAgvHint: Int?,
    val rememberPassword: Boolean,
    val networkSsids: List<String>,
    val batch: BatchUi,
    val batchAdvice: CfgState?,
    val batchNextAgv: Int?,
)

/**
 * 설정 탭의 폼 상태와 동작. 폼 초안은 이 ViewModel 이 쥐고, 적용 진행은 [ApplyRunner] 가 쥔다.
 * 장치 값이 새로 읽히면 초안을 장치 값으로 맞춘다. 연속 세팅 중이면 연속 상태가 채울 값을 정한다 (7.3).
 * 호기 번호 변경은 [setAgvText], 프로필 선택은 [selectProfile], SSID 입력은 [setSsidText] 가 맡는다.
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val session: BridgeSession = container.session
    private val apply: ApplyRunner = container.apply
    private val profileStore: ProfileStore = container.profiles
    private val networks: NetworkStore = container.networks
    private val history: HistoryStore = container.history
    private val batch: BatchController = container.batch
    private val prefill: FormPrefill = container.prefill

    private data class Editing(
        val draft: ConfigDraft? = null,
        val sourceAddress: String? = null,
        val expanded: Set<FormGroup> = emptySet(),
        val showPassword: Boolean = false,
        val saveOnly: Boolean = false,
        val notice: String? = null,
        val selectedProfileId: String? = null,
        val ipState: IpRuleState = IpRuleState(null),
        val rememberPassword: Boolean = true,
        val batchAdvice: CfgState? = null,
        val batchKey: String? = null,
    )

    private data class Core(
        val editing: Editing,
        val session: SessionState,
        val apply: ApplyUi,
        val batch: BatchUi,
    )

    private val editing = MutableStateFlow(Editing())
    private val historyFlow = MutableStateFlow<List<HistoryEntry>>(emptyList())

    private val core = combine(editing, session.state, apply.state, batch.ui) { e, s, a, b -> Core(e, s, a, b) }

    val ui: StateFlow<SettingsUi> = combine(
        core,
        profileStore.document,
        networks.entries,
        historyFlow,
    ) { c, doc, nets, hist -> build(c, doc, nets, hist) }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        build(
            Core(editing.value, session.state.value, apply.state.value, batch.ui.value),
            profileStore.document.value,
            networks.entries.value,
            emptyList(),
        ),
    )

    init {
        reloadHistory()
        viewModelScope.launch {
            // 보드 목록 단계에서 고른 프로필(마지막 프로필)을 따라간다. 연결 뒤 채우기는 이 프로필로 한다.
            profileStore.document.collect { doc ->
                val last = doc?.profiles?.firstOrNull { it.id == doc.lastProfileId }
                if (doc != null && last?.id != editing.value.selectedProfileId && (last != null || editing.value.selectedProfileId == null)) {
                    editing.update { it.copy(selectedProfileId = last?.id, ipState = IpRuleState(last?.ipRule)) }
                }
            }
        }
        viewModelScope.launch {
            session.state.collect { state ->
                val config = state.snapshot?.config ?: return@collect
                val e = editing.value
                if (e.draft == null || e.sourceAddress != state.address) {
                    resetFrom(state.address, config)
                    // 연속 세팅 밖에서 새 보드를 읽으면 고른 프로필로 채운다. 연속 세팅은 배치 상태가 채운다 (F-B2).
                    if (!batch.ui.value.enabled) selectedProfile()?.let { fillFromProfile(it, config.agv) }
                }
            }
        }
        viewModelScope.launch {
            apply.state.collect { a ->
                if (a is ApplyUi.Done) {
                    reloadHistory()
                    if (isSuccess(a.result)) {
                        session.state.value.snapshot?.config?.let { resetFrom(session.state.value.address, it) }
                    }
                }
            }
        }
        viewModelScope.launch {
            batch.ui.collect { b -> onBatch(b) }
        }
        viewModelScope.launch {
            prefill.request.collect { request ->
                if (request != null) {
                    applyPrefill(request)
                    prefill.consume()
                }
            }
        }
    }

    // ---- 입력 ----

    /** 호기 번호 칸 입력. 1~99 가 되면 규칙 모드 IP 를 다시 계산한다. */
    fun setAgvText(text: String) {
        val digits = text.filter(Char::isDigit).take(2)
        editing.update { e ->
            val draft = e.draft ?: return@update e
            val agv = digits.toIntOrNull()
            val next = if (agv != null && agv in AGV_MIN..AGV_MAX) {
                AutoFill.changeAgv(draft, e.ipState, agv)
            } else {
                draft.copy(values = draft.values + (ConfigKey.AGV to digits))
            }
            e.copy(draft = next, notice = null)
        }
    }

    fun setAgv(agv: Int) {
        setAgvText(agv.coerceIn(AGV_MIN, AGV_MAX).toString())
    }

    fun stepAgv(delta: Int) {
        val current = editing.value.draft?.raw(ConfigKey.AGV)?.toIntOrNull() ?: return
        setAgv(current + delta)
    }

    /** 연속 세팅이 알려 준 다음 호기 번호 (또는 이력 기준 제안)로 바꾼다. */
    fun setAgvHint(agv: Int) = setAgv(agv)

    /** 일반 입력. IP 를 직접 고치면 규칙 재계산을 멈춘다. */
    fun setValue(key: ConfigKey, text: String) {
        if (key == ConfigKey.AGV) return setAgvText(text)
        if (key == ConfigKey.SSID) return setSsidText(text)
        editing.update { e ->
            val overridden = if (key == ConfigKey.IP && text != ConfigDraft.DHCP) {
                e.ipState.copy(overridden = true)
            } else {
                e.ipState
            }
            e.copy(draft = e.draft?.with(key, text), ipState = overridden, notice = null)
        }
    }

    /** SSID 입력. 라이브러리에 같은 SSID 가 있으면 비밀번호 칸을 채운다 (F-E5). */
    fun setSsidText(text: String) {
        editing.update { e -> e.copy(draft = e.draft?.with(ConfigKey.SSID, text), notice = null) }
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { networks.password(text) } ?: return@launch
            editing.update { e ->
                val draft = e.draft
                if (draft != null && draft.raw(ConfigKey.SSID) == text) {
                    e.copy(draft = draft.with(ConfigKey.PASS, AutoFill.wirePassword(stored).orEmpty()))
                } else {
                    e
                }
            }
        }
    }

    fun setIpMode(mode: IpMode) {
        editing.update { e ->
            val draft = e.draft ?: return@update e
            when (mode) {
                IpMode.Rule -> {
                    val (next, state) = AutoFill.useRule(draft, e.ipState, draft.raw(ConfigKey.AGV).toIntOrNull())
                        ?: return@update e
                    e.copy(draft = next, ipState = state, notice = null)
                }
                IpMode.Dhcp -> e.copy(draft = draft.withIpMode(IpMode.Dhcp), notice = null)
                IpMode.Manual -> e.copy(
                    draft = draft.withIpMode(IpMode.Manual),
                    ipState = e.ipState.copy(overridden = true),
                    notice = null,
                )
            }
        }
    }

    /** [규칙값으로]: 직접 고친 IP 를 규칙값으로 되돌린다. */
    fun useRuleIp() = setIpMode(IpMode.Rule)

    fun setRememberPassword(remember: Boolean) {
        editing.update { it.copy(rememberPassword = remember) }
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

    /** 프로필을 고른다. 마지막 프로필로 기억하고, 호기 번호만 남기고 값을 채운다 (F-E4). */
    fun selectProfile(id: String) {
        val profile = profileStore.document.value?.profiles?.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            try {
                profileStore.setLast(id)
            } catch (failure: IOException) {
                // 마지막 프로필 기억만 실패한다. 폼 채우기는 계속한다.
            }
            fillFromProfile(profile, currentAgv())
        }
    }

    /** 연속 세팅을 켜거나 끈다. 켤 때 이미 연결된 보드가 있으면 그 보드를 첫 번째로 본다. */
    fun setBatch(on: Boolean) {
        if (!on) {
            batch.stop()
            return
        }
        batch.start(currentAgv(), selectedProfile()?.name)
        val s = session.state.value
        val cfg = s.snapshot?.cfgState
        val address = s.address
        if (s.link == LinkStatus.Connected && address != null && cfg != null) {
            batch.pick(address)
            batch.readFinished(cfg)
        }
    }

    /** 이미 설정된 보드 경고에서 [덮어쓰기]: 다음 호기와 프로필 값으로 채운다. */
    fun overwriteBatch() {
        val ready = batch.ui.value.state as? BatchState.Ready ?: return
        viewModelScope.launch {
            selectedProfile()?.let { fillFromProfile(it, ready.nextAgv) }
            editing.update { it.copy(batchAdvice = null) }
        }
    }

    /** 초안을 장치 값으로 되돌린다 (F-E8). */
    fun resetToDevice() {
        val s = session.state.value
        val config = s.snapshot?.config ?: return
        resetFrom(s.address, config)
    }

    /** 이력에서 넘어온 값으로 폼을 채운다 (이력 상세의 [이 값으로 폼 채우기]). */
    private suspend fun applyPrefill(request: PrefillRequest) {
        request.profileId?.let { id ->
            profileStore.document.value?.profiles?.firstOrNull { it.id == id }?.let { fillFromProfile(it, request.agv ?: currentAgv()) }
        }
        val ssid = request.ssid
        val stored = ssid?.let { withContext(Dispatchers.IO) { networks.password(it) } }
        editing.update { e ->
            var draft = e.draft ?: ConfigDraft(ConfigKey.entries.associateWith { "" }, IpMode.Manual)
            var ip = e.ipState
            request.agv?.let { draft = draft.copy(values = draft.values + (ConfigKey.AGV to it.toString())) }
            ssid?.let {
                draft = draft.with(ConfigKey.SSID, it)
                AutoFill.wirePassword(stored)?.let { pass -> draft = draft.with(ConfigKey.PASS, pass) }
            }
            request.ip?.let { text ->
                if (text == ConfigDraft.DHCP) {
                    draft = draft.withIpMode(IpMode.Dhcp)
                } else {
                    draft = draft.copy(values = draft.values + (ConfigKey.IP to text), ipMode = IpMode.Manual)
                    ip = ip.copy(overridden = true)
                }
            }
            e.copy(draft = draft, ipState = ip, sourceAddress = e.sourceAddress, notice = null)
        }
    }

    fun cancelApply() = apply.cancel()

    fun retryApply() {
        apply.retry()
    }

    fun dismissResult() = apply.dismiss()

    fun startApply() {
        val config = ui.value.check?.config ?: return
        val e = editing.value
        val profile = selectedProfile()
        apply.start(
            config,
            e.saveOnly,
            ApplyMeta(profileId = profile?.id, profileName = profile?.name, rememberWifi = e.rememberPassword),
        )
    }

    /** 설정 모드 닫기 (`setup off`). 성공하면 결과 카드를 닫는다. */
    fun closeSetupMode() {
        viewModelScope.launch {
            session.closeSetupMode()
                .onSuccess { apply.dismiss() }
                .onFailure { failure -> setNotice(failure.message ?: "설정 모드를 닫지 못했습니다.") }
        }
    }

    /** 적용 직전 값으로 되돌린다 (결과 카드의 [이전 값으로 복원]). 저장한 값이 없으면 이유를 카드에 남긴다. */
    fun restore() {
        viewModelScope.launch {
            if (!apply.restore()) setNotice("복원할 이전 값이 없습니다.")
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

    // ---- 내부 ----

    private fun setNotice(text: String) {
        editing.update { it.copy(notice = text) }
    }

    private fun currentAgv(): Int =
        editing.value.draft?.raw(ConfigKey.AGV)?.toIntOrNull()
            ?: session.state.value.snapshot?.config?.agv
            ?: AGV_MIN

    private fun selectedProfile(): Profile? {
        val id = editing.value.selectedProfileId ?: return null
        return profileStore.document.value?.profiles?.firstOrNull { it.id == id }
    }

    /** 장치 값으로 초안을 만든다. 같은 보드의 IP 규칙 상태(프로필 규칙)는 그대로 둔다. */
    private fun resetFrom(address: String?, config: BridgeConfig) {
        editing.update { e ->
            e.copy(
                draft = ConfigForm.fromDevice(config),
                sourceAddress = address,
                notice = null,
                ipState = IpRuleState(e.ipState.rule, overridden = false),
            )
        }
    }

    /** 프로필 값을 채운다. 라이브러리 비밀번호는 SSID 로 찾는다. 호기 번호는 [agv] 를 쓴다. */
    private suspend fun fillFromProfile(profile: Profile, agv: Int) {
        val library = withContext(Dispatchers.IO) {
            if (profile.ssid.isEmpty()) null else networks.password(profile.ssid)
        }
        editing.update { e ->
            val (draft, state) = AutoFill.applyProfile(e.draft, profile, agv, library)
            e.copy(draft = draft, ipState = state, selectedProfileId = profile.id, notice = null)
        }
    }

    /** 연속 세팅 상태 변화: 준비되면 채우거나 경고한다. 같은 보드 · 같은 호기는 한 번만 채운다. */
    private suspend fun onBatch(b: BatchUi) {
        val ready = b.state as? BatchState.Ready
        if (ready == null) {
            editing.update { it.copy(batchAdvice = null, batchKey = null) }
            return
        }
        val key = "${ready.bleAddress}#${ready.nextAgv}"
        if (editing.value.batchKey == key) return
        editing.update { it.copy(batchKey = key) }
        when (val advice = ready.advice) {
            ReadyAdvice.FillNextAgv -> {
                val profile = selectedProfile()
                if (profile != null) {
                    fillFromProfile(profile, ready.nextAgv)
                } else {
                    editing.update { e -> e.copy(draft = e.draft?.with(ConfigKey.AGV, ready.nextAgv.toString())) }
                }
                editing.update { it.copy(batchAdvice = null) }
            }
            is ReadyAdvice.AlreadyConfigured -> editing.update { it.copy(batchAdvice = advice.cfgState) }
        }
    }

    private fun reloadHistory() {
        viewModelScope.launch {
            try {
                historyFlow.value = withContext(Dispatchers.IO) { history.readAll() }
            } catch (failure: IOException) {
                // 이력을 못 읽어도 호기 힌트 없이 동작한다
            }
        }
    }

    private fun build(c: Core, doc: ProfileDocument?, nets: List<WifiEntry>, hist: List<HistoryEntry>): SettingsUi {
        val e = c.editing
        val s = c.session
        val a = c.apply
        val b = c.batch
        val profiles = doc?.profiles.orEmpty()
        val selected = profiles.firstOrNull { it.id == e.selectedProfileId }
        val device = s.snapshot?.config
        val dnsSupported = s.snapshot?.capabilities?.dnsKeys == true
        val linked = s.link == LinkStatus.Connected && s.snapshot != null
        val draft = e.draft
        val agv = draft?.raw(ConfigKey.AGV)?.toIntOrNull()
        val libraryHasSecret = draft != null && nets.any { it.ssid == draft.raw(ConfigKey.SSID) && (it.hasPassword || it.open) }
        val passwordRequired = draft != null && AutoFill.passwordRequired(draft, device?.ssid, libraryHasSecret)
        val taken = AgvHints.takenAgv(hist, s.address, s.snapshot?.mac)
        val check = draft?.let {
            ConfigForm.check(it, ValidationContext(takenAgv = taken), passwordRequired)
        }
        val changed = if (draft != null && device != null) {
            ConfigForm.changedKeys(draft, device, dnsSupported)
        } else {
            emptySet()
        }
        val running = a is ApplyUi.Running
        val batchNext = if (b.enabled) b.state.nextAgv else null
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
            profiles = profiles,
            selectedProfile = selected,
            ruleAvailable = e.ipState.rule is IpRule.Offset,
            ruleIp = AutoFill.ruleIp(e.ipState.rule, agv),
            ruleMismatchIp = draft?.let { AutoFill.mismatchIp(it, e.ipState, agv) },
            nextAgvHint = AgvHints.nextAgvFor(selected?.id, hist),
            rememberPassword = e.rememberPassword,
            networkSsids = nets.map { it.ssid },
            batch = b,
            batchAdvice = if (b.state is BatchState.Ready) e.batchAdvice else null,
            batchNextAgv = batchNext,
        )
    }

    private fun linkNote(link: LinkStatus, hasDraft: Boolean): String? = when {
        link == LinkStatus.Connected -> null
        link == LinkStatus.Rebooting || link == LinkStatus.Reconnecting ->
            "보드를 다시 찾는 중입니다. 연결되면 자동으로 이어집니다."
        link == LinkStatus.Connecting -> "보드에 연결하는 중입니다."
        hasDraft -> "연결이 끊겼습니다. 세팅 탭에서 다시 연결하세요."
        else -> "세팅 탭에서 연결하세요."
    }

    private fun isSuccess(result: ApplyResult): Boolean =
        result.outcome == ApplyOutcome.Applied || result.outcome == ApplyOutcome.SavedOnly

    companion object {
        const val AGV_MIN = 1
        const val AGV_MAX = 99
    }
}
