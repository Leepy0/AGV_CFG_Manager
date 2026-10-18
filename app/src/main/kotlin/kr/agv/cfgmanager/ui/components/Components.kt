package kr.agv.cfgmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kr.agv.cfgmanager.session.ChipTone
import kr.agv.cfgmanager.session.ChipUi
import kr.agv.cfgmanager.ble.SignalGrade
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import kr.agv.cfgmanager.ui.theme.AgvTheme

/** 화면 하단의 주요 행동 (72dp, 가로 전체). */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(AgvSizes.PrimaryAction),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** 상태 화면의 버튼 하나. */
data class ActionSpec(val label: String, val onClick: () -> Unit)

/** Empty · Error 같은 상태 화면: 무엇이 · 왜 · 다음 행동. */
@Composable
fun StatePanel(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    primary: ActionSpec? = null,
    secondary: ActionSpec? = null,
    isError: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = AgvSpacing.L, vertical = AgvSpacing.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AgvSpacing.S),
    ) {
        if (isError) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = AgvTheme.status.alarm)
                Spacer(modifier = Modifier.width(AgvSpacing.S))
                Text(title, style = MaterialTheme.typography.titleMedium, color = AgvTheme.status.alarm)
            }
        } else {
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        body?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        primary?.let {
            Button(onClick = it.onClick, modifier = Modifier.heightIn(min = AgvSizes.TouchMin)) {
                Text(it.label)
            }
        }
        secondary?.let {
            OutlinedButton(onClick = it.onClick, modifier = Modifier.heightIn(min = AgvSizes.TouchMin)) {
                Text(it.label)
            }
        }
    }
}

/** 신호 막대 3개. 등급 글자는 옆 텍스트가 맡는다. [dim] 이면 흐리게. */
@Composable
fun SignalBars(rssi: Int, modifier: Modifier = Modifier, dim: Boolean = false) {
    val grade = SignalGrade.of(rssi)
    val onColor = if (dim) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
    val offColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier.width(AgvSizes.SignalBarWidth * 3 + AgvSizes.SignalBarGap * 2),
        horizontalArrangement = Arrangement.spacedBy(AgvSizes.SignalBarGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        AgvSizes.SignalBarHeights.forEachIndexed { index, height ->
            Box(
                modifier = Modifier
                    .width(AgvSizes.SignalBarWidth)
                    .height(height)
                    .background(if (index < grade.bars) onColor else offColor, RoundedCornerShape(AgvSizes.SignalBarRadius)),
            )
        }
    }
}

/** 상단 연결 칩. 글자가 주, 색은 보조 (정상 · 경고 표식 아이콘을 함께 쓴다). */
@Composable
fun ConnectionChip(chip: ChipUi, modifier: Modifier = Modifier) {
    val toneColor: androidx.compose.ui.graphics.Color = when (chip.tone) {
        ChipTone.Ok -> AgvTheme.status.ok
        ChipTone.Warn -> AgvTheme.status.warn
        ChipTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (chip.tone) {
        ChipTone.Ok -> Icons.Filled.Check
        ChipTone.Warn -> Icons.Filled.Warning
        ChipTone.Neutral -> Icons.Filled.Info
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AgvSizes.ChipRadius),
        color = if (chip.tone == ChipTone.Neutral) MaterialTheme.colorScheme.surfaceContainer else toneColor.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AgvSpacing.M, vertical = AgvSpacing.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = toneColor, modifier = Modifier.size(AgvSizes.IconMedium))
            Spacer(modifier = Modifier.width(AgvSpacing.S))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    chip.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = toneColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                chip.detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 소형 텍스트 버튼 (48dp 타깃). */
@Composable
fun SmallTextAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
    ) {
        Text(text)
    }
}

/** 열 제목 (작고 옅게). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = AgvSpacing.M),
    )
}
