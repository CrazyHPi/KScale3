package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 设置屏（PLAN UI-6）：设备信息（MAC/HUID/型号/序列号）、重新注册、
 * 调试开关（帧日志屏，默认关；日志不含 MAC/HUID 明文或自动打码）。
 */
@Composable
fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("设置")
        // TODO 阶段4：设备信息（MAC/HUID/型号/序列号）——HUID 打码显示
        // TODO 阶段5：重新注册 + 帧日志调试屏（默认关）
    }
}
