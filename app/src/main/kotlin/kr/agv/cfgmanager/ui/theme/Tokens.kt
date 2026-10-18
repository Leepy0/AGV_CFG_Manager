package kr.agv.cfgmanager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/**
 * 글꼴: iOS 텍스트 스타일에 맞춘다. 시스템 산세리프(FontFamily.Default)를 쓴다.
 * Large Title 34 · Title1 28 · Title2 22 · Headline 17 · Body 17 · Callout 16 · Subhead 15 · Footnote 13 · Caption 12 / 11.
 * 굵기는 400 과 700 두 가지만 쓴다. Android 시스템 한글 글꼴에는 500 · 600 이 없어 영문만 굵게 나오고 한글은 그대로여서 섞여 보인다.
 * 로그 · 숫자 열용 고정폭은 화면에서 [AgvMonoText] 를 쓴다.
 */
val AgvTypography: Typography = Typography().let { base ->
    fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
    )
    val bold = FontWeight.Bold
    val normal = FontWeight.Normal
    base.copy(
        displaySmall = style(34, 41, bold),
        headlineLarge = style(34, 41, bold), // Large Title
        headlineMedium = style(28, 34, bold), // Title 1
        headlineSmall = style(22, 28, bold), // Title 2
        titleLarge = style(22, 28, bold),
        titleMedium = style(17, 22, bold), // Headline
        titleSmall = style(15, 20, bold), // Subhead
        bodyLarge = style(17, 22, normal), // Body
        bodyMedium = style(16, 21, normal), // Callout
        bodySmall = style(13, 18, normal), // Footnote
        labelLarge = style(15, 20, bold),
        labelMedium = style(12, 16, bold), // Caption 1
        labelSmall = style(11, 13, bold), // Caption 2
    )
}

/** 모양: HIG 모서리. 입력칸 · 그룹 셀 · 메뉴 10, 시트 · 알림 14. 버튼 12 는 [AgvSizes.ButtonRadius] 를 components 의 버튼이 쓴다. */
val AgvShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

/** 크기 토큰 (dp). HIG 최소 44dp 보다 크게 터치 최소 48dp, 주요 행동은 72dp(장갑 사용 기준). */
object AgvSizes {
    val TouchMin = 48.dp
    val PrimaryAction = 72.dp
    val ListRowMin = 64.dp
    val SignalBarWidth = 6.dp
    val SignalBarGap = 3.dp
    val SignalBarHeights = listOf(8.dp, 12.dp, 16.dp)
    val LabelColumn = 96.dp
    val IconSmall = 16.dp
    val IconMedium = 20.dp
    val IconLarge = 28.dp
    val SignalBarRadius = 2.dp
    val ChipRadius = 16.dp
    val CardRadius = 10.dp
    val GroupRadius = 10.dp
    val ButtonRadius = 12.dp
    val Hairline = 0.5.dp
    val SeparatorInset = 16.dp
    val QrSize = 240.dp
    val ConsoleHeight = 200.dp
    val Stroke = 1.dp
}

/**
 * QR 코드 색. 어두운 테마에서도 스캔이 되도록 흰 바탕 · 검은 점을 고정한다 (기능상 필요한 예외).
 */
object AgvQrColors {
    val Background = Color(0xFFFFFFFF)
    val Foreground = Color(0xFF000000)
}

/** 로그 · 명령 · 숫자 열용 고정폭 글자. 크기는 Caption 단계에 맞춘다. */
val AgvMonoText: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

/** 호기 번호 입력 글자 (28sp, 굵게). 설정 탭 폼의 큰 숫자 칸에만 쓴다. */
val AgvStepperText: TextStyle = TextStyle(
    fontSize = 28.sp,
    fontWeight = FontWeight.Bold,
)
