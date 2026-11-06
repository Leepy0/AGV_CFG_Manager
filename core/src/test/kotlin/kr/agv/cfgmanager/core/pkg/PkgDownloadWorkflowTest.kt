package kr.agv.cfgmanager.core.pkg

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.ChannelKind
import kr.agv.cfgmanager.core.bridge.ChannelState
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kr.agv.cfgmanager.core.fake.FakeBridgeChannel
import kr.agv.cfgmanager.core.fake.FakeDevice
import kr.agv.cfgmanager.core.fake.FakeDeviceOptions
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.CRC32
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val PKG_ID = "0123456789abcdef"
private const val OTHER_ID = "fedcba9876543210"

private typealias Step = suspend (off: Long) -> String

private fun chunkLine(id: String, off: Long, data: ByteArray): String {
    val crc = "%08x".format(CRC32().apply { update(data) }.value)
    return "P $id $off ${data.size} $crc ${Base64.getEncoder().encodeToString(data)}\n"
}

private fun sha256Hex(data: ByteArray) =
    MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

/** 호출 순서대로 고장을 넣는 채널. [script] 의 null 과 목록 뒤의 호출은 정상 청크를 돌려준다. */
private class ScriptedChannel(
    private val pkg: ByteArray,
    private val script: List<Step?> = emptyList(),
) : IBridgeChannel {
    val offsets = mutableListOf<Long>()
    val lengths = mutableListOf<Int>()
    val timeouts = mutableListOf<Duration>()

    override val kind = ChannelKind.Fake
    override val state: StateFlow<ChannelState> = MutableStateFlow(ChannelState.Disconnected)
    override val raw: SharedFlow<String> = MutableSharedFlow()

    override suspend fun exec(line: String, timeout: Duration): String {
        val (off, len) = line.removePrefix("dsp pkg read 0 ").split(' ').let { it[0].toLong() to it[1].toInt() }
        val step = script.getOrNull(offsets.size)
        offsets += off
        lengths += len
        timeouts += timeout
        if (step != null) return step(off)
        val end = minOf(pkg.size.toLong(), off + len).toInt()
        return chunkLine(PKG_ID, off, pkg.copyOfRange(off.toInt(), end))
    }

    override suspend fun execThenDisconnect(line: String): String = error("쓰지 않음")
    override suspend fun waitReady(timeout: Duration): Boolean = error("쓰지 않음")
    override suspend fun disconnect() = error("쓰지 않음")
}

class PkgDownloadWorkflowTest {

    private lateinit var dir: File
    private lateinit var part: File
    private val pkg = ByteArray(1300) { (it % 251).toByte() }
    private val slot = PkgSlot(0, PkgState.Applied, PKG_ID, "1.0.0", pkg.size.toLong(), 1, sha256Hex(pkg))
    private val progress = mutableListOf<Pair<Long, Long>>()

    private val bad: Step = { "I (123) wifi: log line\n" }
    private val badCrc: Step = { off -> chunkLine(PKG_ID, off, ByteArray(512)).replaceFirst(Regex(" [0-9a-f]{8} "), " 00000000 ") }
    private val timeout: Step = { throw BridgeChannelException.Timeout("dsp pkg read") }
    private val drop: Step = { throw BridgeChannelException.Disconnected() }
    private val notConnected: Step = { throw BridgeChannelException.NotConnected() }
    private val otherId: Step = { off -> chunkLine(OTHER_ID, off, ByteArray(512) { 0x7f }) }
    private val wrongOff: Step = { off -> chunkLine(PKG_ID, off + 512, ByteArray(512) { 0x7f }) }
    private val empty: Step = { off -> chunkLine(PKG_ID, off, ByteArray(0)) }
    private val busy: Step = { "E busy\n" }

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("pkg-test").toFile()
        part = File(dir, "$PKG_ID.part")
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private suspend fun run(channel: IBridgeChannel, target: PkgSlot = slot, file: File = part) =
        PkgDownloadWorkflow.run(channel, target, file) { done, total -> progress += done to total }

    @Test
    fun `가짜 브리지에서 끝까지 받는다`() = runTest {
        val channel = FakeBridgeChannel(FakeDevice(FakeDeviceOptions(pkg = pkg)))
        assertTrue(channel.waitReady(10.seconds))

        val done = assertIs<PkgDownloadOutcome.Done>(run(channel))

        assertEquals(part, done.file)
        assertContentEquals(pkg, part.readBytes())
        assertEquals(pkg.size.toLong() to pkg.size.toLong(), progress.last())
    }

    @Test
    fun `512 바이트씩 요청하고 시간 제한은 5초이며 진행은 시작과 청크마다 알린다`() = runTest {
        val channel = ScriptedChannel(pkg)

        assertIs<PkgDownloadOutcome.Done>(run(channel))

        assertEquals(listOf(0L, 512L, 1024L), channel.offsets)
        assertEquals(listOf(512, 512, 276), channel.lengths)
        assertEquals(setOf(5.seconds), channel.timeouts.toSet())
        assertEquals(listOf(0L, 512L, 1024L, 1300L).map { it to 1300L }, progress)
    }

    @Test
    fun `끊기면 파일을 남기고 다시 실행하면 남은 오프셋부터 받는다`() = runTest {
        val first = run(ScriptedChannel(pkg, listOf(null, drop)))

        assertEquals(PkgDownloadOutcome.Disconnected, first)
        assertEquals(512, part.length())

        progress.clear()
        val again = ScriptedChannel(pkg)
        assertIs<PkgDownloadOutcome.Done>(run(again))

        assertEquals(listOf(512L, 1024L), again.offsets)
        assertEquals(512L to 1300L, progress.first())
        assertContentEquals(pkg, part.readBytes())
    }

    @Test
    fun `미연결은 재시도 없이 끊김으로 끝난다`() = runTest {
        val channel = ScriptedChannel(pkg, listOf(null, notConnected))

        assertEquals(PkgDownloadOutcome.Disconnected, run(channel))

        assertEquals(2, channel.offsets.size)
        assertEquals(512, part.length())
    }

    @Test
    fun `같은 오프셋에서 다른 id 가 두 번 연속이면 SlotChanged 이고 파일을 지운다`() = runTest {
        val channel = ScriptedChannel(pkg, listOf(null, otherId, otherId))

        assertEquals(PkgDownloadOutcome.SlotChanged, run(channel))

        assertEquals(listOf(0L, 512L, 512L), channel.offsets)
        assertFalse(part.exists())
    }

    @Test
    fun `다른 id 가 한 번이면 다시 읽어 계속하고 그 바이트는 버린다`() = runTest {
        val channel = ScriptedChannel(pkg, listOf(null, otherId))

        assertIs<PkgDownloadOutcome.Done>(run(channel))

        assertEquals(listOf(0L, 512L, 512L, 1024L), channel.offsets)
        assertContentEquals(pkg, part.readBytes())
    }

    @Test
    fun `다른 id 사이에 오류가 끼면 연속이 아니다`() = runTest {
        val channel = ScriptedChannel(pkg, listOf(otherId, bad, otherId))

        assertIs<PkgDownloadOutcome.Done>(run(channel))

        assertContentEquals(pkg, part.readBytes())
    }

    @Test
    fun `다른 id 와 오류가 번갈아 나와도 실패 3회에서 끝난다`() = runTest {
        val channel = ScriptedChannel(pkg, listOf(otherId, bad, otherId, bad, otherId, bad))

        assertIs<PkgDownloadOutcome.Failed>(run(channel))

        assertEquals(6, channel.offsets.size)
    }

    @Test
    fun `Bad 2회 뒤 정상이면 성공한다`() = runTest {
        val channel = ScriptedChannel(pkg, listOf(bad, badCrc))

        assertIs<PkgDownloadOutcome.Done>(run(channel))

        assertContentEquals(pkg, part.readBytes())
    }

    @Test
    fun `Bad 3회면 Failed 이고 파일을 남긴다`() = runTest {
        val channel = ScriptedChannel(pkg, listOf(null, bad, bad, bad))

        assertIs<PkgDownloadOutcome.Failed>(run(channel))

        assertEquals(4, channel.offsets.size)
        assertEquals(512, part.length())
    }

    @Test
    fun `성공하면 실패 횟수가 초기화된다`() = runTest {
        val channel = ScriptedChannel(pkg, listOf(bad, bad, null, bad, bad, null))

        assertIs<PkgDownloadOutcome.Done>(run(channel))
    }

    @Test
    fun `시간 초과도 재시도하고 3회면 Failed 다`() = runTest {
        assertIs<PkgDownloadOutcome.Done>(run(ScriptedChannel(pkg, listOf(timeout, timeout))))
        assertContentEquals(pkg, part.readBytes())

        part.delete()
        val channel = ScriptedChannel(pkg, listOf(null, timeout, timeout, timeout))
        assertIs<PkgDownloadOutcome.Failed>(run(channel))
        assertEquals(512, part.length())
    }

    @Test
    fun `오류 종류가 섞여도 연속 실패로 센다`() = runTest {
        assertIs<PkgDownloadOutcome.Failed>(run(ScriptedChannel(pkg, listOf(bad, timeout, wrongOff))))
    }

    @Test
    fun `응답 off 가 다르면 Bad 로 치고 그 바이트를 쓰지 않는다`() = runTest {
        assertIs<PkgDownloadOutcome.Done>(run(ScriptedChannel(pkg, listOf(wrongOff))))
        assertContentEquals(pkg, part.readBytes())

        part.delete()
        assertIs<PkgDownloadOutcome.Failed>(run(ScriptedChannel(pkg, listOf(wrongOff, wrongOff, wrongOff))))
        assertEquals(0, part.length())
    }

    @Test
    fun `off 와 id 가 함께 다르면 id 변경으로 보지 않는다`() = runTest {
        val both: Step = { off -> chunkLine(OTHER_ID, off + 512, ByteArray(512)) }

        assertIs<PkgDownloadOutcome.Failed>(run(ScriptedChannel(pkg, listOf(null, both, both, both))))

        assertEquals(512, part.length())
    }

    @Test
    fun `빈 청크는 Bad 다`() = runTest {
        assertIs<PkgDownloadOutcome.Failed>(run(ScriptedChannel(pkg, listOf(empty, empty, empty))))
    }

    @Test
    fun `크기를 넘기는 청크는 Bad 다`() = runTest {
        val tooLong: Step = { off -> chunkLine(PKG_ID, off, ByteArray(512)) }
        val channel = ScriptedChannel(pkg, listOf(null, null, tooLong, tooLong, tooLong))

        assertIs<PkgDownloadOutcome.Failed>(run(channel))

        assertEquals(1024, part.length())
    }

    @Test
    fun `E busy 는 Busy 이고 파일을 남긴다`() = runTest {
        assertEquals(PkgDownloadOutcome.Busy, run(ScriptedChannel(pkg, listOf(null, busy))))
        assertEquals(512, part.length())
    }

    @Test
    fun `다른 거절 코드는 Failed 이고 파일을 남긴다`() = runTest {
        val outcome = run(ScriptedChannel(pkg, listOf(null, { "E flash\n" })))

        assertEquals(PkgDownloadOutcome.Failed("flash"), outcome)
        assertEquals(512, part.length())
    }

    @Test
    fun `sha256 이 다르면 Corrupt 이고 파일을 지운다`() = runTest {
        val wrong = slot.copy(sha256 = "0".repeat(64))

        assertEquals(PkgDownloadOutcome.Corrupt, run(ScriptedChannel(pkg), wrong))

        assertFalse(part.exists())
    }

    @Test
    fun `이미 다 받은 파일은 읽지 않고 검사만 한다`() = runTest {
        part.writeBytes(pkg)
        val channel = ScriptedChannel(pkg)

        assertIs<PkgDownloadOutcome.Done>(run(channel))

        assertTrue(channel.offsets.isEmpty())
        assertEquals(listOf(1300L to 1300L), progress)
    }

    @Test
    fun `시작 때 파일이 크기보다 길면 비우고 처음부터 받는다`() = runTest {
        part.writeBytes(ByteArray(pkg.size + 1) { 0x55 })
        val channel = ScriptedChannel(pkg)

        assertIs<PkgDownloadOutcome.Done>(run(channel))

        assertEquals(0L, channel.offsets.first())
        assertEquals(0L to 1300L, progress.first())
        assertContentEquals(pkg, part.readBytes())
    }

    @Test
    fun `저장 폴더가 없으면 만든다`() = runTest {
        val nested = File(dir, "pkg/$PKG_ID.part")

        assertIs<PkgDownloadOutcome.Done>(run(ScriptedChannel(pkg), file = nested))

        assertContentEquals(pkg, nested.readBytes())
    }

    @Test
    fun `파일을 쓸 수 없으면 죽지 않고 DiskFailed 다`() = runTest {
        val blocker = File(dir, "blocker").apply { writeText("x") }

        assertEquals(PkgDownloadOutcome.DiskFailed, run(ScriptedChannel(pkg), file = File(blocker, "$PKG_ID.part")))

        assertEquals("x", blocker.readText())
    }

    @Test
    fun `덧붙이다 실패하면 마지막 정상 길이로 되돌린다`() {
        part.writeBytes(ByteArray(512))
        val failing = object : RandomAccessFile(part, "rw") {
            override fun write(b: ByteArray) {
                super.write(b, 0, 3)
                throw IOException("no space")
            }
        }

        assertFailsWith<IOException> { failing.use { PkgDownloadWorkflow.appendWhole(it, 512, ByteArray(512)) } }

        assertEquals(512, part.length())
    }

    @Test
    fun `취소하면 CancellationException 이 나가고 파일이 남는다`() = runTest {
        val reached = CompletableDeferred<Unit>()
        val channel = ScriptedChannel(
            pkg,
            listOf(null, {
                reached.complete(Unit)
                awaitCancellation()
            }),
        )
        var thrown: Throwable? = null
        val job = launch {
            try {
                run(channel)
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }

        reached.await()
        job.cancelAndJoin()

        assertIs<CancellationException>(thrown)
        assertEquals(512, part.length())
    }
}
