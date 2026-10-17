package kr.agv.cfgmanager.batch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.agv.cfgmanager.apply.ApplyUi
import kr.agv.cfgmanager.core.bridge.CfgState
import kr.agv.cfgmanager.core.workflows.ApplyOutcome
import kr.agv.cfgmanager.core.workflows.BatchCoordinator
import kr.agv.cfgmanager.core.workflows.BatchEvent
import kr.agv.cfgmanager.core.workflows.BatchState

/** 연속 세팅 화면이 보는 상태: 상태 기계 + 프로필 이름(배너용). */
data class BatchUi(val state: BatchState, val profileName: String?) {
    val enabled: Boolean get() = state !is BatchState.Off
}

/**
 * 연속 세팅 (docs/DEVELOPMENT_PLAN.md 7.3). 상태 기계는 [BatchCoordinator] 가 맡고, 이 클래스는
 * 화면 사건(보드 선택 · 읽기 결과 · 뒤로 가기)을 넣고 [ApplyRunner] 의 적용 상태를 관찰해 사건을 만든다.
 * 앱 수명 범위에서 살아 있어야 탭을 옮겨도 연속 작업이 이어진다.
 */
class BatchController(apply: StateFlow<ApplyUi>, scope: CoroutineScope) {

    private val uiFlow = MutableStateFlow(BatchUi(BatchCoordinator.initial(), null))
    val ui: StateFlow<BatchUi> = uiFlow.asStateFlow()

    init {
        scope.launch {
            var previous: ApplyUi = apply.value
            apply.collect { now ->
                observe(previous, now)
                previous = now
            }
        }
    }

    /** 연속 모드를 켠다. [firstAgv] 는 첫 번째 보드에 줄 호기 번호. */
    fun start(firstAgv: Int, profileName: String?) {
        uiFlow.value = BatchUi(BatchCoordinator.reduce(BatchCoordinator.initial(firstAgv), BatchEvent.Start(firstAgv)), profileName)
    }

    fun stop() = send(BatchEvent.Stop)

    /** 보드를 골랐다 (연결 시작). */
    fun pick(address: String) = send(BatchEvent.PickDevice(address))

    /** 읽기가 끝났다. [cfgState] 로 자동 채움 또는 경고를 정한다. */
    fun readFinished(cfgState: CfgState) = send(BatchEvent.ReadFinished(cfgState))

    fun readFailed() = send(BatchEvent.ReadFailed)

    /** 보드 목록으로 돌아갔다. 읽은 보드를 버리고 목록 상태로 둔다. */
    fun backToList() = send(BatchEvent.BackToList)

    private fun send(event: BatchEvent) {
        uiFlow.update { it.copy(state = BatchCoordinator.reduce(it.state, event)) }
    }

    /**
     * 적용 상태 변화를 사건으로 바꾼다. [ApplyUi.Running] 은 StateFlow 에서 합쳐져 빠질 수 있으므로
     * 준비 상태에서 끝난 결과가 오면 시작 사건을 먼저 넣는다.
     */
    private fun observe(previous: ApplyUi, now: ApplyUi) {
        if (now !is ApplyUi.Done || previous is ApplyUi.Done) return
        if (uiFlow.value.state is BatchState.Ready) send(BatchEvent.ApplyStarted)
        if (uiFlow.value.state !is BatchState.Applying) return
        val result = now.result
        val success = result.outcome == ApplyOutcome.Applied || result.outcome == ApplyOutcome.SavedOnly
        if (success) {
            send(BatchEvent.ApplySucceeded(result.after?.name, result.historyResult))
        } else {
            send(BatchEvent.ApplyFailed)
        }
    }
}
