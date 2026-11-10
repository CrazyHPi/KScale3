package xyz.crazyh.kscale3.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import xyz.crazyh.kscale3.di.AppContainer
import xyz.crazyh.kscale3.domain.LanguageSetting
import xyz.crazyh.kscale3.ui.i18n.AppLanguage
import xyz.crazyh.kscale3.ui.i18n.AppStrings
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.i18n.currentSystemLanguage
import xyz.crazyh.kscale3.ui.i18n.resolveLanguage
import xyz.crazyh.kscale3.ui.i18n.stringsFor
import xyz.crazyh.kscale3.ui.screens.DeviceScanScreen
import xyz.crazyh.kscale3.ui.screens.HistoryScreen
import xyz.crazyh.kscale3.ui.screens.ResultScreen
import xyz.crazyh.kscale3.ui.screens.SettingsScreen
import xyz.crazyh.kscale3.ui.screens.UserEditScreen
import xyz.crazyh.kscale3.ui.screens.UsersScreen
import xyz.crazyh.kscale3.ui.screens.WeighInScreen
import xyz.crazyh.kscale3.ui.vm.AppSettingsViewModel
import xyz.crazyh.kscale3.ui.vm.DeviceState
import xyz.crazyh.kscale3.ui.vm.SessionViewModel

private data class Tab(
    val label: (AppStrings) -> String,
    val icon: ImageVector,
    val route: Route,
)

private val TABS = listOf(
    Tab({ it.tabWeighIn }, Icons.Filled.MonitorWeight, Route.WeighIn),
    Tab({ it.tabUsers }, Icons.Filled.Person, Route.Users),
    Tab({ it.tabHistory }, Icons.Filled.History, Route.History),
    Tab({ it.tabSettings }, Icons.Filled.Settings, Route.Settings),
)

/**
 * 应用根 Composable：先读取语言设置（首帧未读到时空白等待，避免先闪中文），
 * 再按生效语言提供 [LocalStrings]，最后进入 Scaffold + 四 Tab 导航。
 * [SessionViewModel] 与 [AppSettingsViewModel] 挂在根级（跨屏共享）。
 */
@Composable
fun App(container: AppContainer) {
    val sessionVm: SessionViewModel = viewModel { SessionViewModel(container) }
    val settingsVm: AppSettingsViewModel = viewModel {
        AppSettingsViewModel(container.appSettingsRepository)
    }

    MaterialTheme {
        val settingOrNull by settingsVm.language.collectAsState()
        val setting = settingOrNull
        if (setting == null) {
            Box(Modifier.fillMaxSize())
        } else {
            val language = remember(setting) {
                resolveLanguage(setting, currentSystemLanguage())
            }
            CompositionLocalProvider(LocalStrings provides stringsFor(language)) {
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
 * 四 Tab 导航（称重/用户/历史/设置）；起始页按是否已保存设备决定
 * （已配对 → 称重；未配对 → DeviceScan），UserEdit/Result 为压栈页。
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRouteClass = backStackEntry?.destination?.route

    val showBottomBar = TABS.any { tab ->
        currentRouteClass?.contains(tab.route::class.simpleName ?: "@") == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TABS.forEach { tab ->
                        val selected = currentRouteClass?.contains(
                            tab.route::class.simpleName ?: "@"
                        ) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label(strings)) },
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
                                // 从称重页进入扫描页时，返回后复用已有 WeighIn，不叠栈
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable<Route.Users> {
                    UsersScreen(
                        vm = sessionVm,
                        onEditUser = { userId -> navController.navigate(Route.UserEdit(userId)) },
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
                        onGoRegister = { navController.navigate(Route.Users) },
                        onGoDevice = { navController.navigate(Route.DeviceScan) },
                    )
                }
                composable<Route.Result> {
                    ResultScreen(
                        vm = sessionVm,
                        onDone = {
                            navController.navigate(Route.WeighIn) {
                                popUpTo(Route.WeighIn) { inclusive = true }
                            }
                        },
                    )
                }
                composable<Route.History> {
                    HistoryScreen(container = container)
                }
                composable<Route.Settings> {
                    SettingsScreen(
                        vm = sessionVm,
                        languageSetting = languageSetting,
                        effectiveLanguage = effectiveLanguage,
                        onSetLanguage = onSetLanguage,
                        onForgetDevice = {
                            // 清库后回扫描配对页；保留下方 WeighIn，配对完成后可复用（不叠栈）
                            navController.navigate(Route.DeviceScan) {
                                popUpTo(Route.Settings) { inclusive = true }
                            }
                        },
                    )
                }
            }
        }
    }
}
