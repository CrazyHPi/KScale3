package xyz.crazyh.kscale3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import xyz.crazyh.kscale3.di.AppContainer
import xyz.crazyh.kscale3.domain.LanguageSetting
import xyz.crazyh.kscale3.ui.components.ScaleGlyph
import xyz.crazyh.kscale3.ui.i18n.AppLanguage
import xyz.crazyh.kscale3.ui.i18n.AppStrings
import xyz.crazyh.kscale3.ui.i18n.LocalAppLanguage
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.i18n.currentSystemLanguage
import xyz.crazyh.kscale3.ui.i18n.resolveLanguage
import xyz.crazyh.kscale3.ui.i18n.stringsFor
import xyz.crazyh.kscale3.ui.screens.DeviceScanScreen
import xyz.crazyh.kscale3.ui.screens.MeasurementDetailScreen
import xyz.crazyh.kscale3.ui.screens.ProfileScreen
import xyz.crazyh.kscale3.ui.screens.ResultScreen
import xyz.crazyh.kscale3.ui.screens.SettingsScreen
import xyz.crazyh.kscale3.ui.screens.UserEditScreen
import xyz.crazyh.kscale3.ui.screens.WeighInScreen
import xyz.crazyh.kscale3.ui.theme.KScaleTheme
import xyz.crazyh.kscale3.ui.vm.AppSettingsViewModel
import xyz.crazyh.kscale3.ui.vm.DeviceState
import xyz.crazyh.kscale3.ui.vm.SessionViewModel

private data class Tab(
    val label: (AppStrings) -> String,
    val icon: @Composable () -> Unit,
    val route: Route,
)

private val TABS = listOf(
    Tab(
        label = { it.tabProfile },
        icon = {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        route = Route.Profile,
    ),
    Tab(
        label = { it.tabWeighIn },
        icon = { ScaleGlyph(Modifier.size(24.dp)) },
        route = Route.WeighIn,
    ),
    Tab(
        label = { it.tabSettings },
        icon = {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        route = Route.Settings,
    ),
)

/**
 * 应用根 Composable：先读取语言设置（首帧未读到时保持空白，避免先闪中文），
 * 再按生效语言提供 [LocalStrings]/[LocalAppLanguage]，最后进入三 Tab 导航。
 */
@Composable
fun App(container: AppContainer) {
    val sessionVm: SessionViewModel = viewModel { SessionViewModel(container) }
    val settingsVm: AppSettingsViewModel = viewModel {
        AppSettingsViewModel(container.appSettingsRepository)
    }

    KScaleTheme {
        val settingOrNull by settingsVm.language.collectAsState()
        val setting = settingOrNull
        if (setting == null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        } else {
            val language = remember(setting) {
                resolveLanguage(setting, currentSystemLanguage())
            }
            CompositionLocalProvider(
                LocalStrings provides stringsFor(language),
                LocalAppLanguage provides language,
            ) {
                AppScaffold(
                    container = container,
                    sessionVm = sessionVm,
                    languageSetting = setting,
                    effectiveLanguage = language,
                    onSetLanguage = settingsVm::setLanguage,
                )
            }
        }
    }
}

/**
 * 三 Tab 导航（档案/称重/设置）；档案页内部再分历史/成员。
 * 结果、详情、编辑、扫描没有底部栏——避免保存前误触离开。
 */
@Composable
private fun AppScaffold(
    container: AppContainer,
    sessionVm: SessionViewModel,
    languageSetting: LanguageSetting,
    effectiveLanguage: AppLanguage,
    onSetLanguage: (LanguageSetting) -> Unit,
) {
    val strings = LocalStrings.current
    val navController = rememberNavController()
    val deviceState by sessionVm.deviceState.collectAsState()
    val device by sessionVm.device.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun routeActive(route: Route): Boolean =
        currentRoute?.contains(route::class.simpleName ?: "@") == true

    val isProfile = routeActive(Route.Profile)
    val isWeighIn = routeActive(Route.WeighIn)
    val isSettings = routeActive(Route.Settings)
    val showBottomBar = isProfile || isWeighIn || isSettings

    var profileTabIndex by rememberSaveable { mutableIntStateOf(ProfileTab.History.ordinal) }
    val profileTab = ProfileTab.entries[profileTabIndex.coerceIn(0, ProfileTab.entries.lastIndex)]

    fun navigateTab(route: Route) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun goProfileMembers() {
        profileTabIndex = ProfileTab.Members.ordinal
        navigateTab(Route.Profile)
    }

    fun goWeighIn() {
        navController.navigate(Route.WeighIn) {
            popUpTo(Route.WeighIn) { inclusive = true }
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    TABS.forEachIndexed { index, tab ->
                        val selected = when (index) {
                            0 -> isProfile
                            1 -> isWeighIn
                            else -> isSettings
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTab(tab.route) },
                            icon = tab.icon,
                            label = { Text(tab.label(strings)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        // 等设备库首帧读完再决定起始页：已保存设备直接进称重主界面，
        // 否则进扫描配对页——避免每次冷启动都被丢到扫描页重新配对。
        if (deviceState is DeviceState.Loading) {
            Box(Modifier.padding(padding)) { }
        } else {
            NavHost(
                navController = navController,
                startDestination = if (deviceState is DeviceState.Found) Route.WeighIn else Route.DeviceScan,
                modifier = Modifier.padding(padding),
            ) {
                composable<Route.DeviceScan> {
                    DeviceScanScreen(
                        vm = sessionVm,
                        onDone = {
                            navController.navigate(Route.WeighIn) {
                                popUpTo(Route.DeviceScan) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable<Route.Profile> {
                    ProfileScreen(
                        container = container,
                        sessionVm = sessionVm,
                        device = device,
                        selectedTab = profileTab,
                        onSelectTab = { profileTabIndex = it.ordinal },
                        onAddMember = { navController.navigate(Route.UserEdit(null)) },
                        onEditMember = { userId -> navController.navigate(Route.UserEdit(userId)) },
                        onOpenMeasurement = { id ->
                            navController.navigate(
                                Route.MeasurementDetail(
                                    id
                                )
                            )
                        },
                        onGoWeigh = { navigateTab(Route.WeighIn) },
                        onGoDevice = { navController.navigate(Route.DeviceScan) },
                    )
                }
                composable<Route.UserEdit> { entry ->
                    UserEditScreen(
                        container = container,
                        vm = sessionVm,
                        userId = entry.toRoute<Route.UserEdit>().userId,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<Route.WeighIn> {
                    WeighInScreen(
                        vm = sessionVm,
                        onResultReady = { navController.navigate(Route.Result) },
                        onGoRegister = { goProfileMembers() },
                        onGoDevice = { navController.navigate(Route.DeviceScan) },
                        onAddMember = { navController.navigate(Route.UserEdit(null)) },
                    )
                }
                composable<Route.Result> {
                    ResultScreen(
                        vm = sessionVm,
                        onDone = { goWeighIn() },
                    )
                }
                composable<Route.MeasurementDetail> { entry ->
                    MeasurementDetailScreen(
                        container = container,
                        measurementId = entry.toRoute<Route.MeasurementDetail>().measurementId,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<Route.Settings> {
                    SettingsScreen(
                        vm = sessionVm,
                        languageSetting = languageSetting,
                        effectiveLanguage = effectiveLanguage,
                        onSetLanguage = onSetLanguage,
                        onForgetDevice = {
                            navController.navigate(Route.DeviceScan) {
                                popUpTo(Route.Settings) { inclusive = true }
                            }
                        },
                        onGoDevice = { navController.navigate(Route.DeviceScan) },
                    )
                }
            }
        }
    }
}
