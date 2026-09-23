package xyz.crazyh.kscale3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import xyz.crazyh.kscale3.data.db.DriverFactory
import xyz.crazyh.kscale3.di.AppContainer
import xyz.crazyh.kscale3.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = AppContainer(DriverFactory(applicationContext))
        setContent {
            App(container)
        }
    }
}
