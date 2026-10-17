package kr.agv.cfgmanager.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 색은 이 파일과 Theme.kt 에서만 정의한다. 화면 코드에 Color(0xFF…) 를 쓰지 않는다.
// 구성: 중립 회색 바탕 + 강조 1색(파랑) + 상태 색은 Phase 2 에서 필요할 때 추가.

private val Accent = Color(0xFF1F5FBF)
private val AccentDark = Color(0xFF9CC3FF)

val AgvLightColors: ColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E5FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF555F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E3F8),
    onSecondaryContainer = Color(0xFF121C2B),
    tertiary = Color(0xFF6E5676),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF7D8FF),
    onTertiaryContainer = Color(0xFF28132F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FB),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FB),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
)

val AgvDarkColors: ColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF00306B),
    primaryContainer = Color(0xFF144889),
    onPrimaryContainer = Color(0xFFD8E5FF),
    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF273141),
    secondaryContainer = Color(0xFF3D4758),
    onSecondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFFDABDE0),
    onTertiary = Color(0xFF3D2843),
    tertiaryContainer = Color(0xFF543F5B),
    onTertiaryContainer = Color(0xFFF7D8FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111418),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
)

/**
 * 상태 색 3종 (정상 · 경고 · 알람). 화면은 글자와 아이콘을 함께 쓰고, 색은 보조로만 쓴다.
 * 평상시 화면에는 쓰지 않는다 (field-app-ui-design: 색은 상태에만).
 */
data class AgvStatusColors(
    val ok: Color,
    val warn: Color,
    val alarm: Color,
)

val AgvLightStatus = AgvStatusColors(
    ok = Color(0xFF1B6E3A),
    warn = Color(0xFF7A4D00),
    alarm = Color(0xFFBA1A1A),
)

val AgvDarkStatus = AgvStatusColors(
    ok = Color(0xFF8AD9A3),
    warn = Color(0xFFFFC36B),
    alarm = Color(0xFFFFB4AB),
)
