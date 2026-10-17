package kr.agv.cfgmanager.ui.diagnostics

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.session.ConsoleKind
import kr.agv.cfgmanager.session.ConsoleLine
import kr.agv.cfgmanager.session.LinkStatus
import kr.agv.cfgmanager.ui.components.PrimaryActionButton
import kr.agv.cfgmanager.ui.components.SectionTitle
import kr.agv.cfgmanager.ui.components.SmallTextAction
import kr.agv.cfgmanager.ui.components.StatePanel
import kr.agv.cfgmanager.ui.theme.AgvMonoText
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import kr.agv.cfgmanager.ui.theme.AgvTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** 진단 탭: 상태 카드(위) + 콘솔(아래) + 입력줄 + 주요 행동 [새로고침]. */
@Composable
fun DiagnosticsRoute(container: AppContainer) {
    val vm: DiagnosticsViewModel = viewModel(factory = viewModelFactory {
        initializer { DiagnosticsViewModel(container) }
    })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sessionState by container.session.state.collectAsStateWithLifecycle()
    val lines by container.session.console.collectAsStateWithLifecycle()
    val connected = sessionState.link == LinkStatus.Connected
    val snapshot = sessionState.snapshot
    val card = remember(snapshot) {
        StatusCardBuilder.build(snapshot?.status, snapshot?.cfgState, snapshot?.fw)
    }
    val recent = remember(lines) { recentCommands(lines) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AgvSpacing.L)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(AgvSpacing.S),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("상태", modifier = Modifier.weight(1f))
            Text("자동 5초", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = ui.autoRefresh,
                onCheckedChange = vm::setAutoRefresh,
                enabled = connected,
                modifier = Modifier.padding(start = AgvSpacing.S),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().weight(0.42f)) {
            if (connected && snapshot != null) {
                StatusCardView(card = card, modifier = Modifier.fillMaxSize())
            } else {
                StatePanel(
                    title = if (sessionState.link == LinkStatus.Connecting || sessionState.link == LinkStatus.Reconnecting) {
                        "연결을 기다리는 중"
                    } else {
                        "연결된 장치가 없습니다"
                    },
                    body = "보드 탭에서 보드를 찾아 연결하면 상태가 여기 보입니다.",
                )
            }
        }
        ui.error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = AgvTheme.status.alarm)
        }

        ConsoleSection(lines = lines, modifier = Modifier.fillMaxWidth().weight(0.58f))

        if (recent.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AgvSpacing.S),
            ) {
                recent.forEach { command ->
                    AssistChip(
                        onClick = { vm.recall(command) },
                        label = { Text(command, style = AgvMonoText) },
                        enabled = connected,
                        modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = ui.input,
                onValueChange = vm::setInput,
                enabled = connected,
                singleLine = true,
                textStyle = AgvMonoText,
                placeholder = { Text("명령 입력 (예: stat)") },
                isError = ui.inputError != null,
                modifier = Modifier.weight(1f),
            )
            SmallTextAction(
                text = "보내기",
                onClick = vm::send,
                enabled = connected && ui.input.isNotBlank() && ui.inputError == null,
            )
        }
        ui.inputError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = AgvTheme.status.alarm)
        }
        PrimaryActionButton(
            text = "새로고침",
            onClick = vm::refresh,
            enabled = connected && !ui.busy,
            modifier = Modifier.padding(bottom = AgvSpacing.M),
        )
    }
}

/**
 * 콘솔 원문. 끝 따라가기는 위로 스크롤하면 자동으로 꺼진다.
 * 명령은 이미 가려진 형태로 들어온다 (`login ***`).
 */
@Composable
fun ConsoleSection(lines: List<ConsoleLine>, modifier: Modifier = Modifier) {
    var follow by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && listState.canScrollForward) follow = false
    }
    LaunchedEffect(lines.size, follow) {
        if (follow && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AgvSpacing.Xxs)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("콘솔 원문", modifier = Modifier.weight(1f))
            SmallTextAction(
                text = "공유",
                onClick = {
                    val body = lines.joinToString("\n") { it.text }
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
                    context.startActivity(Intent.createChooser(send, "콘솔 원문 공유"))
                },
                enabled = lines.isNotEmpty(),
            )
            Text("끝 따라가기", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = follow,
                onCheckedChange = { follow = it },
                modifier = Modifier.padding(start = AgvSpacing.S),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(AgvSizes.CardRadius),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (lines.isEmpty()) {
                Text(
                    "아직 주고받은 내용이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(AgvSpacing.M),
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(AgvSpacing.M),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(lines) { line ->
                        Text(
                            text = when (line.kind) {
                                ConsoleKind.System -> "· ${line.text}"
                                else -> line.text
                            },
                            style = AgvMonoText,
                            color = when (line.kind) {
                                ConsoleKind.Sent -> MaterialTheme.colorScheme.onSurface
                                ConsoleKind.Received -> MaterialTheme.colorScheme.onSurfaceVariant
                                ConsoleKind.System -> MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 보낸 명령 최근 5개, 최신순. 비밀 값이 든 줄은 빼서 다시 불러올 수 없게 한다. */
internal fun recentCommands(lines: List<ConsoleLine>): List<String> =
    lines.asReversed().asSequence()
        .filter { it.kind == ConsoleKind.Sent }
        .map { it.text.removePrefix("> ") }
        .filterNot { it.contains("***") }
        .distinct()
        .take(5)
        .toList()
