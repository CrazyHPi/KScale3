package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.crazyh.kscale3.ui.components.MetricRow
import xyz.crazyh.kscale3.ui.components.SectionTitle
import xyz.crazyh.kscale3.ui.maskHuid
import xyz.crazyh.kscale3.ui.vm.SessionViewModel

/**
 * 设置屏（PLAN UI-6）：设备信息（MAC/型号/序列号 + 用户 HUID 打码）、
 * 调试开关（帧日志屏，默认关；日志仅帧 hex——载荷均加密、MAC 不上线）。
 * "重新注册"入口在用户管理页（每档案独立 HUID）。
 *
 * @param onForgetDevice 忘记设备并清库后回调（调用方跳回扫描配对页）
 */
@Composable
fun SettingsScreen(vm: SessionViewModel, onForgetDevice: () -> Unit) {
    val device by vm.device.collectAsState()
    val users by vm.users.collectAsState()
    val info by vm.hardwareInfo.collectAsState()
    val log by vm.frameLog.collectAsState()
    val busy by vm.busy.collectAsState()
    var debugLog by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)

        SectionTitle("设备信息")
        if (device == null) {
            Text("尚未配对设备。", color = MaterialTheme.colorScheme.error)
        } else {
            val d = device!!
            MetricRow("名称", d.name)
            MetricRow("MAC（RootKey）", d.mac)
            if (d.bleId != d.mac) MetricRow("BLE 标识", d.bleId)
            MetricRow("型号", info.model ?: d.model ?: "—")
            MetricRow("序列号", info.serial ?: d.sn ?: "—")
            MetricRow("配置状态", info.configCode?.toString() ?: "—")
            OutlinedButton(
                onClick = { confirmForget = true },
                enabled = !busy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("忘记已配对的秤")
            }
        }

        SectionTitle("已注册用户（HUID 打码）")
        val registered = users.filter { it.huid != null }
        if (registered.isEmpty()) {
            Text("暂无——可在「用户」页注册到秤。", style = MaterialTheme.typography.bodySmall)
        } else {
            registered.forEach { user ->
                MetricRow(user.name, maskHuid(user.huid!!))
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("调试帧日志", modifier = Modifier.weight(1f))
            Switch(checked = debugLog, onCheckedChange = { debugLog = it })
        }
        Text(
            "日志仅帧 hex（载荷均加密；MAC/HUID 不上线、不落盘）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (debugLog) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                if (log.isEmpty()) {
                    Text(
                        "（暂无——发起一次称重/注册后显示）",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                log.takeLast(100).forEach { line ->
                    Text(line, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("忘记已配对的秤？") },
            text = {
                Text(
                    "将清除本机保存的秤（MAC / BLE 标识），之后需要重新扫描配对。" +
                            "用户档案、HUID 与历史测量不受影响。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmForget = false
                        scope.launch {
                            vm.forgetDevice()
                            onForgetDevice()
                        }
                    },
                ) {
                    Text("忘记", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("取消") }
            },
        )
    }
}
