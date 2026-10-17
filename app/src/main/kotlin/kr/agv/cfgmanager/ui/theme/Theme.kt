package kr.agv.cfgmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * 앱 테마의 유일한 진입점. dynamic color 는 쓰지 않는다 (현장 화면 색을 고정하기 위함).
 * 시스템 다크 모드를 따르며 light / dark colorScheme 을 모두 정의한다.
 */
@Composable
fun AgvCfgManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AgvDarkColors else AgvLightColors,
        typography = AgvTypography,
        shapes = AgvShapes,
        content = content,
    )
}
