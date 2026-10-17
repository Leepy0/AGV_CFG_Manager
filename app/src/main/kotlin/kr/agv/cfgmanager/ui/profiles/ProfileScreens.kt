package kr.agv.cfgmanager.ui.profiles

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.produceState
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kr.agv.cfgmanager.ui.theme.AgvQrColors
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.core.profiles.IpRule
import kr.agv.cfgmanager.core.profiles.Profile
import kr.agv.cfgmanager.core.profiles.ProfileField
import kr.agv.cfgmanager.core.profiles.ProfileIssue
import kr.agv.cfgmanager.share.ShareFiles
import kr.agv.cfgmanager.ui.components.ConfirmDialog
import kr.agv.cfgmanager.ui.components.PrimaryActionButton
import kr.agv.cfgmanager.ui.components.SectionTitle
import kr.agv.cfgmanager.ui.components.StatePanel
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import kr.agv.cfgmanager.ui.theme.AgvTheme
import java.time.LocalDate

/** 프로필 목록 (5.6): 목록 · 새로 만들기 · 가져오기 · 내보내기. [onOpenProfile] 의 null 은 새 프로필. */
@Composable
fun ProfileListRoute(container: AppContainer, onOpenProfile: (String?) -> Unit) {
    val vm: ProfilesViewModel = viewModel(factory = viewModelFactory {
        initializer { ProfilesViewModel(container) }
    })
    val doc by vm.document.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val notice by container.profiles.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportOpen by remember { mutableStateOf(false) }
    var includePasswords by remember { mutableStateOf(false) }
    var includeLibrary by remember { mutableStateOf(true) }
    var warnPasswords by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf<String?>(null) }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = exportText
        exportText = null
        if (uri != null && text != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    }.isSuccess
                }
                if (!ok) vm.reportWriteFailure()
            }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                }
                if (text == null) {
                    vm.stageImportFailed()
                } else {
                    vm.stageImport(text) { outcome -> outcome.needsPassword?.let { onOpenProfile(it) } }
                }
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { text ->
            vm.stageImport(text) { outcome -> outcome.needsPassword?.let { onOpenProfile(it) } }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scanLauncher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("프로필 QR 을 가운데에 맞추세요")
                    .setBeepEnabled(false)
                    .setOrientationLocked(true),
            )
        } else {
            vm.reportCameraDenied()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = AgvSpacing.L)) {
        Spacer(modifier = Modifier.size(AgvSpacing.S))
        Row(horizontalArrangement = Arrangement.spacedBy(AgvSpacing.S)) {
            OutlinedButton(
                onClick = { openLauncher.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.weight(1f).heightIn(min = AgvSizes.TouchMin),
            ) { Text("파일 가져오기") }
            OutlinedButton(
                onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.weight(1f).heightIn(min = AgvSizes.TouchMin),
            ) { Text("QR 가져오기") }
            OutlinedButton(onClick = { exportOpen = true }, modifier = Modifier.weight(1f).heightIn(min = AgvSizes.TouchMin)) {
                Text("내보내기")
            }
        }
        notice?.let { NoticeLine(it, isError = false) }
        message?.let { NoticeLine(it, isError = true) }

        val profiles = doc?.profiles
        when {
            profiles == null -> StatePanel(title = "불러오는 중", body = "프로필을 읽고 있습니다.")
            profiles.isEmpty() -> StatePanel(
                title = "프로필이 없습니다",
                body = "현장마다 공통 값을 프로필로 만들면 호기 번호만 바꿔 설정할 수 있습니다.",
                primary = kr.agv.cfgmanager.ui.components.ActionSpec("새 프로필") { onOpenProfile(null) },
            )
            else -> LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileRow(profile, active = profile.id == doc?.lastProfileId) { onOpenProfile(profile.id) }
                    HorizontalDivider()
                }
            }
        }
        PrimaryActionButton(
            text = "새 프로필",
            onClick = { onOpenProfile(null) },
            modifier = Modifier.padding(vertical = AgvSpacing.M),
        )
    }

    if (exportOpen) {
        ExportDialog(
            includePasswords = includePasswords,
            includeLibrary = includeLibrary,
            onIncludeLibrary = { includeLibrary = it },
            onIncludePasswords = { wanted -> if (wanted) warnPasswords = true else includePasswords = false },
            onSaveFile = {
                exportOpen = false
                vm.buildExport(includePasswords, includeLibrary) { text ->
                    exportText = text
                    createLauncher.launch("agvcfg-${LocalDate.now()}.agvcfg.json")
                }
            },
            onShare = {
                exportOpen = false
                vm.buildExport(includePasswords, includeLibrary) { text ->
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) { ShareFiles.writeText(context, "agvcfg-${LocalDate.now()}.agvcfg.json", text) }
                        context.startActivity(Intent.createChooser(ShareFiles.sendIntent(uri, "application/json"), "프로필 공유"))
                    }
                }
            },
            onDismiss = { exportOpen = false },
        )
    }
    if (warnPasswords) {
        ConfirmDialog(
            title = "비밀번호를 파일에 넣습니다",
            text = "Wi-Fi 비밀번호와 BLE 비밀번호가 평문으로 들어갑니다. 파일을 안전한 곳에만 보관하고, 공유 뒤에는 지우세요.",
            confirmLabel = "포함",
            danger = true,
            onConfirm = {
                includePasswords = true
                warnPasswords = false
            },
            onDismiss = { warnPasswords = false },
        )
    }
    pending?.let { p ->
        ConfirmDialog(
            title = "같은 이름의 프로필이 있습니다",
            text = "${p.conflicts.joinToString()} 프로필을 가져온 것으로 바꿀까요? 덮어쓰지 않으면 같은 이름은 건너뜁니다.",
            confirmLabel = "덮어쓰기",
            dismissLabel = "건너뛰기",
            danger = true,
            onConfirm = { vm.confirmImport(overwrite = true) { outcome -> outcome.needsPassword?.let { onOpenProfile(it) } } },
            onDismiss = { vm.skipConflicts { outcome -> outcome.needsPassword?.let { onOpenProfile(it) } } },
        )
    }
}

@Composable
private fun ProfileRow(profile: Profile, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AgvSizes.ListRowMin)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = AgvSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium)
            Text(
                ruleSummary(profile),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                buildString {
                    append(profile.lastUsedAt?.let { "마지막 사용 ${it.take(10)}" } ?: "아직 사용 안 함")
                    if (profile.blePassword.isEmpty()) append(" · BLE 비밀번호 입력 필요")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (profile.blePassword.isEmpty()) AgvTheme.status.warn else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (active) {
            Icon(Icons.Filled.Info, contentDescription = "마지막 사용 프로필", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun ruleSummary(profile: Profile): String = when (val rule = profile.ipRule) {
    is IpRule.Offset -> "IP 규칙: ${rule.baseNetwork} 기준 +${rule.offset}"
    IpRule.Dhcp -> "IP: DHCP"
    IpRule.Manual -> "IP: 직접 입력"
}

@Composable
private fun ExportDialog(
    includePasswords: Boolean,
    includeLibrary: Boolean,
    onIncludeLibrary: (Boolean) -> Unit,
    onIncludePasswords: (Boolean) -> Unit,
    onSaveFile: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("프로필 내보내기", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AgvSpacing.S)) {
                CheckRow("SSID 라이브러리도 포함", includeLibrary) { onIncludeLibrary(!includeLibrary) }
                CheckRow("비밀번호 포함 (평문)", includePasswords) { onIncludePasswords(!includePasswords) }
                Text(
                    if (includePasswords) "비밀번호가 들어갑니다. 파일을 안전하게 보관하세요." else "비밀번호는 기본으로 빠집니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (includePasswords) AgvTheme.status.alarm else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSaveFile, modifier = Modifier.heightIn(min = AgvSizes.TouchMin)) { Text("파일로 저장") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onShare, modifier = Modifier.heightIn(min = AgvSizes.TouchMin)) { Text("공유") }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AgvSizes.TouchMin)) { Text("취소") }
            }
        },
    )
}

/** 프로필 편집 (5.6). [profileId] 가 null 이면 새 프로필. */
@Composable
fun ProfileEditRoute(container: AppContainer, profileId: String?, onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    val vm: ProfileEditViewModel = viewModel(
        key = "profile-edit-${profileId ?: "new"}",
        factory = viewModelFactory { initializer { ProfileEditViewModel(container, profileId) } },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                ProfileEditEvent.Done -> onBack()
                is ProfileEditEvent.Opened -> onOpenProfile(event.id)
            }
        }
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.importPhoneWifi(context, withSsid = granted)
    }
    var showBlePassword by remember { mutableStateOf(false) }
    var qrOpen by remember { mutableStateOf(false) }
    val form = ui.form
    if (!ui.loaded) {
        StatePanel(title = "불러오는 중", body = "프로필을 읽고 있습니다.")
        return
    }
    if (form == null) {
        StatePanel(title = "프로필을 찾을 수 없습니다", body = "목록으로 돌아가 다시 고르세요.")
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AgvSpacing.L),
            verticalArrangement = Arrangement.spacedBy(AgvSpacing.M),
        ) {
            Spacer(modifier = Modifier.size(AgvSpacing.S))
            ui.notice?.let { NoticeLine(it, isError = false) }
            OutlinedButton(
                onClick = {
                    val granted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (granted) vm.importPhoneWifi(context, withSsid = true) else locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
            ) { Text("현재 Wi-Fi 에서 가져오기") }

            SectionTitle("프로필")
            FormField("이름", form.name, { v -> vm.update { it.copy(name = v) } }, issues = ui.issues, field = ProfileField.Name)
            FormField("메모", form.notes, { v -> vm.update { it.copy(notes = v) } }, issues = ui.issues, field = null)

            SectionTitle("Wi-Fi")
            FormField("SSID", form.ssid, { v -> vm.update { it.copy(ssid = v) } }, issues = ui.issues, field = ProfileField.Ssid)
            Text(
                if (ui.libraryPassword) "SSID 라이브러리에 저장된 비밀번호가 있습니다." else "SSID 라이브러리에 비밀번호가 없습니다. 적용할 때 입력하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("IP 규칙")
            RuleSection(form, ui.issues, onMode = { m -> vm.update { it.copy(ruleMode = m) } }, onUpdate = vm::update)

            SectionTitle("네트워크")
            FormField("마스크", form.mask, { v -> vm.update { it.copy(mask = v) } }, issues = ui.issues, field = ProfileField.Mask, keyboard = KeyboardType.Decimal)
            FormField("게이트웨이", form.gw, { v -> vm.update { it.copy(gw = v) } }, issues = ui.issues, field = ProfileField.Gw, keyboard = KeyboardType.Decimal)
            FormField("DNS (auto 또는 IP)", form.dns, { v -> vm.update { it.copy(dns = v) } }, issues = ui.issues, field = ProfileField.Dns, keyboard = KeyboardType.Ascii)
            FormField("보조 DNS (auto 또는 IP)", form.dns2, { v -> vm.update { it.copy(dns2 = v) } }, issues = ui.issues, field = ProfileField.Dns2, keyboard = KeyboardType.Ascii)

            SectionTitle("서버")
            FormField("서버 IP", form.server, { v -> vm.update { it.copy(server = v) } }, issues = ui.issues, field = ProfileField.Server, keyboard = KeyboardType.Decimal)
            FormField("서버 포트", form.serverPort, { v -> vm.update { it.copy(serverPort = v) } }, issues = ui.issues, field = ProfileField.ServerPort, keyboard = KeyboardType.Number)
            FormField("수신 포트", form.localPort, { v -> vm.update { it.copy(localPort = v) } }, issues = ui.issues, field = ProfileField.LocalPort, keyboard = KeyboardType.Number)

            SectionTitle("고급")
            FormField("RFID 송신 IP (off 또는 IP)", form.rfidDst, { v -> vm.update { it.copy(rfidDst = v) } }, issues = ui.issues, field = ProfileField.RfidDst, keyboard = KeyboardType.Ascii)
            FormField("RFID 포트", form.rfidPort, { v -> vm.update { it.copy(rfidPort = v) } }, issues = ui.issues, field = ProfileField.RfidPort, keyboard = KeyboardType.Number)
            FormField("메인보드 보드레이트", form.mainBaud, { v -> vm.update { it.copy(mainBaud = v) } }, issues = ui.issues, field = ProfileField.MainBaud, keyboard = KeyboardType.Number)
            FormField("RFID 보드레이트", form.rfidBaud, { v -> vm.update { it.copy(rfidBaud = v) } }, issues = ui.issues, field = ProfileField.RfidBaud, keyboard = KeyboardType.Number)
            FormField("heartbeat 주기 (ms, 0=끔)", form.hbMs, { v -> vm.update { it.copy(hbMs = v) } }, issues = ui.issues, field = ProfileField.HbMs, keyboard = KeyboardType.Number)

            SectionTitle("보드 연결")
            OutlinedTextField(
                value = form.blePassword,
                onValueChange = { v -> vm.update { it.copy(blePassword = v) } },
                singleLine = true,
                label = { Text("BLE 비밀번호") },
                visualTransformation = if (showBlePassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { showBlePassword = !showBlePassword }, modifier = Modifier.heightIn(min = AgvSizes.TouchMin)) {
                        Text(if (showBlePassword) "숨기기" else "보기")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ui.issues.filter { it.field == ProfileField.BlePassword }.forEach { IssueLine(it) }
            Spacer(modifier = Modifier.size(AgvSpacing.Xl))
        }

        HorizontalDivider()
        Column(
            modifier = Modifier.padding(horizontal = AgvSpacing.L, vertical = AgvSpacing.S),
            verticalArrangement = Arrangement.spacedBy(AgvSpacing.S),
        ) {
            if (!ui.isNew) {
                Row(horizontalArrangement = Arrangement.spacedBy(AgvSpacing.S)) {
                    OutlinedButton(onClick = { vm.duplicate() }, modifier = Modifier.weight(1f).heightIn(min = AgvSizes.TouchMin)) {
                        Text("복제")
                    }
                    OutlinedButton(onClick = { qrOpen = true }, modifier = Modifier.weight(1f).heightIn(min = AgvSizes.TouchMin)) {
                        Text("QR 보기")
                    }
                    OutlinedButton(onClick = { vm.askDelete(true) }, modifier = Modifier.weight(1f).heightIn(min = AgvSizes.TouchMin)) {
                        Text("삭제")
                    }
                }
            }
            PrimaryActionButton(text = "저장", onClick = { vm.save() })
        }
    }

    if (qrOpen) {
        ProfileQrDialog(vm = vm, onClose = { qrOpen = false })
    }

    if (ui.confirmDelete) {
        ConfirmDialog(
            title = "\"${form.name}\" 프로필을 삭제할까요?",
            text = "삭제하면 되돌릴 수 없습니다. SSID 라이브러리의 비밀번호는 지워지지 않습니다.",
            confirmLabel = "삭제",
            danger = true,
            onConfirm = { vm.delete() },
            onDismiss = { vm.askDelete(false) },
        )
    }
}

/** IP 규칙 모드 3개와 기준 네트워크 · 오프셋 · 예시 계산 (F-P3). */
@Composable
private fun RuleSection(
    form: ProfileForm,
    issues: List<ProfileIssue>,
    onMode: (RuleMode) -> Unit,
    onUpdate: ((ProfileForm) -> ProfileForm) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AgvSpacing.S)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AgvSpacing.M), verticalAlignment = Alignment.CenterVertically) {
            RuleOption("규칙", form.ruleMode == RuleMode.Offset) { onMode(RuleMode.Offset) }
            RuleOption("DHCP", form.ruleMode == RuleMode.Dhcp) { onMode(RuleMode.Dhcp) }
            RuleOption("직접", form.ruleMode == RuleMode.Manual) { onMode(RuleMode.Manual) }
        }
        if (form.ruleMode == RuleMode.Offset) {
            FormField("기준 네트워크", form.baseNetwork, { v -> onUpdate { it.copy(baseNetwork = v) } }, issues = issues, field = ProfileField.IpRule, keyboard = KeyboardType.Decimal)
            FormField("오프셋", form.offset, { v -> onUpdate { it.copy(offset = v) } }, issues = emptyList(), field = null, keyboard = KeyboardType.Number)
            val example = form.exampleIp(7)
            Text(
                if (example != null) "예: 호기 7 → $example" else "예: 이 오프셋으로는 호기 7 의 IP 를 만들 수 없습니다.",
                style = MaterialTheme.typography.titleSmall,
            )
        } else if (form.ruleMode == RuleMode.Dhcp) {
            Text("DHCP 서버에서 IP 를 예약하세요. 게이트웨이는 고정 IP 권장.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("적용할 때마다 IP 를 직접 입력합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        issues.filter { it.field == ProfileField.IpRule && form.ruleMode != RuleMode.Offset }.forEach { IssueLine(it) }
    }
}

@Composable
private fun RuleOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.heightIn(min = AgvSizes.TouchMin).clickable(role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 입력 한 칸: 이름 · 입력 · 해당 항목의 검사 문구. 항목이 null 이면 검사 문구를 붙이지 않는다. */
@Composable
private fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    issues: List<ProfileIssue>,
    field: ProfileField?,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    val own = if (field == null) emptyList() else issues.filter { it.field == field }
    Column(verticalArrangement = Arrangement.spacedBy(AgvSpacing.Xxs)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            isError = own.any { it.isError },
            modifier = Modifier.fillMaxWidth(),
        )
        own.forEach { IssueLine(it) }
    }
}

@Composable
private fun IssueLine(issue: ProfileIssue) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (issue.isError) Icons.Filled.Warning else Icons.Filled.Info,
            contentDescription = null,
            tint = if (issue.isError) AgvTheme.status.alarm else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(AgvSpacing.Xxs))
        Text(
            issue.text,
            style = MaterialTheme.typography.bodySmall,
            color = if (issue.isError) AgvTheme.status.alarm else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoticeLine(text: String, isError: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) AgvTheme.status.alarm else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.heightIn(min = AgvSizes.TouchMin).clickable(role = Role.Checkbox, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val QR_PX = 720

private sealed interface QrState {
    data object Loading : QrState
    data class Ready(val bitmap: Bitmap) : QrState
    data class Failed(val message: String) : QrState
}

/**
 * 저장된 프로필을 QR 로 보인다 (F-P5). 비밀번호는 기본으로 뺀다. 넣으려면 경고를 확인한다.
 * 흰 바탕은 스캔을 위해 고정한다 (다크 테마에서도 같다).
 */
@Composable
private fun ProfileQrDialog(vm: ProfileEditViewModel, onClose: () -> Unit) {
    var includePasswords by remember { mutableStateOf(false) }
    var warnPasswords by remember { mutableStateOf(false) }
    val state by produceState<QrState>(QrState.Loading, includePasswords) {
        value = QrState.Loading
        val text = vm.qrText(includePasswords)
        value = if (text.isSuccess) {
            val bitmap = withContext(Dispatchers.Default) { profileQrBitmap(text.getOrThrow(), QR_PX) }
            QrState.Ready(bitmap)
        } else {
            QrState.Failed(text.exceptionOrNull()?.message ?: "QR 을 만들지 못했습니다.")
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("프로필 QR", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AgvSpacing.S),
            ) {
                when (val current = state) {
                    QrState.Loading -> Text("만드는 중입니다.", style = MaterialTheme.typography.bodyMedium)
                    is QrState.Ready -> Surface(color = AgvQrColors.Background) {
                        Image(
                            bitmap = current.bitmap.asImageBitmap(),
                            contentDescription = "프로필 QR 코드",
                            modifier = Modifier.size(AgvSizes.QrSize).padding(AgvSpacing.S),
                        )
                    }
                    is QrState.Failed -> Text(current.message, style = MaterialTheme.typography.bodyMedium, color = AgvTheme.status.alarm)
                }
                CheckRow("비밀번호 포함 (평문)", includePasswords) {
                    if (includePasswords) includePasswords = false else warnPasswords = true
                }
                Text(
                    if (includePasswords) {
                        "QR 에 BLE 비밀번호가 들어갑니다. 화면을 다른 사람에게 보이지 마세요."
                    } else {
                        "비밀번호는 빠집니다. 받는 휴대폰에서 BLE 비밀번호를 다시 입력합니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (includePasswords) AgvTheme.status.alarm else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = AgvSizes.TouchMin)) { Text("닫기") }
        },
    )
    if (warnPasswords) {
        ConfirmDialog(
            title = "QR 에 비밀번호를 넣습니다",
            text = "BLE 비밀번호가 QR 에 평문으로 담깁니다. QR 을 찍을 휴대폰에만 보이게 하세요.",
            confirmLabel = "포함",
            danger = true,
            onConfirm = {
                includePasswords = true
                warnPasswords = false
            },
            onDismiss = { warnPasswords = false },
        )
    }
}
