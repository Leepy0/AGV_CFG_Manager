package kr.agv.cfgmanager.ui.config

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.BuildConfig
import kr.agv.cfgmanager.apply.ApplyUi
import kr.agv.cfgmanager.apply.ResultAction
import kr.agv.cfgmanager.core.bridge.BridgeConfig
import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.ui.components.PrimaryActionButton
import kr.agv.cfgmanager.ui.components.SectionTitle
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import kr.agv.cfgmanager.ui.theme.AgvStepperText
import kr.agv.cfgmanager.ui.theme.AgvTheme

/** 설정 탭. 상태에 따라 폼 · 진행 패널 · 결과 카드 중 하나를 보인다 (docs/DEVELOPMENT_PLAN.md 5.3). */
@Composable
fun SettingsRoute(
    container: AppContainer,
    onOpenConsole: () -> Unit,
    onOpenBoards: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(container) }
    })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val running = ui.apply is ApplyUi.Running

    // 적용 중에는 뒤로 가기로 빠져나가지 않는다 (취소 버튼으로만).
    BackHandler(enabled = running) { }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Box(modifier = Modifier.weight(1f)) {
            when (val a = ui.apply) {
                is ApplyUi.Running -> ApplyProgressPanel(a, onCancel = vm::cancelApply)
                is ApplyUi.Done -> ResultPanel(
                    done = a,
                    notice = ui.notice,
                    onAction = { action ->
                        when (action) {
                            ResultAction.Retry -> vm.retryApply()
                            ResultAction.Console -> onOpenConsole()
                            ResultAction.Reboot -> vm.reboot()
                            ResultAction.Boards -> onOpenBoards()
                            ResultAction.CloseSetup -> vm.closeSetupMode()
                            ResultAction.Dismiss -> vm.dismissResult()
                        }
                    },
                )
                ApplyUi.Idle -> FormBody(ui, vm, container, onOpenBoards)
            }
        }
        if (ui.apply is ApplyUi.Idle) {
            BottomBar(ui, vm)
        }
    }
}

@Composable
private fun FormBody(ui: SettingsUi, vm: SettingsViewModel, container: AppContainer, onOpenBoards: () -> Unit) {
    val draft = ui.draft
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AgvSpacing.L),
        verticalArrangement = Arrangement.spacedBy(AgvSpacing.M),
    ) {
        Spacer(modifier = Modifier.size(AgvSpacing.S))
        ui.linkNote?.let { note ->
            NoticeLine(note, isError = false)
        }
        ui.notice?.let { NoticeLine(it, isError = true) }
        if (draft == null) {
            Text(
                "보드 탭에서 장치를 연결하면 설정 값이 여기에 채워집니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpenBoards,
                modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
            ) { Text("보드 탭으로") }
            return@Column
        }
        val editable = ui.linked
        AgvNumberRow(ui, vm, editable)
        SectionTitle("Wi-Fi")
        SsidField(ui, vm, editable)
        PasswordField(ui, vm, editable)

        SectionTitle("네트워크")
        IpModeRow(ui, vm, editable)
        ConfigInput(ui, vm, ConfigKey.IP, "IP", editable && draft.ipMode == IpMode.Manual, KeyboardType.Decimal, device = deviceText(ui, ConfigKey.IP))
        if (draft.ipMode == IpMode.Dhcp) {
            NoticeLine(
                "게이트웨이는 고정 IP 권장 (ACS agvOpCtrl 과 일치해야 함). DHCP 를 쓰면 DHCP 서버에서 IP 를 예약하세요.",
                isError = false,
            )
        }
        ConfigInput(ui, vm, ConfigKey.MASK, "마스크", editable, KeyboardType.Decimal, device = deviceText(ui, ConfigKey.MASK))
        ConfigInput(ui, vm, ConfigKey.GW, "게이트웨이", editable, KeyboardType.Decimal, device = deviceText(ui, ConfigKey.GW))
        ExpandRow(
            title = "고급 · DNS",
            summary = "장치: ${deviceText(ui, ConfigKey.DNS)} · ${deviceText(ui, ConfigKey.DNS2)}",
            expanded = FormGroup.Dns in ui.expanded,
            onToggle = { vm.toggleGroup(FormGroup.Dns) },
        )
        if (FormGroup.Dns in ui.expanded) {
            if (!ui.dnsSupported && ui.device != null) {
                NoticeLine("이 펌웨어는 DNS 설정을 지원하지 않습니다 (0.5.5 이상 필요). 값은 보내지 않습니다.", isError = false)
            }
            val dnsEnabled = editable && ui.dnsSupported
            ConfigInput(ui, vm, ConfigKey.DNS, "DNS", dnsEnabled, KeyboardType.Decimal, device = deviceText(ui, ConfigKey.DNS))
            ConfigInput(ui, vm, ConfigKey.DNS2, "보조 DNS", dnsEnabled, KeyboardType.Decimal, device = deviceText(ui, ConfigKey.DNS2))
        }

        SectionTitle("서버")
        ConfigInput(ui, vm, ConfigKey.SERVER, "서버 IP", editable, KeyboardType.Decimal, device = deviceText(ui, ConfigKey.SERVER))
        Row(horizontalArrangement = Arrangement.spacedBy(AgvSpacing.M)) {
            Column(modifier = Modifier.weight(1f)) {
                ConfigInput(ui, vm, ConfigKey.SERVER_PORT, "서버 포트", editable, KeyboardType.Number, device = deviceText(ui, ConfigKey.SERVER_PORT))
            }
            Column(modifier = Modifier.weight(1f)) {
                ConfigInput(ui, vm, ConfigKey.LOCAL_PORT, "수신 포트", editable, KeyboardType.Number, device = deviceText(ui, ConfigKey.LOCAL_PORT))
            }
        }

        ExpandRow(
            title = "고급 · RFID · 보드레이트 · 주기",
            summary = "RFID ${deviceText(ui, ConfigKey.RFID_DST)}:${deviceText(ui, ConfigKey.RFID_PORT)}",
            expanded = FormGroup.Advanced in ui.expanded,
            onToggle = { vm.toggleGroup(FormGroup.Advanced) },
        )
        if (FormGroup.Advanced in ui.expanded) {
            ConfigInput(ui, vm, ConfigKey.RFID_DST, "RFID 송신 IP (off · 255.255.255.255)", editable, KeyboardType.Decimal, device = deviceText(ui, ConfigKey.RFID_DST))
            ConfigInput(ui, vm, ConfigKey.RFID_PORT, "RFID 포트", editable, KeyboardType.Number, device = deviceText(ui, ConfigKey.RFID_PORT))
            ConfigInput(ui, vm, ConfigKey.MAIN_BAUD, "메인보드 보드레이트", editable, KeyboardType.Number, device = deviceText(ui, ConfigKey.MAIN_BAUD))
            ConfigInput(ui, vm, ConfigKey.RFID_BAUD, "RFID 보드레이트", editable, KeyboardType.Number, device = deviceText(ui, ConfigKey.RFID_BAUD))
            ConfigInput(ui, vm, ConfigKey.HB_MS, "heartbeat 주기 (ms, 0=끔)", editable, KeyboardType.Number, device = deviceText(ui, ConfigKey.HB_MS))
        }

        if (BuildConfig.DEBUG) {
            DeveloperSwitch(container)
        }
        Spacer(modifier = Modifier.size(AgvSpacing.Xl))
    }
}

/** 호기 번호: 28sp 숫자 칸과 −/+. 숫자 자판. */
@Composable
private fun AgvNumberRow(ui: SettingsUi, vm: SettingsViewModel, editable: Boolean) {
    val draft = ui.draft ?: return
    val agvText = draft.raw(ConfigKey.AGV)
    val message = ui.check?.messageFor(ConfigKey.AGV)
    Column(verticalArrangement = Arrangement.spacedBy(AgvSpacing.Xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgvSpacing.S)) {
            Text("호기 번호", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(AgvSizes.LabelColumn))
            OutlinedButton(
                onClick = { vm.stepAgv(-1) },
                enabled = editable,
                modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            OutlinedTextField(
                value = agvText,
                onValueChange = { vm.setValue(ConfigKey.AGV, it.filter(Char::isDigit).take(2)) },
                enabled = editable,
                singleLine = true,
                textStyle = AgvStepperText.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = message?.isError == true,
                modifier = Modifier.width(96.dp),
            )
            OutlinedButton(
                onClick = { vm.stepAgv(1) },
                enabled = editable,
                modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
            Spacer(modifier = Modifier.weight(1f))
            if (ui.device != null) {
                TextButton(
                    onClick = { vm.resetToDevice() },
                    enabled = editable,
                    modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
                ) { Text("장치값으로") }
            }
        }
        Text(
            "장치: ${deviceText(ui, ConfigKey.AGV)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FieldMessageLine(message)
    }
}

@Composable
private fun SsidField(ui: SettingsUi, vm: SettingsViewModel, editable: Boolean) {
    val draft = ui.draft ?: return
    FieldBlock(ui, ConfigKey.SSID, "SSID (Wi-Fi 이름)") {
        OutlinedTextField(
            value = draft.raw(ConfigKey.SSID),
            onValueChange = { vm.setValue(ConfigKey.SSID, it) },
            enabled = editable,
            singleLine = true,
            keyboardOptions = textOptions(KeyboardType.Ascii),
            isError = ui.check?.messageFor(ConfigKey.SSID)?.isError == true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PasswordField(ui: SettingsUi, vm: SettingsViewModel, editable: Boolean) {
    val draft = ui.draft ?: return
    val show = ui.showPassword
    FieldBlock(ui, ConfigKey.PASS, "비밀번호", deviceOverride = deviceText(ui, ConfigKey.PASS)) {
        OutlinedTextField(
            value = draft.raw(ConfigKey.PASS),
            onValueChange = { vm.setValue(ConfigKey.PASS, it) },
            enabled = editable,
            singleLine = true,
            placeholder = { Text("비우면 장치 비밀번호 유지") },
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = textOptions(if (show) KeyboardType.Ascii else KeyboardType.Password),
            trailingIcon = {
                TextButton(
                    onClick = { vm.togglePassword() },
                    enabled = editable,
                    modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
                ) { Text(if (show) "숨기기" else "보기") }
            },
            isError = ui.check?.messageFor(ConfigKey.PASS)?.isError == true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun IpModeRow(ui: SettingsUi, vm: SettingsViewModel, editable: Boolean) {
    val draft = ui.draft ?: return
    Column(verticalArrangement = Arrangement.spacedBy(AgvSpacing.Xxs)) {
        Text("IP 모드", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(AgvSpacing.S), verticalAlignment = Alignment.CenterVertically) {
            ModeOption("규칙", draft.ipMode == IpMode.Rule, enabled = false) { }
            ModeOption("DHCP", draft.ipMode == IpMode.Dhcp, enabled = editable) { vm.setIpMode(IpMode.Dhcp) }
            ModeOption("직접", draft.ipMode == IpMode.Manual, enabled = editable) { vm.setIpMode(IpMode.Manual) }
        }
        Text(
            "규칙(호기 번호로 IP 계산)은 프로필을 만든 뒤 쓸 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = AgvSizes.TouchMin)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}

/** 숫자 · IP 한 칸. [device] 는 장치 현재값 표시. */
@Composable
private fun ConfigInput(
    ui: SettingsUi,
    vm: SettingsViewModel,
    key: ConfigKey,
    label: String,
    enabled: Boolean,
    keyboard: KeyboardType,
    device: String,
) {
    val draft = ui.draft ?: return
    FieldBlock(ui, key, label, deviceOverride = device) {
        OutlinedTextField(
            value = draft.raw(key),
            onValueChange = { vm.setValue(key, it) },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            isError = ui.check?.messageFor(key)?.isError == true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 항목 한 줄 묶음: 이름 · 변경 표식 · 입력 · 장치 현재값 · 검증 안내 한 줄.
 * 변경은 아이콘 + 글자로 알린다(색만 쓰지 않는다).
 */
@Composable
private fun FieldBlock(
    ui: SettingsUi,
    key: ConfigKey,
    label: String,
    deviceOverride: String? = null,
    input: @Composable () -> Unit,
) {
    val changed = key in ui.changed
    val message = ui.check?.messageFor(key)
    Column(verticalArrangement = Arrangement.spacedBy(AgvSpacing.Xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (changed) {
                StatusIcon(Icons.Filled.Edit, AgvTheme.status.warn)
                Spacer(modifier = Modifier.width(AgvSpacing.Xxs))
                Text("변경", style = MaterialTheme.typography.labelMedium, color = AgvTheme.status.warn)
            }
        }
        input()
        Text(
            "장치: ${deviceOverride ?: deviceText(ui, key)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FieldMessageLine(message)
    }
}

@Composable
private fun FieldMessageLine(message: FieldMessage?) {
    message ?: return
    NoticeLine(message.text, isError = message.isError)
}

/** 아이콘 + 글자 한 줄 안내. 오류는 알람 색 + 경고 아이콘, 나머지는 보조 글자색. */
@Composable
private fun NoticeLine(text: String, isError: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        if (isError) {
            StatusIcon(Icons.Filled.Warning, AgvTheme.status.alarm)
        } else {
            StatusIcon(Icons.Filled.Info, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(AgvSpacing.Xxs))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) AgvTheme.status.alarm else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpandRow(title: String, summary: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AgvSizes.ListRowMin)
            .clickable(role = Role.Button, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusIcon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BottomBar(ui: SettingsUi, vm: SettingsViewModel) {
    val check = ui.check
    val summary = when {
        ui.draft == null -> "보드 탭에서 연결하세요"
        !ui.linked -> "연결이 끊겨 적용할 수 없습니다"
        check == null -> ""
        check.errorCount > 0 -> "변경 ${ui.changed.size}개 · 오류 ${check.errorCount}"
        ui.changed.isEmpty() -> "바뀐 값이 없습니다"
        else -> "변경 ${ui.changed.size}개 · 검증 통과"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AgvSpacing.L, vertical = AgvSpacing.S),
            verticalArrangement = Arrangement.spacedBy(AgvSpacing.S),
        ) {
            Text(summary, style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AgvSizes.TouchMin)
                    .clickable(role = Role.Checkbox, enabled = ui.linked) { vm.setSaveOnly(!ui.saveOnly) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = ui.saveOnly, onCheckedChange = null, enabled = ui.linked)
                Text("저장만 (재부팅은 나중에)", style = MaterialTheme.typography.bodyMedium)
            }
            PrimaryActionButton(
                text = if (ui.saveOnly) "저장만" else "적용 (저장 후 재부팅)",
                onClick = { vm.startApply() },
                enabled = ui.canApply,
            )
        }
    }
}

@Composable
private fun DeveloperSwitch(container: AppContainer) {
    val fake by container.settings.fakeBridge.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AgvSizes.ListRowMin)
            .clickable(role = Role.Switch) {
                container.settings.setFakeBridge(!fake)
                scope.launch {
                    container.session.disconnect(notice = "가짜 브리지 설정이 바뀌어 연결을 끊었습니다.")
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("개발자 옵션 · 가짜 브리지", style = MaterialTheme.typography.titleSmall)
            Text(
                "하드웨어 없이 가짜 보드로 동작합니다. 바꾸면 연결이 끊깁니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = fake, onCheckedChange = null)
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
}

/** 장치 현재값 문구. 장치가 DNS 를 지원하지 않으면 지원 안 함. 비밀번호는 길이만. */
private fun deviceText(ui: SettingsUi, key: ConfigKey): String {
    val device: BridgeConfig = ui.device ?: return "—"
    return when (key) {
        ConfigKey.PASS -> device.passLength?.let { "(${it}자)" } ?: "—"
        ConfigKey.DNS, ConfigKey.DNS2 -> if (ui.dnsSupported) device.canonical(key) ?: "—" else "지원 안 함"
        else -> device.canonical(key) ?: "—"
    }
}

private fun textOptions(keyboard: KeyboardType): KeyboardOptions = KeyboardOptions(
    keyboardType = keyboard,
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
)
