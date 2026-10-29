package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.crazyh.kscale3.domain.PairedDevice
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.domain.WeightUnit
import xyz.crazyh.kscale3.session.HardwareInfo
import xyz.crazyh.kscale3.ui.components.MetricRow
import xyz.crazyh.kscale3.ui.components.SectionTitle
import xyz.crazyh.kscale3.ui.components.SettingEntry
import xyz.crazyh.kscale3.ui.maskHuid
import xyz.crazyh.kscale3.ui.preview.previewDevice
import xyz.crazyh.kscale3.ui.preview.previewHardwareInfo
import xyz.crazyh.kscale3.ui.preview.previewUsers
import xyz.crazyh.kscale3.ui.vm.SessionViewModel

/**
 * 设置屏（PLAN UI-6）：设备信息（MAC/型号/序列号 + 用户 HUID 打码）、
 * 秤显示单位（TECH.md §7.3）、秤恢复出厂设置（TECH.md §7.4；成功后清本机配对并回扫描页）、
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
    val busy by vm.busyOrUnit.collectAsState()
    val weightUnit by vm.weightUnit.collectAsState()
    val unitBusy by vm.unitBusy.collectAsState()
    val unitError by vm.unitError.collectAsState()
    val resetBusy by vm.resetBusy.collectAsState()
    val resetError by vm.resetError.collectAsState()
    val scope = rememberCoroutineScope()

    // 进入设置屏时向秤查询当前单位（未配对/会话进行中时 VM 内部忽略）
    LaunchedEffect(device?.id) {
        if (device != null) vm.refreshWeightUnit()
    }

    SettingsContent(
        device = device,
        users = users,
        info = info,
        log = log,
        busy = busy,
        weightUnit = weightUnit,
        unitBusy = unitBusy,
        unitError = unitError,
        resetBusy = resetBusy,
        resetError = resetError,
        onSetWeightUnit = vm::setWeightUnit,
        onFactoryReset = {
            scope.launch {
                if (vm.factoryReset()) onForgetDevice()
            }
        },
        onForgetDevice = {
            scope.launch {
                vm.forgetDevice()
                onForgetDevice()
            }
        },
    )
}

/** 无 ViewModel 的设置 UI（供 @Preview）。 */
@Composable
private fun SettingsContent(
    device: PairedDevice?,
    users: List<UserProfile>,
    info: HardwareInfo,
    log: List<String>,
    busy: Boolean,
    weightUnit: WeightUnit?,
    unitBusy: Boolean,
    unitError: String?,
    resetBusy: Boolean,
    resetError: String?,
    onSetWeightUnit: (WeightUnit) -> Unit,
    onFactoryReset: () -> Unit,
    onForgetDevice: () -> Unit,
) {
    var showDebugInfo by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    var confirmFactoryReset by remember { mutableStateOf(false) }
    var showUnitPicker by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)

        SectionTitle("设备信息")
        if (device == null) {
            Text("尚未配对设备。", color = MaterialTheme.colorScheme.error)
        } else {
            MetricRow("名称", device.name)
            MetricRow("MAC 地址", device.mac)
            if (device.bleId != device.mac) MetricRow("BLE 标识", device.bleId)

            // hide extra info for now, not needed i think
//            MetricRow("型号", info.model ?: device.model ?: "—")
//            MetricRow("序列号", info.serial ?: device.sn ?: "—")
//            MetricRow("配置状态", info.configCode?.toString() ?: "—")
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // 秤显示单位（TECH.md §7.3：0x5A 设置 / 7e6dbc73 查询）
        SettingEntry(
            name = "秤显示单位",
            value = weightUnit?.label ?: if (unitBusy) "连接秤..." else null,
            // 读写单位本身也在 busy 内（busyOrUnit）
            enabled = device != null && !busy,
        ) {
            showUnitPicker = true
        }
        unitError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        SectionTitle("已注册用户")
        val registered = users.filter { it.huid != null }
        if (registered.isEmpty()) {
            Text("暂无——可在「用户」页注册到秤。", style = MaterialTheme.typography.bodySmall)
        } else {
            registered.forEach { user ->
                MetricRow(
                    user.name,
                    if (showDebugInfo) user.huid!! else maskHuid(user.huid!!)
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        OutlinedButton(
            onClick = { confirmForget = true },
            enabled = !busy,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("忘记已配对的秤")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { confirmFactoryReset = true },
            enabled = device != null && !busy,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(if (resetBusy) "正在恢复出厂设置…" else "秤恢复出厂设置")
        }
        resetError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("调试模式", modifier = Modifier.weight(1f))
            Switch(checked = showDebugInfo, onCheckedChange = { showDebugInfo = it })
        }

        if (showDebugInfo) {
            Text(
                "日志 仅显示帧 (hex)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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

    if (showUnitPicker) {
        AlertDialog(
            onDismissRequest = { showUnitPicker = false },
            title = { Text("秤显示单位") },
            text = {
                Column {
                    WeightUnit.entries.forEach { unit ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    showUnitPicker = false
                                    onSetWeightUnit(unit)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = unit == weightUnit,
                                onClick = {
                                    showUnitPicker = false
                                    onSetWeightUnit(unit)
                                },
                            )
                            Text(unit.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUnitPicker = false }) { Text("取消") }
            },
        )
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("忘记已配对的秤？") },
            text = {
                Text(
                    "将清除本机保存的秤\n" +
                            "再次连接需要重新配对\n" +
                            "用户档案与历史体重不会被清楚",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmForget = false
                        onForgetDevice()
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

    if (confirmFactoryReset) {
        AlertDialog(
            onDismissRequest = { confirmFactoryReset = false },
            title = { Text("秤恢复出厂设置？") },
            text = {
                Text(
                    "将清除秤上的绑定与用户数据\n" +
                            "本机配对也会被移除，需要重新配对并注册用户\n" +
                            "本机用户档案与历史体重不会被清除",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmFactoryReset = false
                        onFactoryReset()
                    },
                ) {
                    Text("恢复出厂设置", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmFactoryReset = false }) { Text("取消") }
            },
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsContent(
            device = previewDevice,
            users = previewUsers,
            info = previewHardwareInfo,
            log = listOf(
                "TX 0x21 AUTH_REQ",
                "RX 0x21 randA",
                "TX 0x25 randB+tokenB",
                "RX 0x29 Code 0",
            ),
            busy = false,
            weightUnit = WeightUnit.Kg,
            unitBusy = false,
            unitError = null,
            resetBusy = false,
            resetError = null,
            onSetWeightUnit = {},
            onFactoryReset = {},
            onForgetDevice = {},
        )
    }
}
