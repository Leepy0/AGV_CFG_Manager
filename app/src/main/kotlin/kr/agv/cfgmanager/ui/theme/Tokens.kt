package kr.agv.cfgmanager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
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

/**
 * 글꼴 (field-app-ui-design: 크기 5단계 · 굵기 2단계).
 * 크기: 28(호기 입력) · 20(제목) · 16(강조 본문) · 14(본문 · 라벨) · 12(보조). 굵기: 400 과 700 두 가지만 쓴다.
 * 스킬 예시는 400/600 이지만 Android 시스템 한글 글꼴에는 500 · 600 이 없어 영문만 굵게 나오고 한글은 그대로여서 섞여 보인다.
 * 그래서 한글 · 영문 모두 제대로 나오는 700 을 쓴다. 로그 · 숫자 열용 고정폭은 화면에서 [AgvMonoText] 를 쓴다.
 */
val AgvTypography: Typography = Typography().let { base ->
    fun Typography.bold(style: TextStyle, size: Int, line: Int) = style.copy(
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = FontWeight.Bold,
    )
    fun regular(style: TextStyle, size: Int, line: Int) = style.copy(
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = FontWeight.Normal,
    )
    base.copy(
        titleLarge = base.bold(base.titleLarge, 20, 28),
        titleMedium = base.bold(base.titleMedium, 16, 24),
        titleSmall = base.bold(base.titleSmall, 14, 20),
        bodyLarge = regular(base.bodyLarge, 16, 24),
        bodyMedium = regular(base.bodyMedium, 14, 20),
        bodySmall = regular(base.bodySmall, 12, 16),
        labelLarge = base.bold(base.labelLarge, 14, 20),
        labelMedium = base.bold(base.labelMedium, 12, 16),
        labelSmall = base.bold(base.labelSmall, 12, 16),
    )
}

/** 모양 (모서리 3종: 4 · 8 · 16). 입력칸 4, 카드 · 칩 8, 시트 · 큰 면 16. 버튼은 M3 기본(완전 둥근)을 그대로 둔다. */
val AgvShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** 크기 토큰 (dp). 터치 최소 48dp, 주요 행동은 72dp(장갑 사용 기준). */
object AgvSizes {
    val TouchMin = 48.dp
    val PrimaryAction = 72.dp
    val ListRowMin = 64.dp
    val SignalBarWidth = 6.dp
    val SignalBarGap = 3.dp
    val SignalBarHeights = listOf(8.dp, 12.dp, 16.dp)
    val LabelColumn = 96.dp
    val ChipRadius = 16.dp
    val CardRadius = 8.dp
    val QrSize = 240.dp
    val ConsoleHeight = 200.dp
}

/**
 * QR 코드 색. 어두운 테마에서도 스캔이 되도록 흰 바탕 · 검은 점을 고정한다 (기능상 필요한 예외).
 */
object AgvQrColors {
    val Background = Color(0xFFFFFFFF)
    val Foreground = Color(0xFF000000)
}

/** 로그 · 명령 · 숫자 열용 고정폭 글자. 크기는 본문 소형 단계에 맞춘다. */
val AgvMonoText: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

/** 호기 번호 입력 글자 (28sp, 굵게). 설정 탭 폼의 큰 숫자 칸에만 쓴다. */
val AgvStepperText: TextStyle = TextStyle(
    fontSize = 28.sp,
    fontWeight = FontWeight.SemiBold,
)
