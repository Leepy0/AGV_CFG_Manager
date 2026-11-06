package kr.agv.cfgmanager.pkg

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.ChannelKind
import kr.agv.cfgmanager.core.bridge.ChannelState
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

private const val VER = "1.2.3"

private fun sha(bytes: ByteArray) =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        for ((name, data) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(data)
            zip.closeEntry()
        }
    }
    return bytes.toByteArray()
}

/** 규격에 맞는 가짜 패키지. 크기가 512 를 넘어 청크가 여러 번 나뉜다. */
private fun validPackage(): ByteArray {
    val dspfw = ByteArray(300) { (it % 7).toByte() }
    val out = ByteArray(1500) { (it % 251).toByte() }
    val map = "OUTPUT FILE NAME:   <AGV_Main.out>\n".toByteArray()
    fun entry(name: String, bytes: ByteArray) =
        """{"name":"$name","orig_name":"x.bin","size":${bytes.size},"sha256":"${sha(bytes)}"}"""
    val manifest = """{"format":"agv-dsppkg","format_ver":1,"ver":"$VER","files":{""" +
        """"dspfw":${entry("AGV_Main_1.2.3.dspfw", dspfw)},"out":${entry("AGV_Main.out", out)},"map":${entry("AGV_Main.map", map)}}}"""
    return zipOf(
        "manifest.json" to manifest.toByteArray(),
        "AGV_Main_1.2.3.dspfw" to dspfw,
        "AGV_Main.out" to out,
        "AGV_Main.map" to map,
    )
}

/** 패키지 ID 는 manifest 해시 앞 16자다. */
private fun manifestId(zip: ByteArray): String {
    val z = ZipInputStream(zip.inputStream())
    while (true) {
        val e = z.nextEntry ?: error("manifest 없음")
        if (e.name == "manifest.json") return sha(z.readBytes()).take(16)
    }
}

/** 브리지 대역: `dsp pkg json` · `dsp pkg read` 에만 답한다. */
private class PkgChannel(
    private val pkg: ByteArray,
    private val id: String,
    private val jsonReply: String? = null,
) : IBridgeChannel {
    override val kind = ChannelKind.Fake
    override val state: StateFlow<ChannelState> = MutableStateFlow(ChannelState.Disconnected)
    override val raw: SharedFlow<String> = MutableSharedFlow()

    val readOffsets: MutableList<Long> = java.util.Collections.synchronizedList(mutableListOf())

    /** 이 오프셋을 읽을 때 한 번 끊는다. */
    var dropAt: Long? = null

    /** 이 오프셋을 읽을 때 [hold] 가 풀릴 때까지 기다린다. */
    var holdAt: Long? = null
    val hold = CompletableDeferred<Unit>()
    var readError: String? = null

    override suspend fun exec(line: String, timeout: Duration): String {
        val parts = line.split(" ")
        if (parts.getOrNull(2) == "json") {
            return jsonReply
                ?: """{"slots":[{"i":0,"st":"APPLIED","id":"$id","ver":"$VER","size":${pkg.size},"seq":1,"sha256":"${sha(pkg)}"}]}"""
        }
        val off = parts[4].toLong()
        readOffsets += off
        if (off == dropAt) {
            dropAt = null
            throw BridgeChannelException.Disconnected()
        }
        if (off == holdAt) hold.await()
        readError?.let { return "E $it\n" }
        val data = pkg.copyOfRange(off.toInt(), minOf(pkg.size.toLong(), off + parts[5].toLong()).toInt())
        val crc = "%08x".format(CRC32().apply { update(data) }.value)
        return "P $id $off ${data.size} $crc ${Base64.getEncoder().encodeToString(data)}\n"
    }

    override suspend fun execThenDisconnect(line: String): String = error("미사용")
    override suspend fun waitReady(timeout: Duration) = true
    override suspend fun disconnect() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class PkgControllerTest {
    private lateinit var cache: File
    private val pkgDir get() = File(cache, "pkg")

    @BeforeTest
    fun setUp() {
        cache = Files.createTempDirectory("pkg-ctl").toFile()
    }

    @AfterTest
    fun tearDown() {
        cache.deleteRecursively()
    }

    private fun TestScope.controller(channel: IBridgeChannel?, agv: Int? = 7, busy: () -> Boolean = { false }): PkgController {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return PkgController(
            exclusive = { block -> block(channel ?: throw BridgeChannelException.NotConnected()) },
            isBusy = busy,
            agvNumber = { agv },
            cacheDir = cache,
            scope = CoroutineScope(backgroundScope.coroutineContext + dispatcher),
            io = dispatcher,
        )
    }

    /** 워크플로가 실제 IO 스레드에서 파일을 쓰므로 가상 시간이 아니라 실제 시간으로 기다린다. */
    private suspend fun eventually(cond: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(10_000) { while (!cond()) delay(5) }
    }

    private suspend fun PkgController.until(check: (PkgUi) -> Boolean) = eventually { check(ui.value) }

    private fun TestScope.slotsOf(c: PkgController): PkgUi.Slots {
        c.refresh()
        return assertIs(c.ui.value)
    }

    @Test
    fun `연결이 없으면 NotConnected`() = runTest {
        val c = controller(null)
        c.refresh()
        assertEquals(PkgUi.NotConnected, c.ui.value)
    }

    @Test
    fun `구버전 응답은 Unsupported`() = runTest {
        val pkg = validPackage()
        val c = controller(PkgChannel(pkg, manifestId(pkg), jsonReply = "unknown command\n"))
        c.refresh()
        assertEquals(PkgUi.Unsupported, c.ui.value)
    }

    @Test
    fun `슬롯이 없으면 Empty 이고 파티션이 없으면 NoPartition`() = runTest {
        val pkg = validPackage()
        val empty = controller(PkgChannel(pkg, manifestId(pkg), jsonReply = """{"slots":[{"i":0,"st":"empty"}]}"""))
        empty.refresh()
        assertEquals(PkgUi.Empty, empty.ui.value)
        val none = controller(PkgChannel(pkg, manifestId(pkg), jsonReply = """{"err":"nopart"}"""))
        none.refresh()
        assertEquals(PkgUi.NoPartition, none.ui.value)
    }

    @Test
    fun `받기는 Downloading 을 거쳐 ReadyToSave 가 되고 폴더 이름이 정해진다`() = runTest {
        val pkg = validPackage()
        val id = manifestId(pkg)
        val c = controller(PkgChannel(pkg, id))
        val seen = mutableListOf<PkgUi>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.ui.collect { seen += it } }
        val slots = slotsOf(c)
        assertEquals(0, slots.preferred)
        c.download(slots.slots.single())
        c.until { it is PkgUi.ReadyToSave }
        val ready = assertIs<PkgUi.ReadyToSave>(c.ui.value)
        assertEquals("AGV07_${VER}_${id.take(8)}", ready.folder)
        assertTrue(ready.files.out.isFile && ready.files.map.isFile)
        assertTrue(seen.any { it is PkgUi.Downloading && it.done > 0 && it.done < it.total })
    }

    @Test
    fun `호기를 모르면 AGV00`() = runTest {
        val pkg = validPackage()
        val c = controller(PkgChannel(pkg, manifestId(pkg)), agv = null)
        c.download(slotsOf(c).slots.single())
        c.until { it is PkgUi.ReadyToSave }
        assertTrue(assertIs<PkgUi.ReadyToSave>(c.ui.value).folder.startsWith("AGV00_"))
    }

    @Test
    fun `끊기면 재시도 가능 오류이고 다시 받기는 받은 곳에서 이어받는다`() = runTest {
        val pkg = validPackage()
        val id = manifestId(pkg)
        val channel = PkgChannel(pkg, id).apply { dropAt = 1024 }
        val c = controller(channel)
        c.download(slotsOf(c).slots.single())
        c.until { it is PkgUi.Error }
        assertEquals(PkgUi.Error("연결 끊김", canRetry = true), c.ui.value)
        assertEquals(1024L, File(pkgDir, "$id.part").length())
        channel.readOffsets.clear()
        c.retry()
        c.until { it is PkgUi.ReadyToSave }
        assertEquals(1024L, channel.readOffsets.first())
    }

    @Test
    fun `저장 실패는 ReadyToSave 와 캐시를 지키고 저장 성공은 캐시를 지운다`() = runTest {
        val pkg = validPackage()
        val id = manifestId(pkg)
        val c = controller(PkgChannel(pkg, id))
        c.download(slotsOf(c).slots.single())
        c.until { it is PkgUi.ReadyToSave }
        val ready = assertIs<PkgUi.ReadyToSave>(c.ui.value)
        c.onSaveFailed("저장 실패")
        assertEquals(ready, c.ui.value)
        assertTrue(ready.files.out.isFile)
        c.onSaved()
        assertEquals(PkgUi.Saved(ready.folder), c.ui.value)
        assertFalse(File(pkgDir, "$id.part").exists())
        assertFalse(File(pkgDir, id).exists())
    }

    @Test
    fun `취소는 받은 부분을 지키고 목록으로 돌아간다`() = runTest {
        val pkg = validPackage()
        val id = manifestId(pkg)
        val channel = PkgChannel(pkg, id).apply { holdAt = 512 }
        val c = controller(channel)
        c.download(slotsOf(c).slots.single())
        eventually { 512L in channel.readOffsets }
        assertIs<PkgUi.Downloading>(c.ui.value)
        c.cancel()
        assertIs<PkgUi.Slots>(c.ui.value)
        assertEquals(512L, File(pkgDir, "$id.part").length())
    }

    @Test
    fun `받는 중에는 받기와 새로 읽기를 무시한다`() = runTest {
        val pkg = validPackage()
        val channel = PkgChannel(pkg, manifestId(pkg)).apply { holdAt = 512 }
        val c = controller(channel)
        val slot = slotsOf(c).slots.single()
        c.download(slot)
        eventually { 512L in channel.readOffsets }
        val during = c.ui.value
        assertIs<PkgUi.Downloading>(during)
        c.download(slot)
        c.refresh()
        assertEquals(during, c.ui.value)
        assertEquals(listOf(0L, 512L), channel.readOffsets.toList())
    }

    @Test
    fun `세션이 작업 중이면 받기를 거절하고 다시 시도로 받는다`() = runTest {
        val pkg = validPackage()
        val channel = PkgChannel(pkg, manifestId(pkg))
        var sessionBusy = false
        val c = controller(channel, busy = { sessionBusy })
        val slot = slotsOf(c).slots.single()
        sessionBusy = true
        c.download(slot)
        c.until { it is PkgUi.Error }
        assertEquals(PkgUi.Error("작업 중", canRetry = true), c.ui.value)
        assertTrue(channel.readOffsets.isEmpty())
        sessionBusy = false
        c.retry()
        c.until { it is PkgUi.ReadyToSave }
    }

    @Test
    fun `파일에 쓰지 못하면 저장 공간 문구이고 다시 시도할 수 있다`() = runTest {
        val pkg = validPackage()
        val id = manifestId(pkg)
        File(pkgDir, "$id.part").mkdirs() // 폴더라서 파일로 열리지 않는다
        val c = controller(PkgChannel(pkg, id))
        c.download(slotsOf(c).slots.single())
        c.until { it is PkgUi.Error }
        assertEquals(PkgUi.Error("저장 공간 부족 또는 쓰기 실패", canRetry = true), c.ui.value)
    }

    @Test
    fun `받기를 시작하면 다른 패키지의 오래된 캐시를 지운다`() = runTest {
        val pkg = validPackage()
        val id = manifestId(pkg)
        pkgDir.mkdirs()
        File(pkgDir, "ffffffffffffffff.part").writeBytes(ByteArray(10))
        File(pkgDir, "ffffffffffffffff").mkdirs()
        File(pkgDir, "ffffffffffffffff/a.out").writeBytes(ByteArray(10))
        val c = controller(PkgChannel(pkg, id))
        c.download(slotsOf(c).slots.single())
        c.until { it is PkgUi.ReadyToSave }
        assertEquals(setOf("$id.part", id), pkgDir.list().orEmpty().toSet())
    }

    @Test
    fun `패키지 형식 오류는 재시도 없이 캐시를 지운다`() = runTest {
        val junk = ByteArray(700) { it.toByte() }
        val c = controller(PkgChannel(junk, "0123456789abcdef"))
        c.download(slotsOf(c).slots.single())
        c.until { it is PkgUi.Error }
        assertEquals(PkgUi.Error("패키지 형식 오류", canRetry = false), c.ui.value)
        assertEquals(emptyList(), pkgDir.list().orEmpty().toList())
    }

    @Test
    fun `range 거절은 받은 부분을 지우고 다시 읽는다`() = runTest {
        val pkg = validPackage()
        val id = manifestId(pkg)
        val channel = PkgChannel(pkg, id).apply { holdAt = 512 }
        val c = controller(channel)
        c.download(slotsOf(c).slots.single())
        eventually { 512L in channel.readOffsets }
        channel.readError = "range"
        channel.hold.complete(Unit)
        c.until { it is PkgUi.Error }
        assertEquals(PkgUi.Error("패키지 변경됨", canRetry = true), c.ui.value)
        assertFalse(File(pkgDir, "$id.part").exists())
        channel.readError = null
        c.retry()
        assertIs<PkgUi.Slots>(c.ui.value)
    }
}
