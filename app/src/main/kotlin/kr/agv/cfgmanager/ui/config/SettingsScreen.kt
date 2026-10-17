package kr.agv.cfgmanager.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.BuildConfig
import kr.agv.cfgmanager.session.ConnectionChipMapper
import kr.agv.cfgmanager.ui.components.SectionTitle
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import androidx.compose.runtime.getValue

/** 설정 탭. Phase 2 에서는 "장치:" 값을 읽기 전용으로 보여주고, 개발자 옵션만 켠다. 폼은 Phase 3. */
@Composable
fun SettingsRoute(container: AppContainer) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val fake by container.settings.fakeBridge.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snapshot = session.snapshot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AgvSpacing.L),
        verticalArrangement = Arrangement.spacedBy(AgvSpacing.S),
    ) {
        SectionTitle("장치")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(AgvSizes.CardRadius),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(modifier = Modifier.padding(AgvSpacing.M), verticalArrangement = Arrangement.spacedBy(AgvSpacing.Xxs)) {
                if (snapshot == null) {
                    Text("—", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "보드 탭에서 연결하세요. 연결하면 장치 값이 여기에 보입니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DeviceRow("이름", session.name ?: "—")
                    DeviceRow("펌웨어", snapshot.fw ?: session.fw ?: "—")
                    DeviceRow("설정 상태", ConnectionChipMapper.cfgLabel(snapshot.cfgState))
                    DeviceRow("호기", snapshot.config?.agv?.toString() ?: "—")
                    DeviceRow("SSID", snapshot.config?.ssid ?: "—")
                    DeviceRow("IP", snapshot.config?.ip ?: "DHCP")
                }
            }
        }
        Text(
            "장치 값은 읽기 전용입니다. 편집과 적용은 다음 단계에서 추가됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (BuildConfig.DEBUG) {
            SectionTitle("개발자 옵션")
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
                    Text("가짜 브리지", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "하드웨어 없이 가짜 보드 3대(설정 없음 2 · 12호기 1)로 동작합니다. 바꾸면 연결이 끊깁니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = fake, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun DeviceRow(label: String, value: String) {
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
