package kr.agv.cfgmanager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.core.profiles.Profile
import kr.agv.cfgmanager.core.profiles.ProfileCodec
import kr.agv.cfgmanager.core.profiles.ProfileDocument
import kr.agv.cfgmanager.core.profiles.ProfileTemplate
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 프로필 저장소 (`filesDir/profiles.json`, 부록 B-1). 모든 쓰기는 [mutex] 안에서 원자적으로 한다.
 * 첫 실행(파일 없음)이면 "본사" 기본 프로필을 만든다. 파일이 깨졌으면 옆으로 옮겨 보관하고 기본 프로필로 시작한다.
 * 파일 I/O 는 모두 IO 디스패처에서 돈다.
 */
class ProfileStore(
    private val file: File,
    private val nowIso: () -> String = { Instant.now().truncatedTo(ChronoUnit.SECONDS).toString() },
) {
    private val mutex = Mutex()
    private val documentFlow = MutableStateFlow<ProfileDocument?>(null)

    /** 읽은 뒤의 문서. [load] 전에는 null. */
    val document: StateFlow<ProfileDocument?> = documentFlow.asStateFlow()

    private val noticeFlow = MutableStateFlow<String?>(null)

    /** 깨진 파일을 옮겼을 때 한 번 보여 줄 안내. */
    val notice: StateFlow<String?> = noticeFlow.asStateFlow()

    /** 파일을 읽는다. 없으면 기본 프로필을 쓰고, 이미 읽었으면 그 값을 돌려준다. */
    suspend fun load(): ProfileDocument = mutex.withLock {
        documentFlow.value ?: withContext(Dispatchers.IO) { readOrCreateLocked() }.also { documentFlow.value = it }
    }

    /** 문서를 바꾸고 파일에 쓴다. [transform] 은 현재 문서를 받아 새 문서를 돌려준다. */
    suspend fun update(transform: (ProfileDocument) -> ProfileDocument): ProfileDocument = mutex.withLock {
        val current = documentFlow.value
            ?: withContext(Dispatchers.IO) { readOrCreateLocked() }
        val next = transform(current)
        withContext(Dispatchers.IO) { writeAtomic(file, ProfileCodec.encodeDocument(next)) }
        documentFlow.value = next
        next
    }

    /** 같은 id 가 있으면 바꾸고 없으면 추가한다. 마지막 프로필이 없으면 이 프로필을 기억한다. */
    suspend fun upsert(profile: Profile): ProfileDocument = update { doc ->
        val list = if (doc.profiles.any { it.id == profile.id }) {
            doc.profiles.map { if (it.id == profile.id) profile else it }
        } else {
            doc.profiles + profile
        }
        doc.copy(profiles = list, lastProfileId = doc.lastProfileId ?: profile.id)
    }

    suspend fun delete(id: String): ProfileDocument = update { doc ->
        val left = doc.profiles.filter { it.id != id }
        val last = if (doc.lastProfileId == id) left.firstOrNull()?.id else doc.lastProfileId
        doc.copy(profiles = left, lastProfileId = last)
    }

    suspend fun setLast(id: String?): ProfileDocument = update { doc -> doc.copy(lastProfileId = id) }

    /** 적용 성공 뒤 마지막 사용 시각과 마지막 프로필을 갱신한다. 없는 id 는 무시한다. */
    suspend fun markUsed(id: String, nowIsoText: String = nowIso()): ProfileDocument = update { doc ->
        doc.copy(
            lastProfileId = id,
            profiles = doc.profiles.map { if (it.id == id) it.copy(lastUsedAt = nowIsoText) else it },
        )
    }

    private fun readOrCreateLocked(): ProfileDocument {
        if (!file.exists()) {
            return createDefault().also { writeAtomic(file, ProfileCodec.encodeDocument(it)) }
        }
        val text = file.readText(Charsets.UTF_8)
        return try {
            ProfileCodec.decodeDocument(text)
        } catch (broken: IllegalArgumentException) {
            val moved = File(file.parentFile, "profiles.corrupt-${System.currentTimeMillis()}.json")
            file.renameTo(moved)
            noticeFlow.value = "프로필 파일을 읽지 못해 기본 프로필로 시작했습니다. 이전 파일은 ${moved.name} 로 보관했습니다."
            createDefault().also { writeAtomic(file, ProfileCodec.encodeDocument(it)) }
        }
    }

    private fun createDefault(): ProfileDocument {
        val hq = ProfileTemplate.headquarters(nowIso())
        return ProfileDocument(version = ProfileCodec.VERSION, lastProfileId = hq.id, profiles = listOf(hq))
    }
}
