package kr.agv.cfgmanager.batch

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kr.agv.cfgmanager.apply.ApplyUi
import kr.agv.cfgmanager.core.bridge.CfgState
import kr.agv.cfgmanager.core.workflows.ApplyOutcome
import kr.agv.cfgmanager.core.workflows.ApplyOutcome.Failed
import kr.agv.cfgmanager.core.workflows.ApplyResult
import kr.agv.cfgmanager.core.workflows.BatchState
import kr.agv.cfgmanager.core.workflows.ApplyStep
import kr.agv.cfgmanager.core.workflows.ApplyFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class BatchControllerTest {

    private fun done(outcome: ApplyOutcome, history: String) = ApplyUi.Done(
        result = ApplyResult(outcome, history, emptySet(), saved = true, after = null),
        agv = 7,
        saveOnly = false,
        historyFailed = false,
    )

    private val running = ApplyUi.Running(7, false, ApplyStep.Validate, "설정 확인", 0, 0)

    @Test
    fun `두 대를 연속으로 설정하면 호기가 자동으로 넘어간다`() = runTest {
        val apply = MutableStateFlow<ApplyUi>(ApplyUi.Idle)
        val batch = BatchController(apply, backgroundScope)
        yield()
        batch.start(firstAgv = 7, profileName = "본사 1공장")
        assertEquals("본사 1공장", batch.ui.value.profileName)

        batch.pick("AA:01")
        batch.readFinished(CfgState.DEFAULT)
        assertIs<BatchState.Ready>(batch.ui.value.state)
        apply.value = running
        yield()
        assertIs<BatchState.Ready>(batch.ui.value.state)
        apply.value = done(ApplyOutcome.Applied, "ok")
        yield()
        var state = assertIs<BatchState.Armed>(batch.ui.value.state)
        assertEquals(8, state.nextAgv)
        assertEquals(listOf("AA:01"), state.done.map { it.bleAddress })

        apply.value = ApplyUi.Idle

        yield()
        batch.pick("AA:02")
        batch.readFinished(CfgState.DEFAULT)
        apply.value = running
        yield()
        apply.value = done(ApplyOutcome.Applied, "ok")
        yield()
        state = assertIs<BatchState.Armed>(batch.ui.value.state)
        assertEquals(9, state.nextAgv)
        assertEquals(listOf("AA:01", "AA:02"), state.done.map { it.bleAddress })
    }

    @Test
    fun `이미 설정된 보드는 경고 상태로 두고 적용 실패는 준비 상태로 돌아간다`() = runTest {
        val apply = MutableStateFlow<ApplyUi>(ApplyUi.Idle)
        val batch = BatchController(apply, backgroundScope)
        yield()
        batch.start(firstAgv = 7, profileName = null)

        batch.pick("AA:09")
        batch.readFinished(CfgState.SAVED)
        val ready = assertIs<BatchState.Ready>(batch.ui.value.state)
        assertIs<kr.agv.cfgmanager.core.workflows.ReadyAdvice.AlreadyConfigured>(ready.advice)

        batch.backToList()
        batch.pick("AA:03")
        batch.readFinished(CfgState.DEFAULT)
        apply.value = running
        yield()
        apply.value = done(Failed(ApplyStep.SendSet, ApplyFailure.Timeout("set agv 7")), "timeout")
        yield()
        assertIs<BatchState.Ready>(batch.ui.value.state)
        assertEquals(7, batch.ui.value.state.nextAgv)
    }

    @Test
    fun `끝내면 연속 모드가 꺼지고 프로필 이름도 지워진다`() = runTest {
        val batch = BatchController(MutableStateFlow(ApplyUi.Idle), backgroundScope)
        yield()
        batch.start(firstAgv = 3, profileName = "본사")
        batch.stop()
        assertEquals(false, batch.ui.value.enabled)
    }
}
