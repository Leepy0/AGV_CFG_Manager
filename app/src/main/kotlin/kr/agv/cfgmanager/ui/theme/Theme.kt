package kr.agv.cfgmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** 상태 색 전달용. 화면은 [AgvTheme.status] 로만 읽는다. */
val LocalAgvStatusColors = staticCompositionLocalOf { AgvLightStatus }

/** 테마 토큰 접근점. 화면 코드는 Color(0xFF…) 대신 이 객체와 [MaterialTheme] 을 쓴다. */
object AgvTheme {
    val status: AgvStatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAgvStatusColors.current
}

/**
 * 앱 테마의 유일한 진입점. dynamic color 는 쓰지 않는다 (현장 화면 색을 고정하기 위함).
 * 시스템 다크 모드를 따르며 light / dark colorScheme 을 모두 정의한다.
 */
@Composable
fun AgvCfgManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAgvStatusColors provides if (darkTheme) AgvDarkStatus else AgvLightStatus,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) AgvDarkColors else AgvLightColors,
            typography = AgvTypography,
            shapes = AgvShapes,
            content = content,
        )
    }
}
