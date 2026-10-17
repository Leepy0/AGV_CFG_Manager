package kr.agv.cfgmanager.ui.boards

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kr.agv.cfgmanager.session.BoardRow
import kr.agv.cfgmanager.ui.theme.AgvCfgManagerTheme
import org.junit.Rule
import org.junit.Test

/**
 * 보드 탭 Compose 테스트 (docs/DEVELOPMENT_PLAN.md 10.3). 에뮬레이터에서만 실행한다.
 * 이 Phase 에서는 작성만 하고 실행하지 않는다 (실기 · 에뮬레이터 없음).
 */
class BoardsContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val row = BoardRow(
        address = "00:AB:CD:00:00:01",
        name = "AGVBR-01-A1B2",
        rssi = -48,
        lastSeenMs = 0L,
    )

    private fun render(ui: BoardsUi, onRowClick: (BoardRow) -> Unit = {}) {
        rule.setContent {
            AgvCfgManagerTheme {
                BoardsContent(
                    ui = ui,
                    onPrimary = {},
                    onStop = {},
                    onRowClick = onRowClick,
                    onErrorAction = {},
                    onOpenAppSettings = {},
                    onPasswordChange = {},
                    onConnect = {},
                    onCancelConnect = {},
                    onSheetDismiss = {},
                )
            }
        }
    }

    @Test
    fun 스캔이_끝났는데_보드가_없으면_빈_상태가_안내를_보인다() {
        render(BoardsUi(scanState = ScanState.Done))
        rule.onNodeWithText("보이는 보드가 없습니다").assertIsDisplayed()
    }

    @Test
    fun 보드_목록에_이름과_설정_없음_표식이_보인다() {
        render(BoardsUi(scanState = ScanState.Done, rows = listOf(row), nowMs = 0L))
        rule.onNodeWithText("AGVBR-01-A1B2").assertIsDisplayed()
        rule.onNodeWithText("설정 없음(01)", substring = true).assertIsDisplayed()
    }

    @Test
    fun 보드를_누르면_행_클릭이_전달된다() {
        var clicked: BoardRow? = null
        render(BoardsUi(scanState = ScanState.Done, rows = listOf(row), nowMs = 0L)) { clicked = it }
        rule.onNodeWithText("AGVBR-01-A1B2").performClick()
        rule.waitForIdle()
        check(clicked == row)
    }

    @Test
    fun 권한이_없으면_오류_상태가_권한_허용_버튼을_보인다() {
        render(BoardsUi(scanState = ScanState.Done, error = ScanError.Permission(listOf("x"))))
        rule.onNodeWithText("권한 허용").assertIsDisplayed()
        rule.onNodeWithText("설정 열기").assertIsDisplayed()
    }
}
