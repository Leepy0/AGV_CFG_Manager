package kr.agv.cfgmanager.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.unit.dp

// 간격 · 모양 · 글꼴 토큰. 화면 코드는 여기 값만 쓴다 (sp · dp 하드코딩 금지).

/** 간격 토큰 (dp). */
object AgvSpacing {
    val Xxs = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
}

/** 글꼴. Phase 0 은 M3 기본 type scale. 로그 · 숫자 열용 고정폭은 화면에서 FontFamily.Monospace 를 쓴다. */
val AgvTypography: Typography = Typography()

/** 모양. Phase 0 은 M3 기본 값. */
val AgvShapes: Shapes = Shapes()
