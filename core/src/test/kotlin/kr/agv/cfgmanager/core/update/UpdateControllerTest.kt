package kr.agv.cfgmanager.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateControllerTest {

    private lateinit var dir: File
    private val payload = ByteArray(40_000) { (it % 97).toByte() }
    private val payloadSha = sha256Hex(payload)

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("update-ctl").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun manifestJson(code: Int = 110, version: String = "0.1.1", sha: String = payloadSha, minSdk: Int = 26) =
        """{"version":"$version","versionCode":$code,"sha256":"$sha","size":${payload.size},"minSdk":$minSdk}"""

    private fun controller(
        http: UpdateHttp,
        channel: String = "release",
        currentCode: Int = 100,
        deviceSdk: Int = 34,
        journal: (String) -> Unit = {},
    ) = UpdateController(
        checker = UpdateChecker(http, dispatcher = Dispatchers.IO),
        downloader = UpdateDownloader(http, dir, dispatcher = Dispatchers.IO),
        build = BuildInfo(versionCode = currentCode, channel = channel, deviceSdk = deviceSdk),
        journal = journal,
    )

    /** APK 몸체. 읽을 때마다 컨트롤러 상태를 기록하고, [cancelAt] 번째 읽기에서 [onCancel] 을 부른다. */
    private fun observedBody(
        ctl: () -> UpdateController,
        seen: MutableList<UpdateState>,
        cancelAt: Int = -1,
        onCancel: () -> Unit = {},
    ): InputStream {
        val inner = ByteArrayInputStream(payload)
        return object : InputStream() {
            private var reads = 0
            override fun read(): Int = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                reads++
                seen += ctl().state.value
                if (reads == cancelAt) onCancel()
                return inner.read(b, off, minOf(len, 8_192))
            }
        }
    }

    @Test
    fun `새 버전이면 업데이트 있음 상태가 된다`() = runTest {
        val http = ScriptedHttp(mapOf(UpdateSpec.MANIFEST_URL to { _ -> textReply(manifestJson()) }))
        val available = assertIs<UpdateState.Available>(controller(http).check(silent = false))

        assertEquals("0.1.1", available.release.version.toString())
        assertTrue(available.installableHere)
        assertFalse(available.overwriteNote)
    }

    @Test
    fun `같은 버전이면 최신이다`() = runTest {
        val http = ScriptedHttp(mapOf(UpdateSpec.MANIFEST_URL to { _ -> textReply(manifestJson(code = 100, version = "0.1.0")) }))
        assertEquals(UpdateState.UpToDate(UpdateVersion(0, 1, 0)), controller(http).check(silent = false))
    }

    @Test
    fun `dev 빌드는 업데이트 있음이고 정식으로 덮어 설치된다는 안내를 붙인다`() = runTest {
        val http = ScriptedHttp(mapOf(UpdateSpec.MANIFEST_URL to { _ -> textReply(manifestJson()) }))
        val available = assertIs<UpdateState.Available>(controller(http, channel = "dev").check(silent = false))
        assertTrue(available.overwriteNote)
    }

    @Test
    fun `이 휴대폰의 Android 가 낮으면 설치할 수 없다고 표시한다`() = runTest {
        val http = ScriptedHttp(mapOf(UpdateSpec.MANIFEST_URL to { _ -> textReply(manifestJson(minSdk = 35)) }))
        val available = assertIs<UpdateState.Available>(controller(http, deviceSdk = 33).check(silent = false))
        assertFalse(available.installableHere)
    }

    @Test
    fun `자동 확인이 실패하면 화면에는 아무것도 내지 않고 이전 상태로 둔다`() = runTest {
        val ctl = controller(ScriptedHttp(mapOf()))
        assertEquals(UpdateState.Idle, ctl.check(silent = true))
        assertEquals(UpdateState.Idle, ctl.state.value)
    }

    @Test
    fun `직접 확인이 실패하면 오류 상태와 메시지를 준다`() = runTest {
        val failed = assertIs<UpdateState.Failed>(controller(ScriptedHttp(mapOf())).check(silent = false))
        assertTrue(failed.message.contains("인터넷"))
        assertNull(failed.release)
    }

    @Test
    fun `확인 실패 기록은 원인 종류만 남기고 주소는 남기지 않는다`() = runTest {
        val lines = mutableListOf<String>()
        val http = ScriptedHttp(mapOf(UpdateSpec.MANIFEST_URL to { _ -> throw IOException("refused") }))
        controller(http, journal = { lines += it }).check(silent = false)
        assertTrue(lines.any { it.contains("확인 실패") })
        assertTrue(lines.none { it.contains("https://") })
    }

    @Test
    fun `받기는 진행률을 보이고 검증 뒤 Ready 가 된다`() = runTest {
        lateinit var ctl: UpdateController
        val seen = mutableListOf<UpdateState>()
        val http = ScriptedHttp(
            mapOf(
                UpdateSpec.MANIFEST_URL to { _ -> textReply(manifestJson()) },
                UpdateSpec.apkUrlOf(UpdateVersion(0, 1, 1)) to { _ ->
                    HttpReply(200, null, payload.size.toLong(), observedBody({ ctl }, seen))
                },
            ),
        )
        ctl = controller(http)
        ctl.check(silent = false)
        val ready = assertIs<UpdateState.Ready>(ctl.download())

        assertContentEquals(payload, ready.apk.readBytes())
        val percents = seen.filterIsInstance<UpdateState.Downloading>().mapNotNull { it.percent }
        assertTrue(percents.isNotEmpty(), "받는 중 상태가 보여야 한다")
        assertEquals(percents.sorted(), percents, "진행률은 줄지 않는다")
        assertTrue(percents.last() >= 50, "몸체를 절반 넘게 읽었을 때 진행률이 올라가야 한다: $percents")
    }

    @Test
    fun `받기 중 취소하면 Available 로 돌아가고 partial 을 지운다`() = runTest {
        lateinit var ctl: UpdateController
        lateinit var running: Job
        val seen = mutableListOf<UpdateState>()
        val http = ScriptedHttp(
            mapOf(
                UpdateSpec.MANIFEST_URL to { _ -> textReply(manifestJson()) },
                UpdateSpec.apkUrlOf(UpdateVersion(0, 1, 1)) to { _ ->
                    HttpReply(200, null, payload.size.toLong(), observedBody({ ctl }, seen, cancelAt = 2) { running.cancel() })
                },
            ),
        )
        ctl = controller(http)
        ctl.check(silent = false)
        running = launch { runCatching { ctl.download() } }
        running.join()

        assertIs<UpdateState.Available>(ctl.state.value)
        assertFalse(File(dir, "AgvCfgManager.apk.partial").exists())
        assertFalse(File(dir, "AgvCfgManager.apk").exists())
    }

    @Test
    fun `sha256 이 없는 릴리스는 받기를 시작하지 않는다`() = runTest {
        val http = ScriptedHttp(mapOf(UpdateSpec.MANIFEST_URL to { _ -> textReply("""{"version":"0.1.1","versionCode":110}""") }))
        val ctl = controller(http)
        val available = assertIs<UpdateState.Available>(ctl.check(silent = false))
        assertFalse(available.release.installableInApp)
        assertEquals(available, ctl.download())
        assertEquals(listOf(UpdateSpec.MANIFEST_URL to true), http.calls)
    }

    @Test
    fun `sha 가 틀리면 오류 상태가 되고 받은 릴리스 정보를 남긴다`() = runTest {
        val http = ScriptedHttp(
            mapOf(
                UpdateSpec.MANIFEST_URL to { _ -> textReply(manifestJson(sha = SAMPLE_SHA)) },
                UpdateSpec.apkUrlOf(UpdateVersion(0, 1, 1)) to { _ -> okReply(payload) },
            ),
        )
        val ctl = controller(http)
        ctl.check(silent = false)
        val failed = assertIs<UpdateState.Failed>(ctl.download())
        assertTrue(failed.message.contains("sha256"))
        assertEquals("0.1.1", failed.release?.version.toString())
    }

    @Test
    fun `확인하지 않은 상태에서 받기를 부르면 아무 일도 없다`() = runTest {
        assertEquals(UpdateState.Idle, controller(ScriptedHttp(mapOf())).download())
    }
}
