package kr.agv.cfgmanager.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.R
import kr.agv.cfgmanager.session.ConnectionChipMapper
import kr.agv.cfgmanager.session.LinkStatus
import kr.agv.cfgmanager.ui.boards.BoardsRoute
import kr.agv.cfgmanager.ui.components.ConnectionChip
import kr.agv.cfgmanager.ui.config.SettingsRoute
import kr.agv.cfgmanager.ui.diagnostics.DiagnosticsRoute
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(container: AppContainer) {
    val navController = rememberNavController()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val sessionState by container.session.state.collectAsStateWithLifecycle()
    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val chip = ConnectionChipMapper.map(sessionState, nowMs)

    // 연결 중에는 화면이 꺼지지 않게 한다 (F-C4).
    val keepOn = sessionState.link != LinkStatus.Disconnected
    val view = LocalView.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val openTab: (TopLevelDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(stringResource(R.string.app_name)) })
                ConnectionChip(
                    chip = chip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AgvSpacing.L, vertical = AgvSpacing.S),
                )
            }
        },
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = { openTab(destination) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Boards.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.Boards.route) {
                BoardsRoute(container = container, onConnected = { openTab(TopLevelDestination.Settings) })
            }
            composable(TopLevelDestination.Settings.route) {
                SettingsRoute(container = container)
            }
            composable(TopLevelDestination.Diagnostics.route) {
                DiagnosticsRoute(container = container)
            }
            composable(TopLevelDestination.History.route) {
                PlaceholderScreen(TopLevelDestination.History.label)
            }
        }
    }
}

/** 아직 만들지 않은 탭 (Phase 4). */
@Composable
private fun PlaceholderScreen(label: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(AgvSpacing.L),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$label · 준비 중",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
