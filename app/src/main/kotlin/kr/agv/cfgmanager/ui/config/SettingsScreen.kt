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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.apply.ApplyUi
import kr.agv.cfgmanager.apply.ResultAction
import kr.agv.cfgmanager.core.bridge.BridgeConfig
import kr.agv.cfgmanager.core.bridge.CfgState
import kr.agv.cfgmanager.core.bridge.ConfigKey

import kr.agv.cfgmanager.ui.components.PrimaryActionButton
import kr.agv.cfgmanager.ui.components.SectionTitle
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import kr.agv.cfgmanager.ui.theme.AgvStepperText
import kr.agv.cfgmanager.ui.theme.AgvTheme

/**
 * 설정 탭. 상태에 따라 폼 · 진행 패널 · 결과 카드 중 하나를 보인다 (docs/DEVELOPMENT_PLAN.md 5.3).
 * [onEditProfile] 은 선택한 프로필의 편집 화면을 연다 (null 이면 프로필 목록).
 */
@Composable
fun SettingsRoute(
    container: AppContainer,
    onOpenConsole: () -> Unit,
    onOpenBoards: () -> Unit,
    onEditProfile: (String?) -> Unit,
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
                    nextAgv = ui.batchNextAgv,
                    onAction = { action ->
                        when (action) {
                            ResultAction.NextAgv -> {
                                vm.dismissResult()
                                onOpenBoards()
                            }
                            ResultAction.Retry -> vm.retryApply()
                            ResultAction.Console -> onOpenConsole()
                            ResultAction.Reboot -> vm.reboot()
                            ResultAction.Boards, ResultAction.NextBoard -> {
                                vm.dismissResult()
                                onOpenBoards()
                            }
                            ResultAction.CloseSetup -> vm.closeSetupMode()
                            ResultAction.Dismiss -> vm.dismissResult()
                        }
                    },
                )
                ApplyUi.Idle -> FormBody(ui, vm, onOpenBoards, onEditProfile)
            }
        }
        if (ui.apply is ApplyUi.Idle) {
            BottomBar(ui, vm)
        }
    }
}

@Composable
private fun FormBody(
    ui: SettingsUi,
    vm: SettingsViewModel,
    onOpenBoards: () -> Unit,
    onEditProfile: (String?) -> Unit,
) {
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
        if (ui.batch.enabled) {
            BatchLine(ui)
        }
        ui.batchAdvice?.let { advice -> BatchAdviceBlock(advice, vm) }
        ui.notice?.let { NoticeLine(it, isError = true) }
        if (draft == null) {
            Text(
                "보드를 연결하면 설정 값이 여기에 채워집니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpenBoards,
                modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
            ) { Text("다른 보드 고르기") }
            return@Column
        }
        val editable = ui.linked
        AgvNumberRow(ui, vm, editable)
        SummaryCard(ui, onEditProfile)
        SwitchRow(
            title = "연속 모드",
            summary = "끝나면 다음 호기로 이어서 세팅합니다",
            checked = ui.batch.enabled,
            enabled = true,
            onToggle = { vm.setBatch(!ui.batch.enabled) },
        )
        // 프로필이 값을 다 채우므로 보통은 호기 번호만 바꾸고 [적용] 한다. 세부 값은 접어 두고, 오류가 있으면 펼친다.
        val detailsOpen = FormGroup.Details in ui.expanded || (ui.check?.errorCount ?: 0) > 0
        ExpandRow(
            title = "세부 값 편집",
            summary = "Wi-Fi 비밀번호 · 네트워크 · 서버 · 고급",
            expanded = detailsOpen,
            onToggle = { vm.toggleGroup(FormGroup.Details) },
        )
        if (!detailsOpen) {
            Spacer(modifier = Modifier.size(AgvSpacing.Xl))
            return@Column
        }
        SwitchRow(
            title = "저장만",
            summary = "재부팅은 나중에 합니다",
            checked = ui.saveOnly,
            enabled = ui.linked,
            onToggle = { vm.setSaveOnly(!ui.saveOnly) },
        )
        SectionTitle("Wi-Fi")
        SsidField(ui, vm, editable)
        PasswordField(ui, vm, editable)

        SectionTitle("네트워크")
        IpModeRow(ui, vm, editable)
        ConfigInput(ui, vm, ConfigKey.IP, "IP", editable && draft.ipMode == IpMode.Manual, KeyboardType.Decimal, device = deviceText(ui, ConfigKey.IP))
        ui.ruleMismatchIp?.let { ruleIp ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    NoticeLine("규칙과 다름 · 규칙값 $ruleIp", isError = false)
                }
                TextButton(
                    onClick = { vm.useRuleIp() },
                    enabled = editable,
                    modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
                ) { Text("규칙값으로") }
            }
        }
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

        Spacer(modifier = Modifier.size(AgvSpacing.Xl))
    }
}

/** 프로필 선택(드롭다운) + [편집]. 프로필을 고르면 호기 번호만 남기고 값을 채운다 (F-E4). */
@Composable
private fun ProfileRow(ui: SettingsUi, vm: SettingsViewModel, onEditProfile: (String?) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("프로필", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(AgvSizes.LabelColumn))
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { menuOpen = true },
                enabled = ui.profiles.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
            ) {
                Text(
                    ui.selectedProfile?.name ?: if (ui.profiles.isEmpty()) "불러오는 중" else "프로필 선택",
                    modifier = Modifier.weight(1f),
                )
                StatusIcon(Icons.Filled.KeyboardArrowDown, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                ui.profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile.name) },
                        onClick = {
                            menuOpen = false
                            vm.selectProfile(profile.id)
                        },
                    )
                }
            }
        }
        // 프로필을 고르지 않았으면 편집할 대상이 없다. "새 프로필" 로 빠지지 않도록 막는다 (프로필 목록에서 만든다).
        TextButton(
            onClick = { ui.selectedProfile?.let { onEditProfile(it.id) } },
            enabled = ui.selectedProfile != null,
            modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
        ) { Text("편집") }
    }
}

/** 연속 세팅 진행 줄: 다음 호기와 프로필. 배너 문구는 보드 탭과 같다 (5.2). */
@Composable
private fun BatchLine(ui: SettingsUi) {
    val next = ui.batchNextAgv
    val profile = ui.batch.profileName
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusIcon(Icons.Filled.Info, MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.width(AgvSpacing.Xxs))
        Text(
            "연속 세팅 · 다음: ${next ?: "-"}호기${profile?.let { " · $it" }.orEmpty()}",
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/** 이미 설정된 보드 경고 (F-B2). 자동 채움은 멈춘 상태이고 [덮어쓰기] 를 눌러야 채운다. */
@Composable
private fun BatchAdviceBlock(advice: CfgState, vm: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(AgvSpacing.Xxs)) {
        NoticeLine(
            "이미 설정된 보드입니다 (설정 상태: ${if (advice == CfgState.TRIAL) "확인 대기" else "저장됨"}). 자동 채움을 멈췄습니다. 덮어쓰려면 [덮어쓰기] 를 누르세요.",
            isError = true,
        )
        OutlinedButton(
            onClick = { vm.overwriteBatch() },
            modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
        ) { Text("덮어쓰기") }
    }
}

/** 호기 번호: 28sp 숫자 칸과 −/+. 숫자 자판. 다음 번호 제안(이력 기준)을 함께 보인다. */
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
                onValueChange = { vm.setValue(ConfigKey.AGV, it) },
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
        }
        // [장치값으로] 는 숫자 줄에 두면 좁아서 글자가 세로로 쪼개진다. 장치 값 줄의 오른쪽에 둔다.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "장치: ${deviceText(ui, ConfigKey.AGV)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (ui.device != null) {
                TextButton(
                    onClick = { vm.resetToDevice() },
                    enabled = editable,
                    modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
                ) { Text("장치값으로") }
            }
        }
        ui.nextAgvHint?.let { hint ->
            if (hint.toString() != agvText) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        NoticeLine("이 프로필의 다음 번호 제안: ${"%02d".format(hint)}호기", isError = false)
                    }
                    TextButton(
                        onClick = { vm.setAgvHint(hint) },
                        enabled = editable,
                        modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
                    ) { Text("${hint}호기로") }
                }
            }
        }
        FieldMessageLine(message)
    }
}

/** 지금 적용될 핵심 값 요약. 바뀌는 항목에는 "변경" 표식이 붙는다. 세부 값을 열지 않아도 무엇이 나가는지 보인다. */
@Composable
private fun SummaryCard(ui: SettingsUi, onEditProfile: (String?) -> Unit) {
    val draft = ui.draft ?: return
    val pass = draft.raw(ConfigKey.PASS)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(AgvSpacing.M),
            verticalArrangement = Arrangement.spacedBy(AgvSpacing.S),
        ) {
            // 프로필은 보드를 고르기 전에 정했다. 여기서는 어느 프로필 값인지 보이고, 값을 고치러 갈 수만 있게 한다.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "적용할 값 · ${ui.selectedProfile?.name ?: "프로필 없음"}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { ui.selectedProfile?.let { onEditProfile(it.id) } },
                    enabled = ui.selectedProfile != null,
                    modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
                ) { Text("프로필 편집") }
            }
            SummaryLine("Wi-Fi", draft.raw(ConfigKey.SSID).ifEmpty { "—" }, ConfigKey.SSID in ui.changed)
            SummaryLine(
                "비밀번호",
                if (pass.isEmpty()) "장치에 있는 값 유지" else "입력됨 (${pass.length}자)",
                ConfigKey.PASS in ui.changed,
            )
            SummaryLine("IP", draft.raw(ConfigKey.IP).ifEmpty { "—" }, ConfigKey.IP in ui.changed)
            SummaryLine(
                "서버",
                "${draft.raw(ConfigKey.SERVER).ifEmpty { "—" }}:${draft.raw(ConfigKey.SERVER_PORT)}",
                ConfigKey.SERVER in ui.changed || ConfigKey.SERVER_PORT in ui.changed,
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, changed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(AgvSizes.LabelColumn))
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (changed) {
            StatusIcon(Icons.Filled.Edit, AgvTheme.status.warn)
            Spacer(modifier = Modifier.width(AgvSpacing.Xxs))
            Text("변경", style = MaterialTheme.typography.labelMedium, color = AgvTheme.status.warn)
        }
    }
}

@Composable
private fun SsidField(ui: SettingsUi, vm: SettingsViewModel, editable: Boolean) {
    val draft = ui.draft ?: return
    var menuOpen by remember { mutableStateOf(false) }
    FieldBlock(ui, ConfigKey.SSID, "SSID (Wi-Fi 이름)") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft.raw(ConfigKey.SSID),
                onValueChange = { vm.setSsidText(it) },
                enabled = editable,
                singleLine = true,
                keyboardOptions = textOptions(KeyboardType.Ascii),
                isError = ui.check?.messageFor(ConfigKey.SSID)?.isError == true,
                modifier = Modifier.weight(1f),
            )
            Box {
                TextButton(
                    onClick = { menuOpen = true },
                    enabled = editable && ui.networkSsids.isNotEmpty(),
                    modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
                ) { Text("목록") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ui.networkSsids.forEach { ssid ->
                        DropdownMenuItem(
                            text = { Text(ssid) },
                            onClick = {
                                menuOpen = false
                                vm.setSsidText(ssid)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordField(ui: SettingsUi, vm: SettingsViewModel, editable: Boolean) {
    val draft = ui.draft ?: return
    val show = ui.showPassword
    FieldBlock(ui, ConfigKey.PASS, "비밀번호", deviceOverride = deviceText(ui, ConfigKey.PASS)) {
        Column(verticalArrangement = Arrangement.spacedBy(AgvSpacing.Xxs)) {
            OutlinedTextField(
                value = draft.raw(ConfigKey.PASS),
                onValueChange = { vm.setValue(ConfigKey.PASS, it) },
                enabled = editable,
                singleLine = true,
                placeholder = { Text("비우면 장치 비밀번호 유지 · - 는 오픈 AP") },
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
            Row(
                modifier = Modifier
                    .heightIn(min = AgvSizes.TouchMin)
                    .clickable(role = Role.Checkbox, enabled = editable) { vm.setRememberPassword(!ui.rememberPassword) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = ui.rememberPassword, onCheckedChange = null, enabled = editable)
                Text("이 비밀번호 저장 (적용 성공 시 SSID 라이브러리에)", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun IpModeRow(ui: SettingsUi, vm: SettingsViewModel, editable: Boolean) {
    val draft = ui.draft ?: return
    Column(verticalArrangement = Arrangement.spacedBy(AgvSpacing.Xxs)) {
        Text("IP 모드", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(AgvSpacing.S), verticalAlignment = Alignment.CenterVertically) {
            ModeOption("규칙", draft.ipMode == IpMode.Rule, enabled = editable && ui.ruleAvailable) { vm.setIpMode(IpMode.Rule) }
            ModeOption("DHCP", draft.ipMode == IpMode.Dhcp, enabled = editable) { vm.setIpMode(IpMode.Dhcp) }
            ModeOption("직접", draft.ipMode == IpMode.Manual, enabled = editable) { vm.setIpMode(IpMode.Manual) }
        }
        Text(
            if (ui.ruleAvailable) {
                "규칙: 호기 번호로 IP 를 계산합니다${ui.ruleIp?.let { " (지금 $it)" }.orEmpty()}."
            } else {
                "규칙(호기 번호로 IP 계산)은 규칙이 있는 프로필을 고르면 쓸 수 있습니다."
            },
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
        ui.draft == null -> "세팅 탭에서 연결하세요"
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
            PrimaryActionButton(
                text = if (ui.saveOnly) "저장만" else "적용 (저장 후 재부팅)",
                onClick = { vm.startApply() },
                enabled = ui.canApply,
            )
        }
    }
}

/** 켜고 끄는 선택 한 줄: 제목 · 한 줄 설명 · 스위치. 줄 전체가 누르는 영역이다 (48dp 이상). */
@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AgvSizes.ListRowMin)
            .clickable(enabled = enabled, role = Role.Switch, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
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
