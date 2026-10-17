package kr.agv.cfgmanager.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateDownloaderTest {

    private lateinit var dir: File
    private val payload = ByteArray(100_000) { (it % 251).toByte() }

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("update-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun release(sha: String? = sha256Hex(payload), size: Long? = payload.size.toLong()) = LatestRelease(
        version = UpdateVersion(0, 2, 0),
        versionCode = 200,
        apkUrl = UpdateSpec.apkUrlOf(UpdateVersion(0, 2, 0)),
        sha256 = sha,
        size = size,
        minSdk = 26,
        notes = null,
        page = UpdateSpec.tagPageOf(UpdateVersion(0, 2, 0)),
        source = LatestRelease.Source.Manifest,
    )

    private fun downloader(http: UpdateHttp) = UpdateDownloader(http, dir, dispatcher = Dispatchers.IO)

    @Test
    fun `받아서 sha256 을 맞추면 최종 이름으로 바꾸고 partial 은 남기지 않는다`() = runTest {
        val http = UpdateHttp { _, _, _ -> okReply(payload) }
        val apk = downloader(http).download(release()) { _, _ -> }

        assertEquals(File(dir, "AgvCfgManager.apk").canonicalPath, apk.canonicalPath)
        assertContentEquals(payload, apk.readBytes())
        assertFalse(File(dir, "AgvCfgManager.apk.partial").exists())
    }

    @Test
    fun `진행률은 0 에서 시작해 전체 크기로 끝나고 줄지 않는다`() = runTest {
        val http = UpdateHttp { _, _, _ -> okReply(payload) }
        val seen = mutableListOf<Pair<Long, Long>>()
        downloader(http).download(release()) { received, total -> seen += received to total }

        assertTrue(seen.size > 2, "여러 번 알려야 한다: ${seen.size}")
        assertEquals(0L to payload.size.toLong(), seen.first())
        assertEquals(payload.size.toLong() to payload.size.toLong(), seen.last())
        assertTrue(seen.zipWithNext().all { (a, b) -> b.first >= a.first })
        assertTrue(seen.all { (received, total) -> received <= total && total == payload.size.toLong() })
    }

    @Test
    fun `진행률 퍼센트를 계산하고 전체를 모르면 null 이다`() {
        val known = UpdateState.Downloading(release(), received = 25_000L, total = 100_000L)
        assertEquals(25, known.percent)
        val unknown = UpdateState.Downloading(release(), received = 25_000L, total = -1L)
        assertEquals(null, unknown.percent)
    }

    @Test
    fun `전체 크기를 모르면 응답의 Content-Length 를 쓰고 없으면 -1 이다`() = runTest {
        val http = UpdateHttp { _, _, _ -> okReply(payload, contentLength = -1L) }
        val seen = mutableListOf<Long>()
        downloader(http).download(release(size = null)) { _, total -> seen += total }
        assertEquals(-1L, seen.first())
    }

    @Test
    fun `sha256 이 틀리면 실패하고 파일을 남기지 않는다`() = runTest {
        val http = UpdateHttp { _, _, _ -> okReply(payload) }
        assertFailsWith<UpdateFailure> {
            downloader(http).download(release(sha = SAMPLE_SHA)) { _, _ -> }
        }
        assertFalse(File(dir, "AgvCfgManager.apk").exists())
        assertFalse(File(dir, "AgvCfgManager.apk.partial").exists())
    }

    @Test
    fun `크기가 맞지 않으면 실패한다`() = runTest {
        val http = UpdateHttp { _, _, _ -> okReply(payload) }
        assertFailsWith<UpdateFailure> {
            downloader(http).download(release(size = payload.size + 1L)) { _, _ -> }
        }
        assertFalse(File(dir, "AgvCfgManager.apk.partial").exists())
    }

    @Test
    fun `응답이 2xx 가 아니면 실패한다`() = runTest {
        val http = UpdateHttp { _, _, _ -> HttpReply(404, null, 0L, java.io.ByteArrayInputStream(ByteArray(0))) }
        assertFailsWith<UpdateFailure> { downloader(http).download(release()) { _, _ -> } }
    }

    @Test
    fun `받는 도중 연결이 끊기면 partial 을 지운다`() = runTest {
        val broken = object : InputStream() {
            private var calls = 0
            override fun read(): Int = throw IOException("reset")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                calls++
                if (calls > 1) throw IOException("reset")
                System.arraycopy(payload, 0, b, off, 1_000)
                return 1_000
            }
        }
        val http = UpdateHttp { _, _, _ -> HttpReply(200, null, payload.size.toLong(), broken) }
        assertFailsWith<IOException> { downloader(http).download(release()) { _, _ -> } }
        assertFalse(File(dir, "AgvCfgManager.apk.partial").exists())
    }

    @Test
    fun `sha256 이 없는 릴리스는 받지 않는다`() = runTest {
        var called = false
        val http = UpdateHttp { _, _, _ ->
            called = true
            okReply(payload)
        }
        assertFailsWith<UpdateFailure> { downloader(http).download(release(sha = null)) { _, _ -> } }
        assertFalse(called)
    }

    @Test
    fun `이전에 남은 partial 은 새로 받을 때 지운다`() = runTest {
        File(dir, "AgvCfgManager.apk.partial").writeText("stale")
        val http = UpdateHttp { _, _, _ -> okReply(payload) }
        val apk = downloader(http).download(release()) { _, _ -> }
        assertContentEquals(payload, apk.readBytes())
    }
}
