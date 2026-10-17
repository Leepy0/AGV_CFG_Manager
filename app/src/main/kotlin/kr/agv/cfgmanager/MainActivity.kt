package kr.agv.cfgmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kr.agv.cfgmanager.data.ThemeMode
import kr.agv.cfgmanager.ui.navigation.AppRoot
import kr.agv.cfgmanager.ui.theme.AgvCfgManagerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AgvApp).container
        setContent {
            val settings by container.settings.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            AgvCfgManagerTheme(darkTheme = darkTheme) {
                AppRoot(container)
            }
        }
    }
}
