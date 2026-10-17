package kr.agv.cfgmanager.core.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateCheckerTest {

    private val manifest = """
        {"version":"0.2.0","versionCode":200,"sha256":"$SAMPLE_SHA","size":5,"notes":"- 새 기능"}
    """.trimIndent()

    @Test
    fun `매니페스트가 있으면 그것을 쓰고 리다이렉트를 따라간다`() = runTest {
        val http = ScriptedHttp(mapOf(UpdateSpec.MANIFEST_URL to { _ -> textReply(manifest) }))
        val release = UpdateChecker(http, dispatcher = kotlinx.coroutines.Dispatchers.Unconfined).latest()

        assertEquals(LatestRelease.Source.Manifest, release.source)
        assertEquals("0.2.0", release.version.toString())
        assertEquals(SAMPLE_SHA, release.sha256)
        assertEquals(listOf(UpdateSpec.MANIFEST_URL to true), http.calls)
    }

    @Test
    fun `매니페스트가 404 이면 리다이렉트를 따라가지 않고 Location 태그에서 버전만 얻는다`() = runTest {
        val http = ScriptedHttp(
            mapOf(
                UpdateSpec.MANIFEST_URL to { _ -> statusReply(404) },
                UpdateSpec.LATEST_PAGE_URL to { _ ->
                    statusReply(302, "https://github.com/Leepy0/AGV_CFG_Manager/releases/tag/v0.3.1")
                },
            ),
        )
        val release = UpdateChecker(http, dispatcher = kotlinx.coroutines.Dispatchers.Unconfined).latest()

        assertEquals(LatestRelease.Source.TagFallback, release.source)
        assertEquals("0.3.1", release.version.toString())
        assertEquals(301, release.versionCode)
        assertNull(release.sha256)
        assertEquals("https://github.com/Leepy0/AGV_CFG_Manager/releases/download/v0.3.1/AgvCfgManager.apk", release.apkUrl)
        assertEquals(false, release.installableInApp)
        // 폴백은 리다이렉트를 따라가지 않아야 태그가 보인다
        assertEquals(UpdateSpec.LATEST_PAGE_URL to false, http.calls.last())
    }

    @Test
    fun `매니페스트 JSON 이 깨졌어도 폴백으로 버전을 얻는다`() = runTest {
        val http = ScriptedHttp(
            mapOf(
                UpdateSpec.MANIFEST_URL to { _ -> textReply("{not json") },
                UpdateSpec.LATEST_PAGE_URL to { _ -> statusReply(302, "https://github.com/x/y/releases/tag/v0.2.0") },
            ),
        )
        assertEquals(LatestRelease.Source.TagFallback, UpdateChecker(http, dispatcher = kotlinx.coroutines.Dispatchers.Unconfined).latest().source)
    }

    @Test
    fun `매니페스트 연결 실패도 폴백으로 넘어간다`() = runTest {
        val http = ScriptedHttp(
            mapOf(
                UpdateSpec.MANIFEST_URL to { _ -> throw IOException("timeout") },
                UpdateSpec.LATEST_PAGE_URL to { _ -> statusReply(302, "https://github.com/x/y/releases/tag/v0.2.0") },
            ),
        )
        assertEquals("0.2.0", UpdateChecker(http, dispatcher = kotlinx.coroutines.Dispatchers.Unconfined).latest().version.toString())
    }

    @Test
    fun `둘 다 실패하면 한국어 실패 메시지를 준다`() = runTest {
        val http = ScriptedHttp(
            mapOf(
                UpdateSpec.MANIFEST_URL to { _ -> statusReply(500) },
                UpdateSpec.LATEST_PAGE_URL to { _ -> statusReply(200) },
            ),
        )
        val failure = assertFailsWith<UpdateFailure> {
            UpdateChecker(http, dispatcher = kotlinx.coroutines.Dispatchers.Unconfined).latest()
        }
        assertTrue(failure.message!!.contains("인터넷 연결"))
        assertEquals(1, failure.suppressed.size, "매니페스트 실패 원인도 남긴다")
    }

    @Test
    fun `Location 에 태그가 없으면 폴백도 실패다`() = runTest {
        val http = ScriptedHttp(
            mapOf(
                UpdateSpec.MANIFEST_URL to { _ -> statusReply(404) },
                UpdateSpec.LATEST_PAGE_URL to { _ -> statusReply(302, "https://github.com/Leepy0/AGV_CFG_Manager/releases") },
            ),
        )
        assertFailsWith<UpdateFailure> { UpdateChecker(http, dispatcher = kotlinx.coroutines.Dispatchers.Unconfined).latest() }
    }

    @Test
    fun `Location 이 없는 302 는 실패다`() = runTest {
        val http = ScriptedHttp(
            mapOf(
                UpdateSpec.MANIFEST_URL to { _ -> statusReply(404) },
                UpdateSpec.LATEST_PAGE_URL to { _ -> statusReply(302, null) },
            ),
        )
        assertFailsWith<UpdateFailure> { UpdateChecker(http, dispatcher = kotlinx.coroutines.Dispatchers.Unconfined).latest() }
    }

    @Test
    fun `api github com 은 한 번도 부르지 않는다`() = runTest {
        val http = ScriptedHttp(mapOf(UpdateSpec.MANIFEST_URL to { _ -> throw IOException("down") }))
        runCatching { UpdateChecker(http, dispatcher = kotlinx.coroutines.Dispatchers.Unconfined).latest() }
        assertTrue(http.calls.none { it.first.contains("api.github.com") })
        assertTrue(http.calls.all { it.first.startsWith("https://github.com/") })
    }

    @Test
    fun `취소는 실패로 삼키지 않고 다시 던진다`() = runTest {
        val http = ScriptedHttp(mapOf(UpdateSpec.MANIFEST_URL to { _ -> throw CancellationException("stop") }))
        assertFailsWith<CancellationException> {
            UpdateChecker(http, dispatcher = kotlinx.coroutines.Dispatchers.Unconfined).latest()
        }
    }
}
