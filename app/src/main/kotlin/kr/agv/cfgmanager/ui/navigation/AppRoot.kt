package kr.agv.cfgmanager.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import kr.agv.cfgmanager.apply.ApplyUi
import kr.agv.cfgmanager.R
import kr.agv.cfgmanager.session.ConnectionChipMapper
import kr.agv.cfgmanager.session.LinkStatus
import kr.agv.cfgmanager.ui.boards.BoardsRoute
import kr.agv.cfgmanager.ui.components.ConnectionChip
import kr.agv.cfgmanager.ui.config.SettingsRoute
import kr.agv.cfgmanager.ui.diagnostics.DiagnosticsRoute
import kr.agv.cfgmanager.ui.history.HistoryRoute
import kr.agv.cfgmanager.ui.library.LibraryRoute
import kr.agv.cfgmanager.ui.more.MoreScreen
import kr.agv.cfgmanager.ui.profiles.ProfileEditRoute
import kr.agv.cfgmanager.ui.profiles.ProfileListRoute
import kr.agv.cfgmanager.ui.theme.AgvSpacing

/**
 * 앱 루트. 하단 탭 4개 + 그 밖의 보조 화면(더보기 · 프로필 · 프로필 편집 · SSID 라이브러리).
 * 보조 화면은 뒤로 가기 버튼을 쓰고 하단 탭을 숨긴다. ⚙ 는 탭 화면의 상단 메뉴에 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(container: AppContainer) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val isTab = TopLevelDestination.entries.any { it.route == currentRoute }
    val sessionState by container.session.state.collectAsStateWithLifecycle()
    val applyState by container.apply.state.collectAsStateWithLifecycle()
    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val chip = ConnectionChipMapper.map(sessionState, nowMs)

    // 연결 중이거나 적용 중에는 화면이 꺼지지 않게 한다 (F-C4, F-A6).
    val keepOn = sessionState.link != LinkStatus.Disconnected || applyState is ApplyUi.Running
    val view = LocalView.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val openTab: (TopLevelDestination) -> Unit = { destination ->
        // 보드 탭으로 돌아가면 연속 세팅은 읽은 보드를 버리고 목록 상태로 간다 (7.3).
        if (destination == TopLevelDestination.Boards) container.batch.backToList()
        navController.navigate(destination.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val push: (String) -> Unit = { route ->
        navController.navigate(route) { launchSingleTop = true }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (isTab) stringResource(R.string.app_name) else subScreenTitle(currentRoute)) },
                    navigationIcon = {
                        if (!isTab) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                            }
                        }
                    },
                    actions = {
                        if (isTab) {
                            IconButton(onClick = { push("more") }) {
                                Icon(Icons.Filled.Settings, contentDescription = "보조 메뉴 (프로필 · SSID 라이브러리)")
                            }
                        }
                    },
                )
                ConnectionChip(
                    chip = chip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AgvSpacing.L, vertical = AgvSpacing.S),
                )
            }
        },
        bottomBar = {
            if (isTab) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = navController.currentBackStackEntryAsState().value?.destination?.hierarchy
                                ?.any { it.route == destination.route } == true,
                            onClick = { openTab(destination) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
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
                SettingsRoute(
                    container = container,
                    onOpenConsole = { openTab(TopLevelDestination.Diagnostics) },
                    onOpenBoards = { openTab(TopLevelDestination.Boards) },
                    onEditProfile = { id -> push(if (id == null) "profile/new" else "profile/$id") },
                )
            }
            composable(TopLevelDestination.Diagnostics.route) {
                DiagnosticsRoute(container = container)
            }
            composable(TopLevelDestination.History.route) {
                HistoryRoute(container = container, onFilled = { openTab(TopLevelDestination.Settings) })
            }
            composable(ROUTE_MORE) {
                MoreScreen(onOpenProfiles = { push(ROUTE_PROFILES) }, onOpenLibrary = { push(ROUTE_LIBRARY) })
            }
            composable(ROUTE_PROFILES) {
                ProfileListRoute(container = container, onOpenProfile = { id -> push("profile/${id ?: "new"}") })
            }
            composable("profile/{id}") { entry ->
                val id = entry.arguments?.getString("id") ?: "new"
                ProfileEditRoute(
                    container = container,
                    profileId = id.takeIf { it != "new" },
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { next -> push("profile/$next") },
                )
            }
            composable(ROUTE_LIBRARY) {
                LibraryRoute(container = container)
            }
        }
    }
}

private const val ROUTE_MORE = "more"
private const val ROUTE_PROFILES = "profiles"
private const val ROUTE_LIBRARY = "library"

private fun subScreenTitle(route: String?): String = when {
    route == ROUTE_MORE -> "보조 메뉴"
    route == ROUTE_PROFILES -> "프로필"
    route == ROUTE_LIBRARY -> "SSID 라이브러리"
    route?.startsWith("profile/") == true -> "프로필 편집"
    else -> ""
}
