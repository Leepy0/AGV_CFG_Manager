package kr.agv.cfgmanager.core.workflows

import kr.agv.cfgmanager.core.bridge.CfgState

/** 연속 세팅에서 끝낸 보드 한 대 (세션 요약 · "완료" 표식). */
data class BatchDone(
    val agv: Int,
    val bleAddress: String,
    val deviceName: String?,
    val result: String,
)

/** 읽은 장치의 설정 상태에 따른 안내 (7.3). */
sealed interface ReadyAdvice {
    /** 기본값 상태 — 폼에 다음 호기와 프로필 값을 채운다. */
    data object FillNextAgv : ReadyAdvice

    /** 이미 설정된 보드 — 자동 채움을 멈추고 경고를 띄운다. 덮어쓰기는 사용자가 고른다. */
    data class AlreadyConfigured(val cfgState: CfgState) : ReadyAdvice
}

fun adviceFor(cfgState: CfgState): ReadyAdvice =
    if (cfgState == CfgState.DEFAULT) ReadyAdvice.FillNextAgv else ReadyAdvice.AlreadyConfigured(cfgState)

/** 연속 세팅 상태 (7.3: Off → Armed → Picking → Reading → Ready → Applying → Armed). */
sealed interface BatchState {
    val nextAgv: Int
    val done: List<BatchDone>

    data class Off(override val nextAgv: Int, override val done: List<BatchDone>) : BatchState
    data class Armed(override val nextAgv: Int, override val done: List<BatchDone>) : BatchState
    data class Reading(
        override val nextAgv: Int,
        override val done: List<BatchDone>,
        val bleAddress: String,
    ) : BatchState
    data class Ready(
        override val nextAgv: Int,
        override val done: List<BatchDone>,
        val bleAddress: String,
        val advice: ReadyAdvice,
    ) : BatchState
    data class Applying(
        override val nextAgv: Int,
        override val done: List<BatchDone>,
        val bleAddress: String,
        val advice: ReadyAdvice,
    ) : BatchState
}

sealed interface BatchEvent {
    data class Start(val firstAgv: Int) : BatchEvent
    data object Stop : BatchEvent
    data class PickDevice(val bleAddress: String) : BatchEvent
    data class ReadFinished(val cfgState: CfgState) : BatchEvent
    data object ReadFailed : BatchEvent
    data object BackToList : BatchEvent
    data object ApplyStarted : BatchEvent
    data class ApplySucceeded(val deviceName: String?, val result: String) : BatchEvent
    data object ApplyFailed : BatchEvent
}

/** 연속 세팅 상태 기계. 순수 함수 — 장치 · 시간을 다루지 않는다. 맞지 않는 사건은 무시한다. */
object BatchCoordinator {

    fun initial(firstAgv: Int = 1): BatchState = BatchState.Off(firstAgv, emptyList())

    fun reduce(state: BatchState, event: BatchEvent): BatchState = when (state) {
        is BatchState.Off -> when (event) {
            is BatchEvent.Start -> BatchState.Armed(event.firstAgv, state.done)
            else -> state
        }
        is BatchState.Armed -> when (event) {
            BatchEvent.Stop -> BatchState.Off(state.nextAgv, state.done)
            is BatchEvent.PickDevice ->
                if (state.done.any { it.bleAddress == event.bleAddress }) state
                else BatchState.Reading(state.nextAgv, state.done, event.bleAddress)
            else -> state
        }
        is BatchState.Reading -> when (event) {
            is BatchEvent.ReadFinished ->
                BatchState.Ready(state.nextAgv, state.done, state.bleAddress, adviceFor(event.cfgState))
            BatchEvent.ReadFailed, BatchEvent.BackToList -> BatchState.Armed(state.nextAgv, state.done)
            BatchEvent.Stop -> BatchState.Off(state.nextAgv, state.done)
            else -> state
        }
        is BatchState.Ready -> when (event) {
            BatchEvent.ApplyStarted -> BatchState.Applying(state.nextAgv, state.done, state.bleAddress, state.advice)
            BatchEvent.BackToList -> BatchState.Armed(state.nextAgv, state.done)
            BatchEvent.Stop -> BatchState.Off(state.nextAgv, state.done)
            else -> state
        }
        is BatchState.Applying -> when (event) {
            is BatchEvent.ApplySucceeded -> BatchState.Armed(
                nextAgv = state.nextAgv + 1,
                done = state.done + BatchDone(state.nextAgv, state.bleAddress, event.deviceName, event.result),
            )
            BatchEvent.ApplyFailed -> BatchState.Ready(state.nextAgv, state.done, state.bleAddress, state.advice)
            else -> state
        }
    }
}
