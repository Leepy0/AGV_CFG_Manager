package kr.agv.cfgmanager.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing

/** ⚙ 보조 메뉴. 프로필 목록과 SSID 라이브러리로 들어간다. 정보 · 설정 화면은 Phase 5. */
@Composable
fun MoreScreen(onOpenProfiles: () -> Unit, onOpenLibrary: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AgvSpacing.L)) {
        MoreRow("프로필", "현장별 공통 값 · 내보내기 · 가져오기", onOpenProfiles)
        HorizontalDivider()
        MoreRow("SSID 라이브러리", "Wi-Fi 이름과 저장된 비밀번호", onOpenLibrary)
        HorizontalDivider()
    }
}

@Composable
private fun MoreRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AgvSizes.ListRowMin)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
