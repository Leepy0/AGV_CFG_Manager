package kr.agv.cfgmanager.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 색은 이 파일과 Theme.kt 에서만 정의한다. 화면 코드에 Color(0xFF…) 를 쓰지 않는다.
// Apple HIG 시스템 색: 강조 1색(systemBlue) + 그룹 배경(바탕 #F2F2F7 / 셀 #FFFFFF, 다크는 #000000 / #1C1C1E).
// 셀 색은 M3 surfaceContainer* 전부에 같은 값을 넣어 Card · 시트 · 대화상자 · 탭 바가 셀 색을 그대로 쓰게 한다.

private val Accent = Color(0xFF007AFF)
private val AccentDark = Color(0xFF0A84FF)

private val LabelSecondary = Color(0x993C3C43)
private val LabelSecondaryDark = Color(0x99EBEBF5)
private val LabelTertiary = Color(0x4D3C3C43)
private val LabelTertiaryDark = Color(0x4DEBEBF5)
private val Separator = Color(0x4A3C3C43)
private val SeparatorDark = Color(0xA6545458)

private val CellLight = Color(0xFFFFFFFF)
private val CellDark = Color(0xFF1C1C1E)

val AgvLightColors: ColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF003A80),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFF8E8E93),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE5E5EA),
    onTertiaryContainer = Color(0xFF000000),
    error = Color(0xFFFF3B30),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE1DE),
    onErrorContainer = Color(0xFF8C0A00),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = LabelSecondary,
    surfaceTint = Accent,
    outline = LabelTertiary,
    outlineVariant = Separator,
    surfaceContainerLowest = CellLight,
    surfaceContainerLow = CellLight,
    surfaceContainer = CellLight,
    surfaceContainerHigh = CellLight,
    surfaceContainerHighest = CellLight,
)

val AgvDarkColors: ColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0A2F5C),
    onPrimaryContainer = Color(0xFFCFE4FF),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF8E8E93),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF2C2C2E),
    onTertiaryContainer = Color(0xFFFFFFFF),
    error = Color(0xFFFF453A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF5C1511),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = LabelSecondaryDark,
    surfaceTint = AccentDark,
    outline = LabelTertiaryDark,
    outlineVariant = SeparatorDark,
    surfaceContainerLowest = CellDark,
    surfaceContainerLow = CellDark,
    surfaceContainer = CellDark,
    surfaceContainerHigh = CellDark,
    surfaceContainerHighest = CellDark,
)

/**
 * 상태 색 3종 (정상 · 경고 · 알람). 화면은 글자와 아이콘을 함께 쓰고, 색은 보조로만 쓴다.
 * 평상시 화면에는 쓰지 않는다 (field-app-ui-design: 색은 상태에만).
 * 글자로도 쓰이므로 라이트는 HIG 의 접근성용 어두운 변형(green #248A3D · orange #C93400 · red #D70015)을 쓴다.
 * 다크는 systemGreen #30D158 · systemOrange #FF9F0A · systemRed #FF453A.
 */
data class AgvStatusColors(
    val ok: Color,
    val warn: Color,
    val alarm: Color,
    /** iOS Switch 켜짐 트랙 (systemGreen). 상태 표시가 아니라 컨트롤 색이다. */
    val switchOn: Color,
    /** iOS Switch 꺼짐 트랙. */
    val switchOff: Color,
)

val AgvLightStatus = AgvStatusColors(
    ok = Color(0xFF248A3D),
    warn = Color(0xFFC93400),
    alarm = Color(0xFFD70015),
    switchOn = Color(0xFF34C759),
    switchOff = Color(0xFFE9E9EA),
)

val AgvDarkStatus = AgvStatusColors(
    ok = Color(0xFF30D158),
    warn = Color(0xFFFF9F0A),
    alarm = Color(0xFFFF453A),
    switchOn = Color(0xFF30D158),
    switchOff = Color(0xFF39393D),
)
