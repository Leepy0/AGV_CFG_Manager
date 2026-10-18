package kr.agv.cfgmanager.ui.diagnostics

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.net.GatewayResult
import kr.agv.cfgmanager.session.ConsoleKind
import kr.agv.cfgmanager.session.ConsoleLine
import kr.agv.cfgmanager.session.LinkStatus
import kr.agv.cfgmanager.ui.components.ConfirmDialog
import kr.agv.cfgmanager.ui.components.PrimaryActionButton
import kr.agv.cfgmanager.ui.components.SectionTitle
import kr.agv.cfgmanager.ui.components.SmallTextAction
import kr.agv.cfgmanager.ui.components.StatePanel
import kr.agv.cfgmanager.ui.theme.AgvMonoText
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import kr.agv.cfgmanager.ui.theme.AgvTheme

/** 진단 탭: 상태 카드 · 게이트웨이 확인 · 콘솔 · 빠른 명령 · 입력줄 · 주요 행동 [새로고침], 맨 아래 공장 초기화. */
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
    var resetAsk by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // 위: 상태 · 콘솔 · 입력(스크롤). 아래: 주요 행동 [새로고침] 하나(하단 고정). 높이를 비율로 나누면 내용이 잘려서 스크롤로 바꿨다.
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AgvSpacing.L),
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
        Box(modifier = Modifier.fillMaxWidth()) {
            if (connected && snapshot != null) {
                StatusCardView(card = card)
            } else {
                StatePanel(
                    title = if (sessionState.link == LinkStatus.Connecting || sessionState.link == LinkStatus.Reconnecting) {
                        "연결을 기다리는 중"
                    } else {
                        "연결된 장치가 없습니다"
                    },
                    body = "세팅 탭에서 보드를 찾아 연결하면 상태가 여기 보입니다.",
                )
            }
        }
        if (connected && snapshot != null) {
            GatewayLine(
                result = ui.gateway,
                busy = ui.gatewayBusy,
                onCheck = vm::checkGateway,
                onOpen = {
                    vm.gatewayUrl()?.let { url ->
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (missing: ActivityNotFoundException) {
                            // 브라우저가 없으면 조용히 넘어간다. 확인 결과는 그대로 남는다.
                        }
                    }
                },
            )
        }
        ui.error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = AgvTheme.status.alarm)
        }
        ui.resetNotice?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        ConsoleSection(lines = lines, modifier = Modifier.fillMaxWidth().height(AgvSizes.ConsoleHeight))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AgvSpacing.S),
        ) {
            (QUICK_COMMANDS + recent.filterNot { it in QUICK_COMMANDS }).forEach { command ->
                AssistChip(
                    onClick = { if (command in QUICK_COMMANDS) vm.runQuick(command) else vm.recall(command) },
                    label = { Text(command, style = AgvMonoText) },
                    enabled = connected,
                    modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
                )
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
        // 공장 초기화는 되돌릴 수 없으므로 스크롤 맨 끝에 두어 하단의 주요 행동 [새로고침] 과 멀리 떨어뜨린다.
        // 연결된 장치가 없으면 쓸 수 없으니 숨긴다. 초기화 중에는 회색(비활성)으로 둔다. 글자만 두면 버튼으로 안 보여서 테두리 버튼으로 그린다.
        if (connected && snapshot != null) {
            Spacer(modifier = Modifier.size(AgvSpacing.Xl))
            HorizontalDivider()
            val resetEnabled = !ui.resetBusy
            OutlinedButton(
                onClick = { resetAsk = true },
                enabled = resetEnabled,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(
                    AgvSizes.Stroke,
                    if (resetEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin).padding(bottom = AgvSpacing.M),
            ) {
                Text(if (ui.resetBusy) "초기화 중…" else "공장 초기화…")
            }
            // 스크롤 끝에서도 하단 [새로고침] 과 붙지 않게 아래를 넉넉히 비운다 (잘못 누름 방지).
            Spacer(modifier = Modifier.size(AgvSpacing.Xxl))
        }
    }
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(horizontal = AgvSpacing.L, vertical = AgvSpacing.S)) {
            PrimaryActionButton(
                text = "새로고침",
                onClick = vm::refresh,
                enabled = connected && !ui.busy,
            )
        }
    }
    }

    if (resetAsk) {
        val target = vm.resetTarget()
        val label = target?.agv?.let { "%02d호기".format(it) } ?: "이 보드"
        ConfirmDialog(
            title = "$label 설정을 지울까요?",
            text = buildString {
                append("대상: ${target?.deviceName ?: "장치 이름 없음"}\n")
                append("영향: 저장된 Wi-Fi · IP · 서버 설정이 지워지고 보드가 재부팅합니다.\n")
                append("복구: 프로필이나 SSID 라이브러리가 있으면 다시 적용할 수 있습니다. 그 밖의 값은 되돌릴 수 없습니다.")
            },
            confirmLabel = "$label 설정 지우기",
            danger = true,
            cancelFocused = true,
            onConfirm = {
                resetAsk = false
                target?.let { vm.factoryReset(it) }
            },
            onDismiss = { resetAsk = false },
        )
    }
}

/** 게이트웨이 PC 확인 줄 (F-D4): [확인] 은 HEAD 한 번, [화면 열기] 는 브라우저. */
@Composable
private fun GatewayLine(result: GatewayResult?, busy: Boolean, onCheck: () -> Unit, onOpen: () -> Unit) {
    // 문구는 한 줄 폭을 다 쓰고, 버튼은 그 아래 오른쪽에 둔다 (버튼 옆에 두면 문구가 좁아 꺾였다).
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("게이트웨이 PC", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (busy) "확인하는 중…" else gatewayText(result),
            style = MaterialTheme.typography.bodyMedium,
            color = if (result is GatewayResult.Responded) AgvTheme.status.ok else MaterialTheme.colorScheme.onSurface,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SmallTextAction(text = "확인", onClick = onCheck, enabled = !busy)
            SmallTextAction(text = "화면 열기", onClick = onOpen, enabled = result is GatewayResult.Responded)
        }
    }
}

/** 게이트웨이 확인 결과 문구. 응답 없음은 다음 행동까지 적는다. */
internal fun gatewayText(result: GatewayResult?): String = when (result) {
    null -> "아직 확인하지 않았습니다. [확인] 을 누르세요."
    is GatewayResult.Responded -> "응답 있음 (HTTP ${result.status})"
    GatewayResult.NoResponse -> "응답 없음. 휴대폰이 같은 Wi-Fi 에 있는지, 게이트웨이 PC 가 켜져 있는지 확인하세요."
    GatewayResult.InvalidServer -> "서버 주소를 읽지 못했습니다. 보드 설정을 다시 읽으세요."
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
