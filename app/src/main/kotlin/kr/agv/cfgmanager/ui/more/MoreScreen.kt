package kr.agv.cfgmanager.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import kr.agv.cfgmanager.ui.components.InsetDivider
import kr.agv.cfgmanager.ui.components.InsetGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing

/** ⚙ 보조 메뉴: 프로필 · SSID 라이브러리 · 내보내기 · 연속 세팅 요약 · 설정 · 정보. */
@Composable
fun MoreScreen(
    container: AppContainer,
    onOpenProfiles: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val batch by container.batch.ui.collectAsStateWithLifecycle()
    val done = batch.state.done.size
    InsetGroup(modifier = Modifier.padding(horizontal = AgvSpacing.L)) {
        MoreRow("프로필", "현장별 공통 값 · 내보내기 · QR", onOpenProfiles)
        InsetDivider()
        MoreRow("SSID 라이브러리", "Wi-Fi 이름과 저장된 비밀번호", onOpenLibrary)
        InsetDivider()
        MoreRow("내보내기", "프로필 · SSID 라이브러리를 파일로 공유", onOpenExport)
        InsetDivider()
        MoreRow("연속 세팅 요약", if (done == 0) "아직 설정한 호기가 없습니다" else "이번에 설정한 호기 ${done}대", onOpenSummary)
        InsetDivider()
        MoreRow("설정", "테마 · 스캔 시간 · 응답 유휴 시간", onOpenSettings)
        InsetDivider()
        MoreRow("정보", "버전 · 로그 내보내기 · 오픈소스 고지", onOpenAbout)
    }
}

/** 보조 메뉴의 한 줄. 제목 · 설명 · 오른쪽 화살표. 전체가 누를 수 있는 영역이다. */
@Composable
internal fun MoreRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AgvSizes.ListRowMin)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AgvSpacing.L),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}
