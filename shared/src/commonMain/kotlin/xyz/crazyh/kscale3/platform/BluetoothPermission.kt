package xyz.crazyh.kscale3.platform

import androidx.compose.runtime.Composable

/**
 * 蓝牙权限抽象（PLAN 阶段 5）：
 * Android = BLUETOOTH_SCAN + BLUETOOTH_CONNECT 运行时申请（Activity Result API）；
 * iOS = CoreBluetooth 系统弹窗自动触发，恒视为已授权。
 */
interface BluetoothPermission {
    /** 扫描/连接所需权限是否已授予。 */
    val granted: Boolean

    /** 发起授权（Android 弹系统对话框；iOS 无操作）。 */
    fun request()
}

/** 记忆化权限句柄（随 Compose 生命周期）。 */
@Composable
expect fun rememberBluetoothPermission(): BluetoothPermission
