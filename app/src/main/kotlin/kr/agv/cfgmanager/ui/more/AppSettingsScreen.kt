package kr.agv.cfgmanager.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.BuildConfig
import kr.agv.cfgmanager.data.AppSettings
import kr.agv.cfgmanager.data.ThemeMode
import kr.agv.cfgmanager.ui.components.SectionTitle
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing

/** 설정 화면 (F-G1): 테마 · 스캔 시간 · 연속 모드 기본 · 응답 유휴 시간 · 업데이트 확인(자리) · 디버그 가짜 브리지. */
@Composable
fun AppSettingsScreen(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AgvSpacing.L),
        verticalArrangement = Arrangement.spacedBy(AgvSpacing.S),
    ) {
        SectionTitle("화면")
        Text("테마", style = MaterialTheme.typography.titleSmall)
        ThemeMode.entries.forEach { mode ->
            ChoiceRow(
                label = when (mode) {
                    ThemeMode.System -> "휴대폰 설정 따르기"
                    ThemeMode.Light -> "라이트"
                    ThemeMode.Dark -> "다크"
                },
                selected = settings.themeMode == mode,
                onSelect = { container.settings.update { it.copy(themeMode = mode) } },
            )
        }

        SectionTitle("보드 찾기 · 연결")
        StepperRow(
            label = "스캔 시간",
            value = "${settings.scanSeconds}초",
            canDown = settings.scanSeconds > AppSettings.SCAN_MIN_SECONDS,
            canUp = settings.scanSeconds < AppSettings.SCAN_MAX_SECONDS,
            onDown = { container.settings.update { it.copy(scanSeconds = it.scanSeconds - AppSettings.SCAN_STEP_SECONDS) } },
            onUp = { container.settings.update { it.copy(scanSeconds = it.scanSeconds + AppSettings.SCAN_STEP_SECONDS) } },
        )
        StepperRow(
            label = "응답 유휴 시간",
            value = "${settings.responseIdleMs}ms",
            canDown = settings.responseIdleMs > AppSettings.IDLE_MIN_MS,
            canUp = settings.responseIdleMs < AppSettings.IDLE_MAX_MS,
            onDown = { container.settings.update { it.copy(responseIdleMs = it.responseIdleMs - AppSettings.IDLE_STEP_MS) } },
            onUp = { container.settings.update { it.copy(responseIdleMs = it.responseIdleMs + AppSettings.IDLE_STEP_MS) } },
        )
        Text(
            "응답이 잘리거나 느린 휴대폰이면 올리세요 (150~800ms, 기본 250ms). 다음 연결부터 적용됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(
            label = "연속 모드를 기본으로 켜기",
            note = "설정 탭에서 연속 모드를 켤 때의 기본값입니다.",
            checked = settings.batchByDefault,
            onToggle = { container.settings.update { it.copy(batchByDefault = !it.batchByDefault) } },
        )

        SectionTitle("업데이트")
        SwitchRow(
            label = "업데이트 자동 확인",
            note = "준비 중. 업데이트 기능이 들어오면 이 설정으로 켜고 끕니다.",
            checked = settings.autoUpdateCheck,
            onToggle = { container.settings.update { it.copy(autoUpdateCheck = !it.autoUpdateCheck) } },
        )

        if (BuildConfig.DEBUG) {
            SectionTitle("개발자 옵션")
            SwitchRow(
                label = "가짜 브리지",
                note = "하드웨어 없이 가짜 보드로 동작합니다. 바꾸면 연결이 끊깁니다.",
                checked = settings.fakeBridge,
                onToggle = {
                    container.settings.update { it.copy(fakeBridge = !it.fakeBridge) }
                    scope.launch {
                        container.session.disconnect(notice = "가짜 브리지 설정이 바뀌어 연결을 끊었습니다.")
                    }
                },
            )
        }
        Spacer(modifier = Modifier.size(AgvSpacing.Xl))
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AgvSizes.TouchMin)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(AgvSpacing.S))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** 값을 −/+ 로 바꾸는 줄. 글자 버튼을 쓴다 (material-icons-core 에 −/+ 가 없다). */
@Composable
private fun StepperRow(
    label: String,
    value: String,
    canDown: Boolean,
    canUp: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onDown, enabled = canDown, modifier = Modifier.heightIn(min = AgvSizes.TouchMin)) { Text("−") }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = AgvSpacing.M),
        )
        OutlinedButton(onClick = onUp, enabled = canUp, modifier = Modifier.heightIn(min = AgvSizes.TouchMin)) { Text("+") }
    }
}

@Composable
private fun SwitchRow(label: String, note: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AgvSizes.ListRowMin)
            .clickable(role = Role.Switch, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
