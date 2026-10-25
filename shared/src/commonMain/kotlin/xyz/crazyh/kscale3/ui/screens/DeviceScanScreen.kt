package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.crazyh.kscale3.isIosPlatform
import xyz.crazyh.kscale3.platform.rememberBluetoothPermission
import xyz.crazyh.kscale3.protocol.isValidMacAddress
import xyz.crazyh.kscale3.ui.components.SectionTitle
import xyz.crazyh.kscale3.ui.vm.SessionViewModel
import xyz.crazyh.kscale3.ui.vm.isLikelyScale

/**
 * 设备扫描/配对屏（PLAN UI-1）：
 * 扫描列表（名称/RSSI，名称过滤 `HaigeBLE|Scale 3|HUAWEI Scale`）
 * + MAC 输入框（iOS 必填并提示"见秤身标签"；Android 可留空取扫描地址，也可手填覆盖）
 * + 保存设备 → 进入主界面。
 */
@Composable
fun DeviceScanScreen(
    vm: SessionViewModel,
    onDone: () -> Unit,
) {
    val scanning by vm.scanning.collectAsState()
    val results by vm.scanResults.collectAsState()
    val scanError by vm.scanError.collectAsState()
    val device by vm.device.collectAsState()
    val isIos = remember { isIosPlatform() }
    val permission = rememberBluetoothPermission()

    var macInput by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // 进入页面即申请蓝牙权限（Android 运行时；iOS 无操作）
    LaunchedEffect(Unit) {
        if (!permission.granted) permission.request()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("设备扫描与配对", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)

        // 已保存设备直接可用：扫描列表只含"本次扫描收到的广播"（冷启动时为空），
        // 不应让用户误以为配对丢失、必须重新扫。
        device?.let { saved ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("已保存设备", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    Text(saved.name)
                    Text(
                        "MAC ${saved.mac}" +
                            if (saved.bleId != saved.mac) " · BLE ${saved.bleId}" else "",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onDone, modifier = Modifier.padding(top = 8.dp)) {
                        Text("使用已保存设备")
                    }
                }
            }
        }

        if (!permission.granted) {
            Text(
                "需要蓝牙权限才能扫描与连接——请点「授权」并在系统弹窗中允许。",
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(onClick = { permission.request() }) { Text("授权蓝牙权限") }
        }

        SectionTitle("1. MAC 地址（RootKey 密钥输入）")
        OutlinedTextField(
            value = macInput,
            onValueChange = { macInput = it; error = null },
            label = { Text(if (isIos) "秤身标签 MAC（必填）" else "MAC（留空取扫描地址，可手填覆盖）") },
            supportingText = {
                Text(if (isIos) "iPhone 无法读取物理 MAC，请查看秤身标签" else "隐私随机化时扫描地址≠标签 MAC，请以标签为准")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("2. 选择设备")
        Row {
            Button(
                onClick = { vm.startScan() },
                enabled = !scanning && permission.granted,
            ) { Text(if (scanning) "扫描中…" else "开始扫描") }
            OutlinedButton(onClick = { vm.stopScan() }, enabled = scanning, modifier = Modifier.padding(start = 8.dp)) {
                Text("停止")
            }
        }
        scanError?.let {
            Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
        }

        val adCount by vm.adCount.collectAsState()
        Text(
            "已收到 $adCount 条广播" +
                if (adCount == 0 && scanning) "——若一直为 0：请上秤唤醒（秤待机时不广播），并确认系统蓝牙已开" else "",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            items(results, key = { it.identifier }) { adv ->
                Card(
                    onClick = {
                        selectedId = adv.identifier
                        // Android：扫描地址多为真实 MAC，自动带入（仍可手填覆盖）
                        if (!isIos && macInput.isBlank() && adv.identifier.isValidMacAddress()) {
                            macInput = adv.identifier
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(adv.name ?: "(未知设备)")
                            Text(adv.identifier, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                        if (adv.isLikelyScale()) {
                            Text(
                                "疑似秤",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        Text("${adv.rssi} dBm")
                    }
                }
            }
        }

        error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }

        val selected = results.firstOrNull { it.identifier == selectedId }
        Button(
            onClick = {
                val ok = vm.saveDevice(
                    name = selected?.name,
                    bleId = selected?.identifier,
                    macInput = macInput,
                )
                if (ok) onDone() else error = "MAC 格式无效（应为 XX:XX:XX:XX:XX:XX）"
            },
            enabled = permission.granted && (selected != null || macInput.isValidMacAddress()),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(if (device != null) "保存并更新设备" else "保存设备并进入主界面")
        }
    }
}
