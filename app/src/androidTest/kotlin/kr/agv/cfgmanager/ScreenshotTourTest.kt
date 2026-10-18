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
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * 한 단계가 실패해도(글자를 못 찾는 등) 그 자리의 화면을 남기고 다음 단계로 넘어간다.
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
        // 세팅 1단계: 가짜 보드 중 가장 센 새 보드가 뚜렷하므로 연결 시트가 자동으로 열린다 (자동 선택).
        step("01-boards-autopick") {
            waitForText("AGVBR-01")
            waitForText("BLE 비밀번호", timeoutMs = 20_000)
        }

        // 세팅 2단계: 연결 · 확인
        step("02-apply-summary") {
            compose.onAllNodesWithText("AGVBR-01", substring = true)[0].performClick()
            waitForText("적용할 값")
        }
        step("03-apply-details") {
            compose.onNodeWithText("세부 값 편집").performScrollTo().performClick()
            compose.waitForIdle()
        }
        step("03b-details-advanced") {
            compose.onNodeWithText("고급 · RFID", substring = true).performScrollTo()
            compose.waitForIdle()
        }
        compose.onNodeWithText("세부 값 편집").performScrollTo().performClick()

        // 세팅 3단계: 적용 진행 · 결과
        step("04-apply-progress") {
            compose.onNodeWithText("적용 (저장 후 재부팅)").performClick()
            waitForText("적용 중")
        }
        step("05-apply-result") {
            waitForAnyText("장치 이름", "다시 시도", timeoutMs = 120_000)
        }
        step("06-after-result") {
            compose.onNodeWithText("확인").performClick()
            compose.waitForIdle()
        }

        // 진단 · 이력
        step("07-diagnostics") { tab("진단") }
        step("07b-diagnostics-bottom") {
            compose.onNodeWithText("공장 초기화…").performScrollTo()
            compose.waitForIdle()
        }
        step("08-history") { tab("이력") }
        step("09-history-detail") {
            compose.onAllNodesWithText("192.168.10.101", substring = true)[0].performClick()
            compose.waitForIdle()
        }
        runCatching { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() } }
        compose.waitForIdle()

        // 더보기와 보조 화면
        step("10-more") { tab("더보기") }
        sub("11-profiles", "프로필") {
            step("12-profile-edit") {
                compose.onAllNodesWithText("본사")[0].performClick()
                compose.waitForIdle()
            }
            step("12b-profile-edit-bottom") {
                compose.onNodeWithText("BLE 비밀번호", substring = true).performScrollTo()
                compose.waitForIdle()
            }
            back()
        }
        sub("13-library", "SSID 라이브러리")
        sub("14-export", "내보내기")
        sub("15-session-summary", "연속 세팅 요약")
        sub("16-app-settings", "설정")
        sub("17-about", "정보")

        // 다크
        app.container.settings.update { it.copy(themeMode = ThemeMode.Dark) }
        compose.waitForIdle()
        step("18-dark-more") { }
        step("19-dark-work") { tab("세팅") }
        step("20-dark-diagnostics") { tab("진단") }
    }

    private fun sub(name: String, rowText: String, inside: () -> Unit = {}) {
        step(name) {
            compose.onNodeWithText(rowText).performClick()
            compose.waitForIdle()
        }
        runCatching { inside() }
        back()
    }

    private fun back() {
        runCatching {
            compose.onNodeWithContentDescription("뒤로").performClick()
            compose.waitForIdle()
        }
    }

    private fun step(name: String, action: () -> Unit) {
        runCatching { action() }
        shot(name)
    }

    private fun tab(label: String) {
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and hasText(label)).performClick()
        compose.waitForIdle()
    }

    private fun waitForText(text: String, timeoutMs: Long = 30_000) = waitForAnyText(text, timeoutMs = timeoutMs)

    private fun waitForAnyText(vararg texts: String, timeoutMs: Long = 30_000) {
        compose.waitUntil(timeoutMs) {
            texts.any { compose.onAllNodesWithText(it, substring = true).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        runCatching {
            compose.waitForIdle()
            val roots = compose.onAllNodes(isRoot())
            val count = roots.fetchSemanticsNodes().size
            for (index in 0 until count) {
                val bitmap = roots[index].captureToImage().asAndroidBitmap()
                save(if (index == 0) name else "$name-window$index", bitmap)
            }
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
