package kr.agv.cfgmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kr.agv.cfgmanager.ui.navigation.AppRoot
import kr.agv.cfgmanager.ui.theme.AgvCfgManagerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AgvApp).container
        setContent {
            AgvCfgManagerTheme {
                AppRoot(container)
            }
        }
    }
}
