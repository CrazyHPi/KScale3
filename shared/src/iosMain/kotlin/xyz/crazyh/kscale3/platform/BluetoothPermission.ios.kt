package xyz.crazyh.kscale3.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** iOS：CoreBluetooth 首次扫描/连接时系统弹窗（NSBluetoothAlwaysUsageDescription），无运行时 API。 */
@Composable
actual fun rememberBluetoothPermission(): BluetoothPermission = remember {
    object : BluetoothPermission {
        override val granted: Boolean get() = true

        override fun request() = Unit
    }
}
