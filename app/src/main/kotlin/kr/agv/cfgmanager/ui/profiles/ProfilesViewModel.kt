package kr.agv.cfgmanager.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.core.profiles.ExportBundle
import kr.agv.cfgmanager.core.profiles.ImportedBundle
import kr.agv.cfgmanager.core.profiles.Profile
import kr.agv.cfgmanager.core.profiles.ProfileDocument
import kr.agv.cfgmanager.core.profiles.ProfileExport
import kr.agv.cfgmanager.core.profiles.WifiNetwork
import java.util.UUID

/** 가져오기 확인 대기. [conflicts] 는 같은 이름의 프로필 이름들이다. */
data class PendingImport(val imported: ImportedBundle, val conflicts: List<String>)

/** 가져오기 결과. [needsPassword] 는 BLE 비밀번호가 없어 입력이 필요한 첫 프로필 id. */
data class ImportOutcome(val count: Int, val needsPassword: String?)

/** 프로필 목록 화면의 상태와 내보내기 · 가져오기 (F-P4). 파일 읽기 · 쓰기는 화면이 맡고, 여기는 내용만 만든다. */
class ProfilesViewModel(private val container: AppContainer) : ViewModel() {

    private val profiles = container.profiles
    private val networks = container.networks

    val document: StateFlow<ProfileDocument?> = profiles.document
    val notice: StateFlow<String?> = profiles.notice

    private val pendingFlow = MutableStateFlow<PendingImport?>(null)
    val pending: StateFlow<PendingImport?> = pendingFlow.asStateFlow()

    private val messageFlow = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = messageFlow.asStateFlow()

    /** 내보내기 파일 내용을 만든다. 비밀번호는 [includePasswords] 가 참일 때만 들어간다. */
    fun buildExport(includePasswords: Boolean, includeLibrary: Boolean, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val doc = profiles.load()
            val wifi = if (!includeLibrary) emptyList() else withContext(Dispatchers.IO) {
                networks.entries.value.map { entry ->
                    WifiNetwork(
                        ssid = entry.ssid,
                        password = if (includePasswords) networks.password(entry.ssid) else null,
                        open = entry.open,
                        lastUsedAt = entry.lastUsedAt,
                        useCount = entry.useCount,
                    )
                }
            }
            onReady(ProfileExport.export(ExportBundle(doc.profiles, wifi), includePasswords))
        }
    }

    fun stageImportFailed() {
        messageFlow.value = "가져올 파일을 읽지 못했습니다. 다시 고르세요."
    }

    fun reportCameraDenied() {
        messageFlow.value = "카메라 권한이 있어야 QR 을 읽을 수 있습니다. 허용하거나 파일로 가져오세요."
    }

    fun reportWriteFailure() {
        messageFlow.value = "파일에 쓰지 못했습니다. 저장 공간과 저장 위치를 확인하세요."
    }

    /** 가져오기 파일을 읽는다. 같은 이름이 있으면 확인 대기로 두고, 없으면 바로 가져온다. */
    fun stageImport(text: String, onDone: (ImportOutcome) -> Unit) {
        val imported = try {
            ProfileExport.import(text)
        } catch (failure: IllegalArgumentException) {
            messageFlow.value = "프로필 내보내기 파일(.agvcfg.json)이 아니거나 내용이 깨졌습니다."
            return
        }
        val existing = profiles.document.value?.profiles.orEmpty().map { it.name }.toSet()
        val conflicts = imported.bundle.profiles.map { it.name }.filter { it in existing }
        if (conflicts.isEmpty()) {
            viewModelScope.launch { commit(imported, overwrite = false, onDone = onDone) }
        } else {
            pendingFlow.value = PendingImport(imported, conflicts)
        }
    }

    /** 확인 대기 가져오기를 진행한다. [overwrite] 면 같은 이름의 프로필을 바꾸고, 아니면 건너뛴다. */
    fun confirmImport(overwrite: Boolean, onDone: (ImportOutcome) -> Unit) {
        val pending = pendingFlow.value ?: return
        pendingFlow.value = null
        viewModelScope.launch { commit(pending.imported, overwrite, onDone) }
    }

    /** 확인 대기에서 [건너뛰기]: 같은 이름은 두고 나머지만 가져온다. */
    fun skipConflicts(onDone: (ImportOutcome) -> Unit) = confirmImport(overwrite = false, onDone = onDone)

    private suspend fun commit(imported: ImportedBundle, overwrite: Boolean, onDone: (ImportOutcome) -> Unit) {
        val doc = profiles.load()
        var count = 0
        var needsPassword: String? = null
        for (incoming in imported.bundle.profiles) {
            val same = doc.profiles.firstOrNull { it.name == incoming.name }
            if (same != null && !overwrite) continue
            val id = same?.id ?: if (doc.profiles.any { it.id == incoming.id }) UUID.randomUUID().toString() else incoming.id
            val profile: Profile = incoming.copy(id = id)
            profiles.upsert(profile)
            count++
            if (profile.blePassword.isEmpty() && needsPassword == null) needsPassword = id
        }
        for (wifi in imported.bundle.networks) {
            // 내보내기 파일이 비밀번호를 포함했을 때만 저장한다. 없는 항목은 목록에만 남긴다.
            networks.remember(wifi.ssid, wifi.password ?: if (wifi.open) "" else null, wifi.lastUsedAt ?: nowIso())
        }
        messageFlow.value = if (count == 0) "가져온 프로필이 없습니다. 같은 이름은 건너뛰었습니다." else null
        onDone(ImportOutcome(count, needsPassword))
    }

    private fun nowIso(): String = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
}
