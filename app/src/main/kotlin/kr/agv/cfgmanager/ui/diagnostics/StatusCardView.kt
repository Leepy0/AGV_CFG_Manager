package kr.agv.cfgmanager.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import kr.agv.cfgmanager.ui.theme.AgvTheme

/** 상태 카드 (F-D2). 제목 · 항목 · 할 일을 한 장에 보인다. 길면 카드 안에서 스크롤한다. */
@Composable
fun StatusCardView(card: StatusCard, modifier: Modifier = Modifier) {
    val toneColor: Color = when (card.emphasis) {
        Emphasis.Ok -> AgvTheme.status.ok
        Emphasis.Warn -> AgvTheme.status.warn
        Emphasis.Alarm -> AgvTheme.status.alarm
        Emphasis.Normal -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AgvSizes.CardRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(AgvSpacing.M),
            verticalArrangement = Arrangement.spacedBy(AgvSpacing.Xxs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (card.emphasis == Emphasis.Ok) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = toneColor)
                    Spacer(modifier = Modifier.width(AgvSpacing.Xxs))
                } else if (card.emphasis == Emphasis.Warn || card.emphasis == Emphasis.Alarm) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = toneColor)
                    Spacer(modifier = Modifier.width(AgvSpacing.Xxs))
                }
                Text(card.title, style = MaterialTheme.typography.titleMedium, color = toneColor)
            }
            card.items.forEach { item ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(AgvSizes.LabelColumn),
                    )
                    Text(
                        item.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (item.emphasis) {
                            Emphasis.Ok -> AgvTheme.status.ok
                            Emphasis.Warn -> AgvTheme.status.warn
                            Emphasis.Alarm -> AgvTheme.status.alarm
                            Emphasis.Normal -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
            card.hints.forEach { hint ->
                Text(
                    "· $hint",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
