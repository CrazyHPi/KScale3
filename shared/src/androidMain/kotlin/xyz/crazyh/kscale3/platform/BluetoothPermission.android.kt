package xyz.crazyh.kscale3.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Android 蓝牙运行时权限（minSdk 33，仅现代权限模型，无 legacy BLUETOOTH/LOCATION）。
 * 声明见 androidApp AndroidManifest.xml。
 */
private val REQUIRED_PERMISSIONS: Array<String> = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
)

private fun permissionsGranted(context: Context): Boolean = REQUIRED_PERMISSIONS.all {
    context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
}

@Composable
actual fun rememberBluetoothPermission(): BluetoothPermission {
    val context = LocalContext.current
    var grantedState by remember { mutableStateOf(permissionsGranted(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        grantedState = permissionsGranted(context)
    }
    return remember(grantedState) {
        object : BluetoothPermission {
            override val granted: Boolean get() = grantedState

            override fun request() {
                if (!grantedState) launcher.launch(REQUIRED_PERMISSIONS)
            }
        }
    }
}
