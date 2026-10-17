package kr.agv.cfgmanager.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kr.agv.cfgmanager.apply.ApplyMessages
import kr.agv.cfgmanager.apply.ApplyProgress
import kr.agv.cfgmanager.apply.ApplyUi
import kr.agv.cfgmanager.apply.ProgressRow
import kr.agv.cfgmanager.apply.ResultAction
import kr.agv.cfgmanager.apply.RowState
import kr.agv.cfgmanager.ble.SignalGrade
import kr.agv.cfgmanager.ui.components.PrimaryActionButton
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import kr.agv.cfgmanager.ui.theme.AgvTheme

/** 적용 진행 패널. 폼 자리에 그린다. 취소는 저장 전 단계에서만 켜진다 (F-A1). */
@Composable
fun ApplyProgressPanel(running: ApplyUi.Running, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AgvSpacing.L, vertical = AgvSpacing.M),
        verticalArrangement = Arrangement.spacedBy(AgvSpacing.M),
    ) {
        val mode = if (running.saveOnly) " (저장만)" else ""
        Text("적용 중 · ${running.agv}호기$mode", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(AgvSpacing.S)) {
            ApplyProgress.rows(running.step).forEach { (row, state) ->
                ProgressLine(row, state, detail = if (row == ProgressRow.Send && state == RowState.Current && running.total > 0) {
                    "${running.done}/${running.total}"
                } else {
                    null
                })
            }
        }
        LinearProgressIndicator(progress = { running.fraction }, modifier = Modifier.fillMaxWidth())
        Text(
            running.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onCancel,
            enabled = running.cancellable,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AgvSizes.TouchMin),
        ) {
            Text(if (running.cancellable) "취소" else "저장 이후에는 취소할 수 없습니다")
        }
        Text(
            "취소는 저장 전까지만 됩니다. 적용이 끝날 때까지 화면을 떠나지 마세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProgressLine(row: ProgressRow, state: RowState, detail: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            RowState.Done -> PanelIcon(Icons.Filled.Check, AgvTheme.status.ok)
            RowState.Current -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            RowState.Pending -> Spacer(modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(AgvSpacing.S))
        Text(
            row.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (state == RowState.Pending) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        val tag = when (state) {
            RowState.Done -> "완료"
            RowState.Current -> detail ?: "진행 중"
            RowState.Pending -> "대기"
        }
        Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 결과 카드. 성공은 중립색 + 체크, 실패는 알람색 + 경고 아이콘. 다음 행동 버튼은 문구와 함께 둔다. */
@Composable
fun ResultPanel(done: ApplyUi.Done, notice: String?, nextAgv: Int?, onAction: (ResultAction) -> Unit) {
    val view = ApplyMessages.view(done.result, nextAgv)
    val device = done.result.after
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AgvSpacing.L, vertical = AgvSpacing.M),
        verticalArrangement = Arrangement.spacedBy(AgvSpacing.M),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (view.success) {
                PanelIcon(Icons.Filled.Check, MaterialTheme.colorScheme.onSurface)
            } else {
                PanelIcon(Icons.Filled.Warning, AgvTheme.status.alarm)
            }
            Spacer(modifier = Modifier.width(AgvSpacing.S))
            Text(
                "${done.agv}호기 · ${view.title}",
                style = MaterialTheme.typography.titleMedium,
                color = if (view.success) MaterialTheme.colorScheme.onSurface else AgvTheme.status.alarm,
            )
        }
        view.step?.let { Text("단계: $it", style = MaterialTheme.typography.labelLarge) }
        Text(view.cause, style = MaterialTheme.typography.bodyMedium)

        if (view.success && device != null) {
            val rssi = device.status?.wifi?.rssi
            FactRow("호기", "${done.agv}호기")
            FactRow("장치 이름", device.name ?: "—")
            FactRow("IP", device.status?.wifi?.ip ?: device.config?.ip ?: "DHCP")
            FactRow("RSSI", rssi?.let { "$it dBm (${SignalGrade.of(it).label})" } ?: "—")
            FactRow("펌웨어", device.fw ?: "—")
        }
        if (done.historyFailed) {
            Text(
                "이력 파일에 기록하지 못했습니다. 저장 공간을 확인하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = AgvTheme.status.warn,
            )
        }
        notice?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = AgvTheme.status.alarm)
        }

        val primary = view.actions.firstOrNull()
        primary?.let { first ->
            PrimaryActionButton(text = actionLabel(first, nextAgv), onClick = { onAction(first) })
        }
        view.actions.drop(1).forEach { action ->
            OutlinedButton(
                onClick = { onAction(action) },
                modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
            ) {
                Text(actionLabel(action, nextAgv))
            }
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(AgvSizes.LabelColumn),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun actionLabel(action: ResultAction, nextAgv: Int?): String = when (action) {
    ResultAction.NextAgv -> "다음 호기 (${nextAgv ?: "-"})"
    ResultAction.Retry -> "다시 시도"
    ResultAction.Console -> "콘솔 보기"
    ResultAction.Reboot -> "재부팅"
    ResultAction.Boards -> "다른 보드 고르기"
    ResultAction.CloseSetup -> "설정 모드 닫기"
    ResultAction.Dismiss -> "확인"
}

@Composable
private fun PanelIcon(icon: ImageVector, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
}

