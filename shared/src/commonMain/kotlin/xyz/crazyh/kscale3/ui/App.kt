package xyz.crazyh.kscale3.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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

/**
 * 应用根 Composable：导航骨架（7 屏，中文文案）。
 * 屏幕实线与 ViewModel 接线在后续阶段（PLAN「UI 屏幕」）。
 */
@Composable
fun App(container: AppContainer) {
    MaterialTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Route.DeviceScan) {
            composable<Route.DeviceScan> {
                DeviceScanScreen()
            }
            composable<Route.Users> {
                UsersScreen()
            }
            composable<Route.UserEdit> { entry ->
                UserEditScreen(userId = entry.toRoute<Route.UserEdit>().userId)
            }
            composable<Route.WeighIn> {
                WeighInScreen()
            }
            composable<Route.Result> {
                ResultScreen()
            }
            composable<Route.History> {
                HistoryScreen()
            }
            composable<Route.Settings> {
                SettingsScreen()
            }
        }
    }
}
