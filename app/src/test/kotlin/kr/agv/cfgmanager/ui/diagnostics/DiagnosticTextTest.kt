package kr.agv.cfgmanager.ui.diagnostics

import kr.agv.cfgmanager.core.workflows.FactoryResetOutcome
import kr.agv.cfgmanager.net.GatewayResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticTextTest {

    @Test
    fun `빠른 명령 칩은 F-D3 의 여섯 개다`() {
        assertEquals(listOf("stat", "cfg", "setup off", "dsp", "dsp ver", "help"), QUICK_COMMANDS)
    }

    @Test
    fun `게이트웨이 응답 없음 문구는 다음 행동을 말한다`() {
        assertTrue(gatewayText(GatewayResult.NoResponse).contains("같은 Wi-Fi"))
        assertTrue(gatewayText(GatewayResult.Responded(200)).contains("HTTP 200"))
        assertTrue(gatewayText(null).contains("확인"))
    }

    @Test
    fun `초기화 결과 문구는 설정을 지웠는지와 재부팅 상태를 말한다`() {
        assertTrue(resetNoticeText(FactoryResetOutcome.Rebooting).contains("재부팅"))
        assertTrue(resetNoticeText(FactoryResetOutcome.RebootRefused("busy")).contains("거부"))
        assertTrue(resetNoticeText(FactoryResetOutcome.EraseFailed("timeout")).contains("그대로"))
    }
}
