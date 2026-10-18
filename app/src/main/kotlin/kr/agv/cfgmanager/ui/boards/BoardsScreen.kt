package kr.agv.cfgmanager.ui.boards

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import kr.agv.cfgmanager.ui.components.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import kr.agv.cfgmanager.ui.components.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.ble.SignalGrade
import kr.agv.cfgmanager.ble.isStale
import kr.agv.cfgmanager.session.BoardRow
import kr.agv.cfgmanager.session.ConnectStage
import kr.agv.cfgmanager.session.ConnectFailure
import kr.agv.cfgmanager.session.ConnectFailureText
import kr.agv.cfgmanager.ui.components.ActionSpec
import kr.agv.cfgmanager.ui.components.PrimaryActionButton
import kr.agv.cfgmanager.ui.components.SignalBars
import kr.agv.cfgmanager.ui.components.StatePanel
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvTheme
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import kr.agv.cfgmanager.ui.components.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 세팅 탭의 "보드 고르기" 단계: 상태를 VM 에서 받아 [BoardsContent] 로 그린다. 들어오면 자동으로 찾기 시작한다.
 * 연결이 끝나면 [onConnected] 를 한 번 부른다 (세팅 탭은 연결 상태를 보고 스스로 다음 단계로 바뀐다).
 */
@Composable
fun BoardsRoute(container: AppContainer, onConnected: () -> Unit = {}) {
    val vm: BoardsViewModel = viewModel(factory = viewModelFactory {
        initializer { BoardsViewModel(container) }
    })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(vm) {
        vm.connected.collect { onConnected() }
    }
    LaunchedEffect(vm) { vm.autoScan() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.startScan() }
    val enableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { vm.startScan() }

    BoardsContent(
        ui = ui,
        onPrimary = vm::startScan,
        onStop = vm::stopScan,
        onRowClick = vm::pick,
        onSelectProfile = vm::selectProfile,
        onErrorAction = { error ->
            when (error) {
                is ScanError.Permission -> permissionLauncher.launch(error.permissions.toTypedArray())
                ScanError.BluetoothOff -> enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                ScanError.LocationOff -> context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                ScanError.Failed -> vm.startScan()
            }
        },
        onOpenAppSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        },
        onPasswordChange = vm::setPassword,
        onConnect = vm::connect,
        onCancelConnect = vm::cancelConnect,
        onSheetDismiss = vm::closeSheet,
    )
}

/** 보드 탭 화면 (상태 없음 · 테스트 가능). */
@Composable
fun BoardsContent(
    ui: BoardsUi,
    onPrimary: () -> Unit,
    onStop: () -> Unit,
    onRowClick: (BoardRow) -> Unit,
    onErrorAction: (ScanError) -> Unit,
    onOpenAppSettings: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    onCancelConnect: () -> Unit,
    onSheetDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectProfile: (String) -> Unit = {},
) {
    val scanning = ui.scanState == ScanState.Scanning
    Column(modifier = modifier.fillMaxSize().padding(horizontal = AgvSpacing.L)) {
        ProfileBar(ui, onSelectProfile)
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 스캔 중이라는 말은 상단 연결 칩("찾는 중")과 진행 막대가 이미 알린다. 여기서는 개수만 적는다.
            Text(
                text = "찾은 보드 ${ui.rows.size}대",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
        }
        if (scanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = AgvSpacing.Xxs))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (ui.view) {
                BoardsView.Loading -> StatePanel(
                    title = "보드를 찾고 있습니다",
                    body = "보드 가까이에서 기다려 주세요. 10초 뒤 자동으로 멈춥니다.",
                )
                BoardsView.Empty -> StatePanel(
                    title = if (ui.scanState == ScanState.Idle) "아직 찾지 않았습니다" else "보이는 보드가 없습니다",
                    body = "${BoardsHint.BOOT_HINT}\n${BoardsHint.SETUP_WINDOW_HINT}",
                )
                BoardsView.Error -> {
                    val error = ui.error
                    if (error != null) {
                        val (primary, secondary) = errorActions(error, onErrorAction, onOpenAppSettings)
                        StatePanel(
                            title = ScanErrorText.title(error),
                            isError = true,
                            primary = primary,
                            secondary = secondary,
                        )
                    }
                }
                BoardsView.Content -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    ui.error?.let { error ->
                        item { ScanErrorBanner(error, onErrorAction, onOpenAppSettings) }
                    }
                    items(ui.arrangedRows(), key = { it.address }) { row ->
                        val key = row.address.uppercase()
                        BoardRowItem(
                            row = row,
                            nowMs = ui.nowMs,
                            historyAgv = ui.historyAgv[key],
                            done = key in ui.doneAddresses,
                            onClick = { onRowClick(row) },
                        )
                        HorizontalDivider()
                    }
                    item {
                        Text(
                            BoardsHint.BOOT_HINT,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = AgvSpacing.M),
                        )
                    }
                }
            }
        }

        ui.batchBanner?.let { banner ->
            Text(
                banner,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(vertical = AgvSpacing.S),
            )
        }

        ui.notice?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = AgvSpacing.S),
            )
        }
        // 주요 행동은 하단에 하나: 찾는 중에는 [찾기 중지], 아니면 [보드 찾기] · [다시 찾기]. 비활성 큰 버튼을 두지 않는다.
        if (scanning) {
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AgvSizes.PrimaryAction)
                    .padding(bottom = AgvSpacing.M),
            ) {
                Text("찾기 중지", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            PrimaryActionButton(
                text = if (ui.scanState == ScanState.Idle) "보드 찾기" else "다시 찾기",
                onClick = onPrimary,
                modifier = Modifier.padding(bottom = AgvSpacing.M),
            )
        }
    }

    ui.sheet?.let { sheet ->
        ConnectSheetView(
            sheet = sheet,
            onPasswordChange = onPasswordChange,
            onConnect = onConnect,
            onCancel = onCancelConnect,
            onDismiss = onSheetDismiss,
        )
    }
}

/**
 * 맨 위 한 줄: 이번 작업의 현장 프로필과 다음 호기 제안. 보드를 누르기 전에 정한다
 * (연결 로그인 비밀번호와 연결 뒤 채울 값이 이 프로필에서 나오므로 순서상 첫 단계다).
 */
@Composable
private fun ProfileBar(ui: BoardsUi, onSelect: (String) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val selected = ui.profiles.firstOrNull { it.id == ui.selectedProfileId }
    Column(modifier = Modifier.fillMaxWidth().padding(top = AgvSpacing.S)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("현장", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(AgvSizes.LabelColumn))
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { menuOpen = true },
                    enabled = ui.profiles.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
                ) {
                    Text(selected?.name ?: "프로필 선택", modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ui.profiles.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = {
                                menuOpen = false
                                onSelect(option.id)
                            },
                        )
                    }
                }
            }
        }
        Text(
            if (selected == null) {
                "프로필을 고르세요. 더보기 > 프로필에서 만들 수 있습니다."
            } else {
                "연결하면 이 프로필 값이 채워집니다" +
                    (ui.nextAgv?.let { " · 다음 호기 제안 ${"%02d".format(it)}" } ?: "")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // 드롭다운 버튼의 눈에 보이는 높이는 터치 영역보다 작아서, 위 간격을 띄워야 글자가 버튼에 붙지 않는다.
            modifier = Modifier.padding(top = AgvSpacing.S, bottom = AgvSpacing.S),
        )
    }
}

/** 한 줄: 신호 막대 · 이름 · 설정 표식 · 이력 표식 · 완료 표식 · dBm. 5초 이상 안 보이면 흐리게. */
@Composable
fun BoardRowItem(row: BoardRow, nowMs: Long, historyAgv: Int?, done: Boolean, onClick: () -> Unit) {
    val stale = row.isStale(nowMs)
    val grade = SignalGrade.of(row.rssi)
    val setupText = when (val number = row.agvNumber) {
        null -> "호기 미확인"
        1 -> "설정 없음(01)"
        else -> "${number}호기 설정됨"
    }
    val subtitle = buildString {
        append(setupText)
        historyAgv?.let { append(" · 이력: ${"%02d".format(it)}호기") }
        if (done) append(" · 완료")
        if (row.isUsb) append(" · USB 직결") else append(" · ${row.rssi} dBm · ${grade.label}")
        if (stale) append(" · 오래됨")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AgvSizes.ListRowMin)
            .alpha(if (stale) 0.5f else 1f)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = AgvSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignalBars(rssi = row.rssi, dim = stale)
        Column(modifier = Modifier.weight(1f).padding(start = AgvSpacing.M)) {
            Text(
                row.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "연결",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 목록 위의 스캔 오류 한 줄 (USB 행은 그대로 보인다). 무엇이 막혔는지와 다음 행동만 적는다. */
@Composable
private fun ScanErrorBanner(
    error: ScanError,
    onErrorAction: (ScanError) -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val (primary, _) = errorActions(error, onErrorAction, onOpenAppSettings)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = AgvSpacing.S)) {
        // 블루투스가 막혀도 USB 로는 작업할 수 있으니 알람(빨강)이 아니라 경고(주의색 + 아이콘 + 글자)로 보인다.
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = AgvTheme.status.warn)
            Spacer(modifier = Modifier.width(AgvSpacing.S))
            Text(
                ScanErrorText.title(error),
                style = MaterialTheme.typography.bodyMedium,
                color = AgvTheme.status.warn,
            )
        }
        Text(
            "USB 로 연결한 보드는 계속 보입니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        primary?.let { action ->
            TextButton(onClick = action.onClick, modifier = Modifier.heightIn(min = AgvSizes.TouchMin)) {
                Text(action.label)
            }
        }
    }
}

private fun errorActions(
    error: ScanError,
    onErrorAction: (ScanError) -> Unit,
    onOpenAppSettings: () -> Unit,
): Pair<ActionSpec?, ActionSpec?> = when (error) {
    is ScanError.Permission -> ActionSpec("권한 허용") { onErrorAction(error) } to
        ActionSpec("설정 열기", onOpenAppSettings)
    ScanError.BluetoothOff -> ActionSpec("블루투스 켜기") { onErrorAction(error) } to null
    ScanError.LocationOff -> ActionSpec("위치 설정 열기") { onErrorAction(error) } to null
    ScanError.Failed -> ActionSpec("다시 찾기") { onErrorAction(error) } to null
}

private fun BoardsUi.secondsLeft(): Int {
    val started = scanStartedMs
    return if (started == 0L) 10 else (10 - ((nowMs - started) / 1000)).toInt().coerceIn(0, 10)
}

/** 연결 시트: 연결 → 로그인 → 읽기 3단계, 비밀번호 입력, 실패 사유. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectSheetView(
    sheet: ConnectSheet,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AgvSpacing.L).padding(bottom = AgvSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(AgvSpacing.M),
        ) {
            Text(sheet.row.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            StageSteps(current = sheet.stage, failed = sheet.failure != null)

            if (sheet.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "보드와 연결하는 중입니다. 휴대폰을 보드 가까이에 두세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            sheet.failure?.let { failure ->
                FailureText(failure)
            }
            if (sheet.row.isUsb && !sheet.busy) {
                Text(
                    "USB 케이블로 직접 연결합니다. 로그인은 필요 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!sheet.busy && !sheet.row.isUsb) {
                OutlinedTextField(
                    value = sheet.password,
                    onValueChange = onPasswordChange,
                    label = { Text("BLE 비밀번호") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        capitalization = KeyboardCapitalization.None,
                    ),
                    trailingIcon = {
                        TextButton(
                            onClick = { showPassword = !showPassword },
                            modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
                        ) {
                            Text(if (showPassword) "숨김" else "표시")
                        }
                    },
                )
                Text(
                    "기본값은 agvsetup 입니다. 틀리면 남은 시도 횟수를 알려 드립니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sheet.busy) {
                PrimaryActionButton(text = "취소", onClick = onCancel)
            } else {
                PrimaryActionButton(
                    text = if (sheet.failure != null) "다시 시도" else "연결",
                    onClick = onConnect,
                    enabled = sheet.row.isUsb || sheet.password.isNotEmpty(),
                )
            }
        }
    }
}

@Composable
private fun StageSteps(current: ConnectStage?, failed: Boolean) {
    val steps = listOf("연결" to ConnectStage.Connect, "로그인" to ConnectStage.Login, "읽기" to ConnectStage.Read)
    val currentIndex = steps.indexOfFirst { it.second == current }
    Row(horizontalArrangement = Arrangement.spacedBy(AgvSpacing.S), modifier = Modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, (label, _) ->
            val done = currentIndex > index
            val active = currentIndex == index
            val color = when {
                failed && active -> AgvTheme.status.alarm
                done -> AgvTheme.status.ok
                active -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier.weight(1f).heightIn(min = AgvSizes.TouchMin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (done) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = color)
                    Spacer(modifier = Modifier.width(AgvSpacing.Xxs))
                }
                Text(
                    "${index + 1} $label",
                    style = if (active) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun FailureText(failure: ConnectFailure) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = AgvTheme.status.alarm)
        Spacer(modifier = Modifier.width(AgvSpacing.S))
        Text(
            ConnectFailureText.message(failure),
            style = MaterialTheme.typography.bodyMedium,
            color = AgvTheme.status.alarm,
        )
    }
}
