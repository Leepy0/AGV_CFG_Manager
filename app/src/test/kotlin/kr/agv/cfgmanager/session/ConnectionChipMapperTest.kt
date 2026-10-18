package kr.agv.cfgmanager.session

import kr.agv.cfgmanager.core.bridge.CfgState
import kr.agv.cfgmanager.core.bridge.DeviceSnapshot
import kr.agv.cfgmanager.core.bridge.BridgeStatus
import kr.agv.cfgmanager.core.bridge.StatSource
import kr.agv.cfgmanager.core.bridge.WifiStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionChipMapperTest {

    private fun snapshot(cfg: CfgState?, readAt: Long) = DeviceSnapshot(
        deviceId = "AA:BB", name = "AGVBR-07-A1B2", fw = "0.5.5", mac = null, config = null,
        cfgState = cfg, status = null, capabilities = kr.agv.cfgmanager.core.bridge.Capabilities(),
        readAt = readAt,
    )

    @Test
    fun `끊김은 경고 톤이고 안내 문구를 함께 보인다`() {
        val chip = ConnectionChipMapper.map(SessionState(link = LinkStatus.Disconnected, notice = "5분 동안 사용하지 않아 연결을 끊었습니다."), nowMs = 0)
        assertEquals("끊김", chip.label)
        assertEquals(ChipTone.Warn, chip.tone)
        assertEquals("5분 동안 사용하지 않아 연결을 끊었습니다.", chip.detail)
    }

    @Test
    fun `연결한 적 없는 평상시는 경고가 아니라 중립으로 보인다`() {
        val chip = ConnectionChipMapper.map(SessionState(link = LinkStatus.Disconnected), nowMs = 0)
        assertEquals("연결된 보드 없음", chip.label)
        assertEquals(ChipTone.Neutral, chip.tone)
    }

    @Test
    fun `찾는 중은 스캔 중이고 연결이 없을 때만 보인다`() {
        val scanning = ConnectionChipMapper.map(SessionState(scanning = true), nowMs = 0)
        assertEquals("찾는 중", scanning.label)
        val connecting = ConnectionChipMapper.map(SessionState(link = LinkStatus.Connecting, scanning = true), nowMs = 0)
        assertEquals("연결 중", connecting.label)
    }

    @Test
    fun `연결됨은 이름 fw cfg 상태와 마지막 읽기 시각을 보인다`() {
        val state = SessionState(
            link = LinkStatus.Connected,
            name = "AGVBR-07-A1B2",
            fw = "0.5.5",
            snapshot = snapshot(CfgState.SAVED, readAt = 10_000),
        )
        val chip = ConnectionChipMapper.map(state, nowMs = 14_900)
        assertEquals(ChipTone.Ok, chip.tone)
        assertTrue(chip.label.contains("AGVBR-07-A1B2"))
        assertTrue(chip.label.contains("fw 0.5.5"))
        assertTrue(chip.label.contains("cfg 저장됨"))
        assertEquals("마지막 읽기 4초 전", chip.detail)
    }

    @Test
    fun `재연결 중에는 남은 초를 보인다`() {
        val chip = ConnectionChipMapper.map(
            SessionState(link = LinkStatus.Reconnecting, reconnectLeftSec = 23),
            nowMs = 0,
        )
        assertEquals("끊김 — 다시 연결 중 (23초)", chip.label)
        assertEquals(ChipTone.Warn, chip.tone)
    }

    @Test
    fun `재부팅 대기와 설정 상태 문구`() {
        assertEquals("재부팅 대기", ConnectionChipMapper.map(SessionState(link = LinkStatus.Rebooting), 0).label)
        assertEquals("기본값", ConnectionChipMapper.cfgLabel(CfgState.DEFAULT))
        assertEquals("확인 대기", ConnectionChipMapper.cfgLabel(CfgState.TRIAL))
        assertEquals("미확인", ConnectionChipMapper.cfgLabel(null))
    }

    private fun statusOf(up: Boolean, rssi: Int?) = BridgeStatus(
        source = StatSource.Ble, fw = "0.5.5", part = null, ota = null, cfgState = CfgState.SAVED,
        wifi = WifiStatus(up = up, mac = null, ip = "192.168.10.107", rssi = rssi, channel = 6, bssid = null),
        net = null, setup = null, dsp = null, heapBytes = null,
    )

    private fun connected(status: BridgeStatus?) = SessionState(
        link = LinkStatus.Connected,
        name = "AGVBR-07-A1B2",
        fw = "0.5.5",
        snapshot = snapshot(CfgState.SAVED, readAt = 10_000).copy(status = status),
    )

    
    @Test
    fun `연결됨 칩의 둘째 줄에 Wi-Fi 상태와 신호 세기가 마지막 읽기 앞에 붙는다`() {
        val chip = ConnectionChipMapper.map(connected(statusOf(up = true, rssi = -52)), nowMs = 13_000)
        assertEquals("Wi-Fi 연결됨 -52dBm · 마지막 읽기 3초 전", chip.detail)
    }

    
    @Test
    fun `Wi-Fi 가 끊겼으면 끊김이라고 적고 게이트웨이가 답하면 한 마디 더 적는다`() {
        val down = ConnectionChipMapper.map(connected(statusOf(up = false, rssi = null)), nowMs = 13_000)
        assertEquals("Wi-Fi 끊김 · 마지막 읽기 3초 전", down.detail)
        val up = ConnectionChipMapper.map(connected(statusOf(up = true, rssi = -52)), nowMs = 13_000, gatewayUp = true)
        assertEquals("Wi-Fi 연결됨 -52dBm · 게이트웨이 응답 · 마지막 읽기 3초 전", up.detail)
    }
}
