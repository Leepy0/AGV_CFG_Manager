package kr.agv.cfgmanager.ui.boards

import kr.agv.cfgmanager.session.BoardRow
import kotlin.test.Test
import kotlin.test.assertEquals

class BoardsUiTest {

    private val row = BoardRow("AA:01", "AGVBR-01-A1B2", -48, lastSeenMs = 0)

    @Test
    fun `처음 상태는 Empty 이고 스캔 중에 목록이 없으면 Loading`() {
        assertEquals(BoardsView.Empty, BoardsUi().view)
        assertEquals(BoardsView.Loading, BoardsUi(scanState = ScanState.Scanning).view)
    }

    @Test
    fun `목록이 있으면 오류가 있어도 Content, 목록이 없고 오류면 Error`() {
        assertEquals(BoardsView.Content, BoardsUi(scanState = ScanState.Done, rows = listOf(row)).view)
        assertEquals(
            BoardsView.Content,
            BoardsUi(scanState = ScanState.Done, rows = listOf(row), error = ScanError.BluetoothOff).view,
        )
        assertEquals(BoardsView.Error, BoardsUi(scanState = ScanState.Done, error = ScanError.BluetoothOff).view)
    }

    @Test
    fun `끝났는데 보드가 없으면 Empty`() {
        assertEquals(BoardsView.Empty, BoardsUi(scanState = ScanState.Done).view)
    }

    @Test
    fun `권한 오류 문구는 무엇과 왜를 말한다`() {
        val text = ScanErrorText.title(ScanError.Permission(listOf("x")))
        assertEquals("근처 기기(블루투스) 권한이 없어 보드를 찾을 수 없습니다.", text)
    }
}
