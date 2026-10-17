package kr.agv.cfgmanager.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UpdateUrlsTest {

    private val all = listOf(
        UpdateSpec.REPO_URL,
        UpdateSpec.MANIFEST_URL,
        UpdateSpec.LATEST_PAGE_URL,
        UpdateSpec.BROWSER_APK_URL,
        UpdateSpec.apkUrlOf(UpdateVersion(0, 1, 0)),
        UpdateSpec.tagPageOf(UpdateVersion(0, 1, 0)),
    )

    @Test
    fun `업데이트 주소는 전부 github com 이고 api github com 을 쓰지 않는다`() {
        all.forEach { url ->
            assertFalse(url.contains("api.github.com"), url)
            assertFalse(url.startsWith("http://"), url)
        }
    }

    @Test
    fun `매니페스트와 APK 주소는 12장 형식이다`() {
        assertEquals("https://github.com/Leepy0/AGV_CFG_Manager/releases/latest/download/AgvCfgManager.json", UpdateSpec.MANIFEST_URL)
        assertEquals("https://github.com/Leepy0/AGV_CFG_Manager/releases/download/v0.1.0/AgvCfgManager.apk", UpdateSpec.apkUrlOf(UpdateVersion(0, 1, 0)))
        assertEquals("https://github.com/Leepy0/AGV_CFG_Manager/releases/latest/download/AgvCfgManager.apk", UpdateSpec.BROWSER_APK_URL)
    }
}
