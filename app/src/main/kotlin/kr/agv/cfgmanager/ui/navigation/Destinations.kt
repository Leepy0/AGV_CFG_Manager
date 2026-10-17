package kr.agv.cfgmanager.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 하단 탭 4개. 현장 작업 순서에 맞춘다: 세팅(프로필 → 보드 → 호기 → 적용 → 다음) / 진단 / 이력 / 더보기.
 * 프로필 · 라이브러리 · 출장 준비 · 앱 설정 · 정보는 더보기 안에 있다.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Work(route = "work", label = "세팅", icon = Icons.Filled.Home),
    Diagnostics(route = "diagnostics", label = "진단", icon = Icons.Filled.Build),
    History(route = "history", label = "이력", icon = Icons.AutoMirrored.Filled.List),
    More(route = "more", label = "더보기", icon = Icons.Filled.Menu),
}
