package kr.agv.cfgmanager.pkg

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.BridgeCommands
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kr.agv.cfgmanager.core.pkg.DsppkgExtractor
import kr.agv.cfgmanager.core.pkg.PkgDownloadOutcome
import kr.agv.cfgmanager.core.pkg.PkgDownloadWorkflow
import kr.agv.cfgmanager.core.pkg.PkgFiles
import kr.agv.cfgmanager.core.pkg.PkgFormatException
import kr.agv.cfgmanager.core.pkg.PkgList
import kr.agv.cfgmanager.core.pkg.PkgSlot
import kr.agv.cfgmanager.core.pkg.defaultPkgSlot
import kr.agv.cfgmanager.core.pkg.parsePkgList
import java.io.File
import java.io.IOException
import java.util.Locale

/** 메인보드 원본 화면 상태. 오류 문구는 단어뿐이고 예외 원문 · 경로 · 펌웨어 코드는 담지 않는다. */
sealed interface PkgUi {
    data object NotConnected : PkgUi
    data object Loading : PkgUi

    /** 읽기 실패, 또는 브리지 0.9.1 미만 (USB 로그 줄에 JSON 이 깨져도 여기로 온다). */
    data object Unsupported : PkgUi
    data object NoPartition : PkgUi
    data object Empty : PkgUi
    data class Slots(val slots: List<PkgSlot>, val preferred: Int?) : PkgUi
    data class Downloading(val slot: PkgSlot, val done: Long, val total: Long) : PkgUi
    data class ReadyToSave(val files: PkgFiles, val folder: String) : PkgUi
    data class Saved(val folder: String) : PkgUi
    data class Error(val text: String, val canRetry: Boolean) : PkgUi
}

/**
 * 메인보드 원본(.out · .map) 받기 (docs 설계: 앱 흐름). 앱 수명 scope 에서 돌아 화면을 나가도 받기가 이어진다.
 * [exclusive] 는 `BridgeSession.withExclusive` 를 감싼 함수다. 이 함수는 메인 스레드에서만 불러야 하므로
 * [scope] 는 메인 디스패처여야 한다. 파일 작업은 [io] 에서 한다.
 * 캐시 파일 존재 여부로 "이미 풀었다" 를 판단하지 않는다. 믿는 것은 [PkgFiles] 뿐이다.
 */
class PkgController(
    private val exclusive: suspend (suspend (IBridgeChannel) -> Unit) -> Unit,
    /** 세션이 다른 작업으로 독점 중이면 true (`BridgeSession.busy`). */
    private val isBusy: () -> Boolean,
    private val agvNumber: () -> Int?,
    cacheDir: File,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** 큰 사건만 앱 로그로 보낸다. 경로 · 예외 문구는 넘기지 않는다. */
    private val journal: (String) -> Unit = {},
) {
    private val pkgDir = File(cacheDir, "pkg")
    private val uiFlow = MutableStateFlow<PkgUi>(PkgUi.Loading)
    val ui: StateFlow<PkgUi> = uiFlow.asStateFlow()

    private var job: Job? = null
    private var lastSlots: PkgUi.Slots? = null

    /** 저장 대기 중인 패키지의 ID (저장 뒤 캐시 정리용). */
    private var readyId: String? = null

    /** 다시 시도가 이어받을 슬롯 ID. null 이면 목록만 다시 읽는다. */
    private var retryId: String? = null

    private val busy get() = job?.isActive == true

    fun refresh() {
        if (busy) return
        job = scope.launch { loadSlots() }
    }

    fun download(slot: PkgSlot) {
        if (busy) return
        job = scope.launch { runDownload(slot) }
    }

    /** 오류 화면의 다시 시도. 목록을 다시 읽고, 같은 슬롯이 아직 있으면 이어받는다. */
    fun retry() {
        if (busy) return
        val id = retryId
        job = scope.launch {
            val list = loadSlots() ?: return@launch
            val slot = id?.let { wanted -> list.slots.firstOrNull { it.id == wanted } } ?: return@launch
            runDownload(slot)
        }
    }

    /** 받던 것을 멈추고 목록으로 돌아간다. 받은 부분은 캐시에 남는다. */
    fun cancel() {
        val running = uiFlow.value as? PkgUi.Downloading ?: return
        job?.cancel()
        journal("원본 받기 취소")
        uiFlow.value = lastSlots ?: PkgUi.Slots(listOf(running.slot), running.slot.index)
    }

    fun onSaved() {
        val ready = uiFlow.value as? PkgUi.ReadyToSave ?: return
        val id = readyId
        readyId = null
        journal("원본 저장 완료")
        uiFlow.value = PkgUi.Saved(ready.folder)
        if (id != null) scope.launch { withContext(io) { cleanQuietly(id) } }
    }

    /** 저장에 실패했다. 상태와 캐시를 그대로 두고 다시 저장하게 한다. [text] 는 고정 문구다. */
    fun onSaveFailed(text: String) {
        if (uiFlow.value !is PkgUi.ReadyToSave) return
        journal("원본 저장 실패: $text")
    }

    /** 슬롯 목록을 읽어 상태에 반영한다. 목록이 있으면 돌려주고, 아니면 오류 · 빈 상태로 두고 null. */
    private suspend fun loadSlots(): PkgUi.Slots? {
        uiFlow.value = PkgUi.Loading
        var text = ""
        try {
            exclusive { channel -> text = channel.exec(BridgeCommands.pkgJson()) }
        } catch (_: BridgeChannelException.NotConnected) {
            uiFlow.value = PkgUi.NotConnected
            return null
        } catch (_: BridgeChannelException.Disconnected) {
            fail("연결 끊김", retryable = true, id = null)
            return null
        } catch (_: BridgeChannelException) {
            // 시간 초과: 응답이 없었다
            uiFlow.value = PkgUi.Unsupported
            return null
        }
        when (val list = parsePkgList(text)) {
            PkgList.Unsupported -> uiFlow.value = PkgUi.Unsupported
            PkgList.NoPartition -> uiFlow.value = PkgUi.NoPartition
            PkgList.Busy -> fail("메인보드 작업 중", retryable = true, id = null)
            is PkgList.Ok -> {
                if (list.slots.isEmpty()) {
                    uiFlow.value = PkgUi.Empty
                } else {
                    val slots = PkgUi.Slots(list.slots, defaultPkgSlot(list.slots)?.index)
                    lastSlots = slots
                    uiFlow.value = slots
                    return slots
                }
            }
        }
        return null
    }

    private suspend fun runDownload(slot: PkgSlot) {
        if (isBusy()) {
            fail("작업 중", retryable = true, id = slot.id)
            return
        }
        val part = File(pkgDir, "${slot.id}.part")
        uiFlow.value = PkgUi.Downloading(slot, 0, slot.size)
        journal("원본 받기 시작 슬롯 ${slot.index}")
        try {
            withContext(io) { cleanCache(keepPart = part.name, id = null) }
        } catch (_: IOException) {
            fail("저장 공간 부족 또는 쓰기 실패", retryable = true, id = slot.id)
            return
        }
        var outcome: PkgDownloadOutcome? = null
        try {
            exclusive { channel ->
                outcome = PkgDownloadWorkflow.run(channel, slot, part) { done, total ->
                    uiFlow.value = PkgUi.Downloading(slot, done, total)
                }
            }
        } catch (_: BridgeChannelException.NotConnected) {
            uiFlow.value = PkgUi.NotConnected
            return
        } catch (_: BridgeChannelException) {
            outcome = PkgDownloadOutcome.Disconnected
        }
        when (val result = outcome) {
            is PkgDownloadOutcome.Done -> extract(slot, result.file)
            PkgDownloadOutcome.Disconnected -> fail("연결 끊김", true, slot.id)
            PkgDownloadOutcome.Busy -> fail("메인보드 작업 중", true, slot.id)
            PkgDownloadOutcome.SlotChanged -> fail("패키지 변경됨", true, null)
            PkgDownloadOutcome.Corrupt -> fail("내용 불일치", true, slot.id)
            PkgDownloadOutcome.DiskFailed -> fail("저장 공간 부족 또는 쓰기 실패", true, slot.id)
            is PkgDownloadOutcome.Failed -> if (result.reason == "range") {
                withContext(io) { part.delete() }
                fail("패키지 변경됨", true, null)
            } else {
                fail("읽기 실패", true, slot.id)
            }
            null -> fail("읽기 실패", true, slot.id)
        }
        journal("원본 받기 결과 ${describe(outcome)}")
    }

    private suspend fun extract(slot: PkgSlot, part: File) {
        val dir = File(pkgDir, slot.id)
        try {
            val files = withContext(io) { DsppkgExtractor.extract(part, slot.id, slot.ver, dir) }
            readyId = slot.id
            retryId = null
            uiFlow.value = PkgUi.ReadyToSave(files, folderName(slot))
        } catch (_: PkgFormatException) {
            withContext(io) { cleanQuietly(slot.id) }
            fail("패키지 형식 오류", retryable = false, id = null)
        } catch (_: IOException) {
            fail("저장 공간 부족 또는 쓰기 실패", retryable = true, id = slot.id)
        }
    }

    private fun folderName(slot: PkgSlot): String =
        "AGV%02d_%s_%s".format(Locale.ROOT, agvNumber() ?: 0, slot.ver, slot.id.take(ID_SHORT))

    private fun fail(text: String, retryable: Boolean, id: String?) {
        retryId = id
        uiFlow.value = PkgUi.Error(text, canRetry = retryable)
    }

    /**
     * 캐시 정리. [keepPart] 가 있으면 그 파일만 남기고 모두 지운다. [id] 가 있으면 그 패키지의 `.part` 와 폴더만 지운다.
     * 못 지운 파일은 [IOException] 으로 알린다.
     */
    private fun cleanCache(keepPart: String?, id: String?) {
        val entries = pkgDir.listFiles() ?: return
        for (entry in entries) {
            val remove = if (id != null) entry.name == id || entry.name == "$id.part" else entry.name != keepPart
            if (remove && !entry.deleteRecursively()) throw IOException("캐시를 지우지 못함")
        }
    }

    /** 저장 · 형식 오류 뒤 정리. 못 지워도 사용자 흐름은 막지 않는다 (다음 받기가 다시 정리한다). */
    private fun cleanQuietly(id: String) {
        try {
            cleanCache(keepPart = null, id = id)
        } catch (_: IOException) {
        }
    }

    // 로그에는 결과 종류만 남긴다 (예외 문구에 경로가 있을 수 있다)
    private fun describe(outcome: PkgDownloadOutcome?): String = when (outcome) {
        is PkgDownloadOutcome.Failed -> "Failed ${outcome.reason.takeIf { REASON_RE.matches(it) }.orEmpty()}".trim()
        null -> "none"
        else -> outcome::class.simpleName.orEmpty()
    }

    private companion object {
        const val ID_SHORT = 8
        val REASON_RE = Regex("[a-z]{1,12}")
    }
}
