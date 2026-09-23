package xyz.crazyh.kscale3.platform

import androidx.activity.ComponentActivity

/**
 * Android 蓝牙运行时权限（minSdk 33，仅现代权限模型，无 legacy BLUETOOTH/LOCATION）。
 * 声明见 androidApp AndroidManifest.xml。
 */
object BluetoothPermission {

    val REQUIRED_PERMISSIONS: Array<String> = arrayOf(
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.BLUETOOTH_CONNECT,
    )

    /**
     * 运行时权限请求桩。实现方案（Phase 5）：
     * `ActivityResultContracts.RequestMultiplePermissions` + `rememberLauncherForActivityResult`
     * （或 `ActivityResultContracts.StartActivityForResult` 式封装），结果回调 [onResult]。
     */
    fun request(activity: ComponentActivity, onResult: (Boolean) -> Unit): Unit =
        TODO("Phase 5: polish — Activity Result API")
}
