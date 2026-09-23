package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 设备扫描/配对屏（PLAN UI-1）：
 * 扫描列表（名称/RSSI，名称过滤 `HaigeBLE|Scale 3|HUAWEI Scale`）
 * + MAC 输入框（iOS 必填并提示"见秤身标签"；Android 可留空取 ScanResult MAC，也可手填覆盖）
 * + 配对入口。
 */
@Composable
fun DeviceScanScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("设备扫描")
        // TODO 阶段4：扫描列表（名称/RSSI）
        // TODO 阶段4：MAC 输入框（格式校验 XX:XX:XX:XX:XX:XX）
        // TODO 阶段4：配对按钮 → Mode 1 / 跳转用户注册
    }
}
