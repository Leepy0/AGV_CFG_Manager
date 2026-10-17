package kr.agv.cfgmanager.ui.theme

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

/** 글꼴. Phase 0 은 M3 기본 type scale. 로그 · 숫자 열용 고정폭은 화면에서 FontFamily.Monospace 를 쓴다. */
val AgvTypography: Typography = Typography()

/** 모양. Phase 0 은 M3 기본 값. */
val AgvShapes: Shapes = Shapes()

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
