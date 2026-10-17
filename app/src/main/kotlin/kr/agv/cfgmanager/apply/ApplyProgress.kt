package kr.agv.cfgmanager.apply

import kr.agv.cfgmanager.core.workflows.ApplyStep

/** 진행 패널에 보이는 여섯 줄 (docs/DEVELOPMENT_PLAN.md 5.3). 설정 확인 단계는 첫 줄 전에 지나간다. */
enum class ProgressRow(val label: String) {
    Send("설정 전송"),
    ReadBack("되읽기"),
    Save("저장"),
    Reboot("재부팅"),
    Reconnect("재연결"),
    WifiCheck("Wi-Fi 확인"),
}

enum class RowState { Done, Current, Pending }

/** 워크플로 단계 → 진행 줄 · 전체 진행률 매핑. 화면 문구와 퍼센트는 여기서만 정한다. */
object ApplyProgress {

    /** 현재 단계가 속한 진행 줄. 설정 확인 · 완료는 줄이 없다. */
    fun rowOf(step: ApplyStep): ProgressRow? = when (step) {
        ApplyStep.Validate -> null
        ApplyStep.SendSet -> ProgressRow.Send
        ApplyStep.ReadBack -> ProgressRow.ReadBack
        ApplyStep.Save -> ProgressRow.Save
        ApplyStep.Reboot -> ProgressRow.Reboot
        ApplyStep.WaitReady -> ProgressRow.Reconnect
        ApplyStep.VerifyWifi -> ProgressRow.WifiCheck
        ApplyStep.Done -> null
    }

    /** 각 줄의 상태. [step] 이 Done 이면 모두 끝, Validate 면 모두 대기다. */
    fun rows(step: ApplyStep): List<Pair<ProgressRow, RowState>> {
        val current = rowOf(step)
        val currentIndex = current?.ordinal ?: when (step) {
            ApplyStep.Done -> ProgressRow.entries.size
            else -> -1
        }
        return ProgressRow.entries.map { row ->
            val state = when {
                row.ordinal < currentIndex -> RowState.Done
                row.ordinal == currentIndex -> RowState.Current
                else -> RowState.Pending
            }
            row to state
        }
    }

    /** 전체 진행률 0.0 ~ 1.0. 설정 전송만 i/n 으로 나눈다. */
    fun fraction(step: ApplyStep, done: Int, total: Int): Float {
        val sendRatio = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
        return when (step) {
            ApplyStep.Validate -> 0f
            ApplyStep.SendSet -> 0.05f + 0.45f * sendRatio
            ApplyStep.ReadBack -> 0.55f
            ApplyStep.Save -> 0.65f
            ApplyStep.Reboot -> 0.72f
            ApplyStep.WaitReady -> 0.8f
            ApplyStep.VerifyWifi -> 0.92f
            ApplyStep.Done -> 1f
        }
    }

    /** 저장 전 단계에서만 취소할 수 있다 (F-A1, 7.1). */
    fun cancellable(step: ApplyStep): Boolean =
        step == ApplyStep.Validate || step == ApplyStep.SendSet || step == ApplyStep.ReadBack
}
