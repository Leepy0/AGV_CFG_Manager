package kr.agv.cfgmanager

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import kr.agv.cfgmanager.data.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.File

/**
 * 화면 캡처 투어. CI 에뮬레이터에서 가짜 브리지로 앱을 한 바퀴 돌며 화면을 PNG 로 남긴다
 * (`.github/workflows/screenshots.yml`). 결과 파일은 앱 내부 저장소 `files/screenshots/` 에 쌓이고,
 * 워크플로가 `adb run-as` 로 가져와 artifact 로 올린다. 검증이 아니라 눈으로 보기 위한 것이다.
 *
 * 연결 시트나 대화상자가 떠 있으면 창(root)이 둘 이상이다. 창마다 `이름-windowN.png` 로 따로 남긴다.
 */
class ScreenshotTourTest {

    private val app: AgvApp get() = ApplicationProvider.getApplicationContext()

    @get:Rule(order = 0)
    val prepare = object : ExternalResource() {
        override fun before() {
            app.container.settings.update { it.copy(fakeBridge = true, themeMode = ThemeMode.Light) }
            File(app.filesDir, DIR).deleteRecursively()
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun tour() {
        // 가짜 보드 중 가장 센 새 보드(-48dBm)가 뚜렷하므로 연결 시트가 자동으로 열린다 (자동 선택).
        waitForText("AGVBR-01")
        waitForText("BLE 비밀번호", timeoutMs = 20_000)
        shot("01-boards-autopick")

        compose.onAllNodesWithText("AGVBR-01", substring = true)[0].performClick()
        waitForText("적용할 값")
        shot("02-apply-summary")

        compose.onNodeWithText("세부 값 편집").performScrollTo().performClick()
        compose.waitForIdle()
        shot("03-apply-details")

        tab("진단")
        shot("04-diagnostics")
        tab("이력")
        shot("05-history")
        tab("더보기")
        shot("06-more")
        tab("세팅")
        shot("07-work-again")

        app.container.settings.update { it.copy(themeMode = ThemeMode.Dark) }
        compose.waitForIdle()
        shot("08-dark-work")
        tab("더보기")
        shot("09-dark-more")
    }

    private fun tab(label: String) {
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and hasText(label)).performClick()
        compose.waitForIdle()
    }

    private fun waitForText(text: String, timeoutMs: Long = 30_000) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val roots = compose.onAllNodes(isRoot())
        val count = roots.fetchSemanticsNodes().size
        for (index in 0 until count) {
            val bitmap = roots[index].captureToImage().asAndroidBitmap()
            save(if (index == 0) name else "$name-window$index", bitmap)
        }
    }

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File(app.filesDir, DIR).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val DIR = "screenshots"
    }
}
