package kr.agv.cfgmanager.apply

import kr.agv.cfgmanager.core.workflows.ApplyStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplyProgressTest {

    @Test
    fun `저장 전 단계만 취소할 수 있다`() {
        assertTrue(ApplyProgress.cancellable(ApplyStep.SendSet))
        assertTrue(ApplyProgress.cancellable(ApplyStep.ReadBack))
        assertFalse(ApplyProgress.cancellable(ApplyStep.Save))
        assertFalse(ApplyProgress.cancellable(ApplyStep.Reboot))
        assertFalse(ApplyProgress.cancellable(ApplyStep.VerifyWifi))
    }

    @Test
    fun `단계는 진행 줄에 매핑되고 설정 확인 · 완료는 줄이 없다`() {
        assertEquals(ProgressRow.Send, ApplyProgress.rowOf(ApplyStep.SendSet))
        assertEquals(ProgressRow.Reconnect, ApplyProgress.rowOf(ApplyStep.WaitReady))
        assertEquals(ProgressRow.WifiCheck, ApplyProgress.rowOf(ApplyStep.VerifyWifi))
        assertEquals(null, ApplyProgress.rowOf(ApplyStep.Validate))
        assertEquals(null, ApplyProgress.rowOf(ApplyStep.Done))
    }

    @Test
    fun `저장 단계에서는 앞 줄이 완료 현재 줄이 저장이다`() {
        val rows = ApplyProgress.rows(ApplyStep.Save).associate { it.first to it.second }
        assertEquals(RowState.Done, rows[ProgressRow.Send])
        assertEquals(RowState.Done, rows[ProgressRow.ReadBack])
        assertEquals(RowState.Current, rows[ProgressRow.Save])
        assertEquals(RowState.Pending, rows[ProgressRow.Reboot])
    }

    @Test
    fun `완료 단계는 모든 줄이 끝나고 설정 확인 단계는 모두 대기`() {
        assertTrue(ApplyProgress.rows(ApplyStep.Done).all { it.second == RowState.Done })
        assertTrue(ApplyProgress.rows(ApplyStep.Validate).all { it.second == RowState.Pending })
    }

    @Test
    fun `진행률은 설정 전송 i 과 n 에 따라 늘고 완료에서 1 이 된다`() {
        val first = ApplyProgress.fraction(ApplyStep.SendSet, 1, 16)
        val last = ApplyProgress.fraction(ApplyStep.SendSet, 16, 16)
        assertTrue(first < last)
        assertTrue(last < ApplyProgress.fraction(ApplyStep.ReadBack, 0, 0))
        assertEquals(1f, ApplyProgress.fraction(ApplyStep.Done, 0, 0))
        assertEquals(0f, ApplyProgress.fraction(ApplyStep.Validate, 0, 0))
    }
}
