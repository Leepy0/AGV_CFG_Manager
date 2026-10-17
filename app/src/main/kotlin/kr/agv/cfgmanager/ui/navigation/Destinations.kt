package kr.agv.cfgmanager.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** 하단 탭 4개. 순서는 docs/DEVELOPMENT_PLAN.md 5.1 과 같다. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Boards(route = "boards", label = "보드", icon = Icons.Filled.Home),
    Settings(route = "settings", label = "설정", icon = Icons.Filled.Settings),
    Diagnostics(route = "diagnostics", label = "진단", icon = Icons.Filled.Build),
    History(route = "history", label = "이력", icon = Icons.AutoMirrored.Filled.List),
}
