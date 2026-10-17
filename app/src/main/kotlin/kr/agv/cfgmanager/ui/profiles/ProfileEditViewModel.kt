package kr.agv.cfgmanager.ui.profiles

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.core.profiles.ProfileIssue
import kr.agv.cfgmanager.core.profiles.ProfileTemplate
import kr.agv.cfgmanager.core.profiles.ProfileValidator
import kr.agv.cfgmanager.core.util.PhoneNetwork
import kr.agv.cfgmanager.net.PhoneWifi
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** 프로필 편집 화면의 상태. [form] 이 null 이면 프로필을 찾지 못한 것이다. */
data class ProfileEditUi(
    val form: ProfileForm?,
    val isNew: Boolean,
    val issues: List<ProfileIssue>,
    val notice: String?,
    val confirmDelete: Boolean,
    val libraryPassword: Boolean,
    val loaded: Boolean = false,
)

/** 편집 화면이 해야 할 일 (화면 이동). */
sealed interface ProfileEditEvent {
    data object Done : ProfileEditEvent
    data class Opened(val id: String) : ProfileEditEvent
}

/**
 * 프로필 편집 화면의 상태 (docs/DEVELOPMENT_PLAN.md 5.6, F-P1 · F-P3 · F-P6 · F-E9).
 * 새 프로필은 본사 기본 템플릿 값으로 시작한다. 저장 전에 [ProfileValidator] 로 검사한다.
 */
class ProfileEditViewModel(private val container: AppContainer, private val profileId: String?) : ViewModel() {

    private val profiles = container.profiles
    private val networks = container.networks

    private val uiFlow = MutableStateFlow(ProfileEditUi(form = null, isNew = profileId == null, issues = emptyList(), notice = null, confirmDelete = false, libraryPassword = false))
    val ui: StateFlow<ProfileEditUi> = uiFlow.asStateFlow()

    private val eventChannel = Channel<ProfileEditEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEditEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val doc = profiles.load()
            val form = if (profileId == null) {
                val now = nowIso()
                ProfileForm.of(
                    ProfileTemplate.headquarters(now).copy(
                        id = UUID.randomUUID().toString(),
                        name = "새 프로필",
                        notes = "",
                        ssid = "",
                    ),
                )
            } else {
                doc.profiles.firstOrNull { it.id == profileId }?.let { ProfileForm.of(it) }
            }
            val password = form?.ssid?.takeIf { it.isNotEmpty() }?.let { networks.password(it) } != null
            uiFlow.value = uiFlow.value.copy(form = form, libraryPassword = password, loaded = true)
        }
    }

    fun update(transform: (ProfileForm) -> ProfileForm) {
        uiFlow.value = uiFlow.value.let { st -> st.copy(form = st.form?.let(transform), issues = emptyList(), notice = null) }
    }

    fun save() {
        val form = uiFlow.value.form ?: return
        val now = nowIso()
        val issues = form.issues(now)
        if (ProfileValidator.hasErrors(issues)) {
            uiFlow.value = uiFlow.value.copy(issues = issues)
            return
        }
        viewModelScope.launch {
            profiles.upsert(form.toProfile(now))
            eventChannel.send(ProfileEditEvent.Done)
        }
    }

    /** [복제]: 지금 값을 복사한 새 프로필을 만들고 그 편집 화면으로 간다. */
    fun duplicate() {
        val form = uiFlow.value.form ?: return
        val now = nowIso()
        val copy = form.copy(id = UUID.randomUUID().toString(), name = "${form.name.trim()} 사본", createdAt = now, lastUsedAt = null)
        viewModelScope.launch {
            profiles.upsert(copy.toProfile(now))
            eventChannel.send(ProfileEditEvent.Opened(copy.id))
        }
    }

    fun askDelete(ask: Boolean) {
        uiFlow.value = uiFlow.value.copy(confirmDelete = ask)
    }

    fun delete() {
        val id = profileId ?: return
        viewModelScope.launch {
            profiles.delete(id)
            eventChannel.send(ProfileEditEvent.Done)
        }
    }

    /**
     * [현재 Wi-Fi 에서 가져오기] (7.4). SSID 는 [withSsid] 일 때만 읽는다(위치 권한).
     * 못 얻은 값은 건드리지 않고, 빠진 항목과 5GHz 여부를 안내로 남긴다.
     */
    fun importPhoneWifi(context: Context, withSsid: Boolean) {
        val snapshot = PhoneWifi.read(context)
        if (snapshot == null) {
            uiFlow.value = uiFlow.value.copy(notice = "휴대폰이 Wi-Fi 에 연결되어 있지 않습니다. 현장 Wi-Fi 에 먼저 연결하세요.")
            return
        }
        val fields = PhoneNetwork.fieldsOf(if (withSsid) snapshot else snapshot.copy(ssid = null))
        val parts = mutableListOf<String>()
        if (fields.missing.isNotEmpty()) parts += "못 가져온 항목: ${fields.missing.joinToString()}"
        if (fields.fiveGhz) parts += "휴대폰이 5GHz 에 붙어 있습니다. 브리지는 2.4GHz 만 쓰므로 같은 SSID 의 2.4GHz 가 있는지 확인하세요."
        uiFlow.value = uiFlow.value.copy(
            form = uiFlow.value.form?.withPhoneWifi(fields),
            notice = parts.joinToString(" ").ifEmpty { "현장 Wi-Fi 값을 채웠습니다." },
        )
    }

    private fun nowIso(): String = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
}
