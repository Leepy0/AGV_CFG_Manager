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

    private fun ui(vararg rows: BoardRow) = BoardsUi(scanState = ScanState.Scanning, rows = rows.toList(), nowMs = 0L)

    
    @Test
    fun `가장 센 새 보드가 -50 이상이고 15dB 이상 앞서면 자동 후보가 된다`() {
        val near = BoardRow("AA:01", "AGVBR-01-A1B2", -45, lastSeenMs = 0)
        val far = BoardRow("AA:02", "AGVBR-01-C3D4", -63, lastSeenMs = 0)
        assertEquals(near, ui(near, far).autoPickCandidate())
    }

    
    @Test
    fun `신호가 -50 보다 약하면 자동 후보가 없다`() {
        assertEquals(null, ui(BoardRow("AA:01", "AGVBR-01-A1B2", -55, lastSeenMs = 0)).autoPickCandidate())
    }

    
    @Test
    fun `다른 보드가 15dB 안에 있으면 애매하므로 자동 후보가 없다`() {
        val a = BoardRow("AA:01", "AGVBR-01-A1B2", -45, lastSeenMs = 0)
        val b = BoardRow("AA:02", "AGVBR-01-C3D4", -55, lastSeenMs = 0)
        assertEquals(null, ui(a, b).autoPickCandidate())
    }

    
    @Test
    fun `이미 설정된 보드가 가까이 있으면 새 보드가 있어도 자동 후보가 없다`() {
        val fresh = BoardRow("AA:01", "AGVBR-01-A1B2", -45, lastSeenMs = 0)
        val configured = BoardRow("AA:03", "AGVBR-12-E5F6", -50, lastSeenMs = 0)
        assertEquals(null, ui(fresh, configured).autoPickCandidate())
        val farConfigured = configured.copy(rssi = -70)
        assertEquals(fresh, ui(fresh, farConfigured).autoPickCandidate())
    }

    
    @Test
    fun `USB 행은 자동 후보에서 빠진다`() {
        assertEquals(null, ui(BoardRow("usb:/dev/bus/usb/001/002", "USB 콘솔", 0, lastSeenMs = 0)).autoPickCandidate())
    }
}
