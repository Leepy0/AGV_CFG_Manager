package kr.agv.cfgmanager.apply

import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.core.workflows.ApplyFailure
import kr.agv.cfgmanager.core.workflows.ApplyOutcome
import kr.agv.cfgmanager.core.workflows.ApplyResult
import kr.agv.cfgmanager.core.workflows.ApplyStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplyMessagesTest {

    private fun failed(step: ApplyStep, failure: ApplyFailure) = ApplyResult(
        outcome = ApplyOutcome.Failed(step, failure),
        historyResult = "x",
        changedKeys = emptySet(),
        saved = false,
        after = null,
    )

    @Test
    fun `거부된 키 이름이 제목에 들어가고 다시 시도 · 콘솔 보기가 있다`() {
        val view = ApplyMessages.view(failed(ApplyStep.SendSet, ApplyFailure.InvalidValue(ConfigKey.SERVER, "invalid key or value: server")))
        assertEquals(false, view.success)
        assertTrue(view.title.contains("server"))
        assertEquals(listOf(ResultAction.Retry, ResultAction.Console), view.actions)
    }

    @Test
    fun `재부팅 거부는 저장됐다는 말과 재부팅 버튼을 준다`() {
        val view = ApplyMessages.view(failed(ApplyStep.Reboot, ApplyFailure.RebootRefused("busy: mainboard programming")))
        assertTrue(view.cause.contains("저장은 끝났습니다"))
        assertEquals(ResultAction.Reboot, view.actions.first())
    }

    @Test
    fun `Wi-Fi 끊김 문구는 사유 코드와 조치를 담는다`() {
        val text = ApplyMessages.wifiCause(201)
        assertTrue(text.contains("201"))
        assertTrue(text.contains("NO_AP_FOUND"))
        assertTrue(ApplyMessages.wifiCause(null).contains("알 수 없습니다"))
    }

    @Test
    fun `성공 카드는 설정 모드 닫기를 먼저 준다`() {
        val ok = ApplyResult(ApplyOutcome.Applied, "ok", emptySet(), true, null)
        val view = ApplyMessages.view(ok)
        assertTrue(view.success)
        assertEquals(ResultAction.CloseSetup, view.actions.first())
    }

    @Test
    fun `문구에 비밀번호 값이 들어가지 않는다`() {
        val view = ApplyMessages.view(failed(ApplyStep.ReadBack, ApplyFailure.Timeout("set pass ***")))
        assertFalse(view.cause.contains("secret-pass"))
        assertTrue(view.cause.contains("***"))
    }
}
