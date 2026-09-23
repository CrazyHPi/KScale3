package xyz.crazyh.kscale3

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import xyz.crazyh.kscale3.data.db.DriverFactory
import xyz.crazyh.kscale3.di.AppContainer
import xyz.crazyh.kscale3.ui.App

fun MainViewController() = ComposeUIViewController {
    val container = remember { AppContainer(DriverFactory()) }
    App(container)
}
