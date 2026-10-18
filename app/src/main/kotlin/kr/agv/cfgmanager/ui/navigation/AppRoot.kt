package kr.agv.cfgmanager.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Color
import kr.agv.cfgmanager.ui.components.HorizontalDivider
import kr.agv.cfgmanager.ui.theme.AgvSizes
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.apply.ApplyUi
import kr.agv.cfgmanager.net.GatewayProbe
import kr.agv.cfgmanager.net.GatewayResult
import kr.agv.cfgmanager.session.ConnectionChipMapper
import kr.agv.cfgmanager.session.LinkStatus

import kr.agv.cfgmanager.ui.components.ConnectionChip

import kr.agv.cfgmanager.ui.diagnostics.DiagnosticsRoute
import kr.agv.cfgmanager.ui.history.HistoryRoute
import kr.agv.cfgmanager.ui.library.LibraryRoute
import kr.agv.cfgmanager.ui.more.AboutScreen
import kr.agv.cfgmanager.ui.more.AppSettingsScreen
import kr.agv.cfgmanager.ui.more.MoreScreen
import kr.agv.cfgmanager.ui.more.SessionSummaryScreen
import kr.agv.cfgmanager.ui.more.ExportScreen
import kr.agv.cfgmanager.ui.profiles.ProfileEditRoute
import kr.agv.cfgmanager.ui.profiles.ProfileListRoute
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import kr.agv.cfgmanager.ui.work.WorkRoute

/**
 * 앱 루트. 탭 4개(세팅 · 진단 · 이력 · 더보기)는 [NavigationSuiteScaffold] 가 고른다:
 * Compact 는 하단 바, Medium 이상은 내비게이션 레일 (F-G6). 보조 화면은 뒤로 가기 버튼을 쓰고 탭 바를 숨긴다.
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
    val connected = sessionState.link == LinkStatus.Connected

    // 연결 중에는 10초마다 stat 을 다시 읽어 칩의 Wi-Fi 상태를 최신으로 둔다. 적용 중에는 세션이 거절한다.
    LaunchedEffect(connected) {
        while (connected) {
            delay(STATUS_REFRESH_MS)
            container.session.refreshStatus(auto = true)
        }
    }

    // 장치의 server 로 게이트웨이 화면이 답하는지 15초마다 본다. 답할 때만 칩에 적는다.
    val server = sessionState.snapshot?.config?.canonical(ConfigKey.SERVER)
    val gatewayUp by produceState(false, connected, server) {
        value = false
        if (!connected || server == null) return@produceState
        val probe = GatewayProbe()
        while (true) {
            value = probe.check(server) is GatewayResult.Responded
            delay(GATEWAY_CHECK_MS)
        }
    }
    val chip = ConnectionChipMapper.map(sessionState, nowMs, gatewayUp)

    // 연결 중이거나 적용 중에는 화면이 꺼지지 않게 한다 (F-C4, F-A6).
    val keepOn = sessionState.link != LinkStatus.Disconnected || applyState is ApplyUi.Running
    val view = LocalView.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text) } }

    val openTab: (TopLevelDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val push: (String) -> Unit = { route ->
        navController.navigate(route) { launchSingleTop = true }
    }

    // 시작 때 자동 확인에서 새 버전을 찾으면 한 줄로 알린다 (F-G3). 실패는 알리지 않는다.
    // 알림의 [업데이트] 를 누르면 정보 화면으로 가서 바로 받기를 시작한다 (설치는 받은 뒤 [설치] 로).
    LaunchedEffect(Unit) {
        container.updateNotices.collect { text ->
            scope.launch {
                val result = snackbar.showSnackbar(text, actionLabel = "업데이트", duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) {
                    push(ROUTE_ABOUT)
                    container.updateActions.download()
                }
            }
        }
    }

    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = accent,
            selectedTextColor = accent,
            unselectedIconColor = secondary,
            unselectedTextColor = secondary,
            indicatorColor = Color.Transparent,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = accent,
            selectedTextColor = accent,
            unselectedIconColor = secondary,
            unselectedTextColor = secondary,
            indicatorColor = Color.Transparent,
        ),
    )
    val layoutType = if (isTab) {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    } else {
        NavigationSuiteType.None
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentRoute == destination.route,
                    onClick = { openTab(destination) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(destination.label) },
                    colors = itemColors,
                )
            }
        },
        layoutType = layoutType,
        // 평평한 탭 바: 셀 색 바탕. 위쪽 머리카락 선은 아래 Scaffold 의 bottomBar 가 그린다.
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (isTab && layoutType == NavigationSuiteType.NavigationBar) HorizontalDivider()
            },
            topBar = {
                if (isTab) {
                    // 탭 화면은 Large Title 아래에 연결 칩을 둔다.
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Text(
                            TopLevelDestination.entries.firstOrNull { it.route == currentRoute }?.label.orEmpty(),
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.padding(horizontal = AgvSpacing.L),
                        )
                        ConnectionChip(
                            chip = chip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AgvSpacing.L, vertical = AgvSpacing.S),
                        )
                    }
                } else {
                    Column {
                        CenterAlignedTopAppBar(
                            title = { Text(subScreenTitle(currentRoute), style = MaterialTheme.typography.titleMedium) },
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "뒤로",
                                        tint = accent,
                                        modifier = Modifier.size(AgvSizes.IconLarge),
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                        )
                        ConnectionChip(
                            chip = chip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AgvSpacing.L, vertical = AgvSpacing.S),
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.Work.route,
                modifier = Modifier.padding(innerPadding),
                // 화면 전환에 페이드 · 슬라이드 효과를 쓰지 않는다 (즉시 전환).
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                composable(TopLevelDestination.Work.route) {
                    WorkRoute(
                        container = container,
                        onOpenConsole = { openTab(TopLevelDestination.Diagnostics) },
                        onEditProfile = { id -> push(if (id == null) "profile/new" else "profile/$id") },
                    )
                }
                composable(TopLevelDestination.Diagnostics.route) {
                    DiagnosticsRoute(container = container)
                }
                composable(TopLevelDestination.History.route) {
                    HistoryRoute(container = container, onFilled = { openTab(TopLevelDestination.Work) })
                }
                composable(TopLevelDestination.More.route) {
                    MoreScreen(
                        container = container,
                        onOpenProfiles = { push(ROUTE_PROFILES) },
                        onOpenLibrary = { push(ROUTE_LIBRARY) },
                        onOpenExport = { push(ROUTE_EXPORT) },
                        onOpenSummary = { push(ROUTE_SUMMARY) },
                        onOpenSettings = { push(ROUTE_APP_SETTINGS) },
                        onOpenAbout = { push(ROUTE_ABOUT) },
                    )
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
                composable(ROUTE_EXPORT) {
                    ExportScreen(container = container)
                }
                composable(ROUTE_SUMMARY) {
                    SessionSummaryScreen(container = container, onMessage = notify)
                }
                composable(ROUTE_APP_SETTINGS) {
                    AppSettingsScreen(container = container)
                }
                composable(ROUTE_ABOUT) {
                    AboutScreen(container = container, onMessage = notify)
                }
            }
        }
    }
}

private const val STATUS_REFRESH_MS = 10_000L
private const val GATEWAY_CHECK_MS = 15_000L
private const val ROUTE_PROFILES = "profiles"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_EXPORT = "export"
private const val ROUTE_SUMMARY = "summary"
private const val ROUTE_APP_SETTINGS = "app_settings"
private const val ROUTE_ABOUT = "about"

private fun subScreenTitle(route: String?): String = when {
    route == ROUTE_PROFILES -> "프로필"
    route == ROUTE_LIBRARY -> "SSID 라이브러리"
    route == ROUTE_EXPORT -> "내보내기"
    route == ROUTE_SUMMARY -> "연속 세팅 요약"
    route == ROUTE_APP_SETTINGS -> "앱 설정"
    route == ROUTE_ABOUT -> "정보"
    route?.startsWith("profile/") == true -> "프로필 편집"
    else -> ""
}
