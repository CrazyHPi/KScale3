package xyz.crazyh.kscale3.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import xyz.crazyh.kscale3.di.AppContainer
import xyz.crazyh.kscale3.ui.screens.DeviceScanScreen
import xyz.crazyh.kscale3.ui.screens.HistoryScreen
import xyz.crazyh.kscale3.ui.screens.ResultScreen
import xyz.crazyh.kscale3.ui.screens.SettingsScreen
import xyz.crazyh.kscale3.ui.screens.UserEditScreen
import xyz.crazyh.kscale3.ui.screens.UsersScreen
import xyz.crazyh.kscale3.ui.screens.WeighInScreen
import xyz.crazyh.kscale3.ui.vm.SessionViewModel

private data class Tab(val label: String, val glyph: String, val route: Route)

// 图标用文字符号（不引入 material-icons 依赖）
private val TABS = listOf(
    Tab("称重", "秤", Route.WeighIn),
    Tab("用户", "人", Route.Users),
    Tab("历史", "史", Route.History),
    Tab("设置", "设", Route.Settings),
)

/**
 * 应用根 Composable：Scaffold + 四 Tab 导航（称重/用户/历史/设置）；
 * DeviceScan 为起始页，UserEdit/Result 为压栈页。
 * [SessionViewModel] 挂在根级（跨屏共享：扫描/设备/会话/待保存结果）。
 */
@Composable
fun App(container: AppContainer) {
    MaterialTheme {
        val navController = rememberNavController()
        val sessionVm: SessionViewModel = viewModel { SessionViewModel(container) }
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
                            val selected = currentRouteClass?.contains(tab.route::class.simpleName ?: "@") == true
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
                                icon = { Text(tab.glyph) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Route.DeviceScan,
                modifier = Modifier.padding(padding),
            ) {
                composable<Route.DeviceScan> {
                    DeviceScanScreen(
                        vm = sessionVm,
                        onDone = {
                            navController.navigate(Route.WeighIn) {
                                popUpTo(Route.DeviceScan) { inclusive = true }
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
                    SettingsScreen(vm = sessionVm)
                }
            }
        }
    }
}
