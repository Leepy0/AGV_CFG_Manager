package kr.agv.cfgmanager.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UpdateManifestTest {

    private val full = """
        {
          "version": "0.2.0",
          "versionCode": 200,
          "file": "AgvCfgManager.apk",
          "sha256": "${SAMPLE_SHA.uppercase()}",
          "size": 5120,
          "minSdk": 26,
          "published": "2026-10-17T03:00:00Z",
          "page": "https://github.com/Leepy0/AGV_CFG_Manager/releases/tag/v0.2.0",
          "notes": "- 진단 보강\n- 정보 화면",
          "future": { "field": [1, 2, 3] },
          "another_future_field": true
        }
    """.trimIndent()

    @Test
    fun `12장 형식을 읽고 모르는 필드는 무시한다`() {
        val release = UpdateManifest.parse(full).toRelease()
        assertEquals("0.2.0", release.version.toString())
        assertEquals(200, release.versionCode)
        assertEquals("https://github.com/Leepy0/AGV_CFG_Manager/releases/download/v0.2.0/AgvCfgManager.apk", release.apkUrl)
        assertEquals(5120L, release.size)
        assertEquals(26, release.minSdk)
        assertEquals("- 진단 보강\n- 정보 화면", release.notes)
        assertEquals(LatestRelease.Source.Manifest, release.source)
        assertEquals(true, release.installableInApp)
    }

    @Test
    fun `sha256 은 소문자로 맞춘다`() {
        assertEquals(SAMPLE_SHA, UpdateManifest.parse(full).toRelease().sha256)
    }

    @Test
    fun `versionCode 가 없으면 버전에서 계산한다`() {
        val release = UpdateManifest.parse("""{"version":"v1.2.3","sha256":"$SAMPLE_SHA"}""").toRelease()
        assertEquals(10_203, release.versionCode)
        assertEquals("1.2.3", release.version.toString())
    }

    @Test
    fun `file 이 없으면 APK 기본 이름을 쓰고 sha 가 없으면 브라우저 안내만 한다`() {
        val release = UpdateManifest.parse("""{"version":"0.1.0"}""").toRelease()
        assertEquals("AgvCfgManager.apk", release.apkUrl.substringAfterLast('/'))
        assertNull(release.sha256)
        assertEquals(false, release.installableInApp)
    }

    @Test
    fun `version 이 없으면 매니페스트를 읽지 못한 것이다`() {
        assertFailsWith<UpdateFailure> { UpdateManifest.parse("""{"sha256":"$SAMPLE_SHA"}""") }
    }

    @Test
    fun `JSON 이 아니면 매니페스트를 읽지 못한 것이다`() {
        assertFailsWith<UpdateFailure> { UpdateManifest.parse("<html>error</html>") }
    }

    @Test
    fun `버전 형식이 틀리면 거부한다`() {
        assertFailsWith<UpdateFailure> { UpdateManifest.parse("""{"version":"latest"}""").toRelease() }
    }

    @Test
    fun `sha256 형식이 틀리면 거부한다`() {
        assertFailsWith<UpdateFailure> { UpdateManifest.parse("""{"version":"0.1.0","sha256":"abc"}""").toRelease() }
    }

    @Test
    fun `파일 이름에 경로가 들어 있으면 거부한다`() {
        assertFailsWith<UpdateFailure> {
            UpdateManifest.parse("""{"version":"0.1.0","file":"../AgvCfgManager.apk"}""").toRelease()
        }
    }

    @Test
    fun `크기가 0 이하이면 거부한다`() {
        assertFailsWith<UpdateFailure> { UpdateManifest.parse("""{"version":"0.1.0","size":0}""").toRelease() }
    }
}
