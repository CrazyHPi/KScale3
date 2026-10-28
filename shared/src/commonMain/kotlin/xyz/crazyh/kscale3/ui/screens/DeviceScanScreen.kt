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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.crazyh.kscale3.ble.BleAdvertisement
import xyz.crazyh.kscale3.domain.PairedDevice
import xyz.crazyh.kscale3.isIosPlatform
import xyz.crazyh.kscale3.platform.rememberBluetoothPermission
import xyz.crazyh.kscale3.protocol.isValidMacAddress
import xyz.crazyh.kscale3.ui.components.SectionTitle
import xyz.crazyh.kscale3.ui.preview.previewDevice
import xyz.crazyh.kscale3.ui.vm.SessionViewModel
import xyz.crazyh.kscale3.ui.vm.isLikelyScale

/**
 * 设备扫描/配对屏（PLAN UI-1）：
 * 扫描列表（名称/RSSI，疑似秤置顶）
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
    val adCount by vm.adCount.collectAsState()
    val isIos = remember { isIosPlatform() }
    val permission = rememberBluetoothPermission()

    // 进入页面即申请蓝牙权限（Android 运行时；iOS 无操作）
    LaunchedEffect(Unit) {
        if (!permission.granted) permission.request()
    }

    DeviceScanContent(
        scanning = scanning,
        results = results,
        scanError = scanError,
        device = device,
        adCount = adCount,
        permissionGranted = permission.granted,
        isIos = isIos,
        onRequestPermission = { permission.request() },
        onStartScan = vm::startScan,
        onStopScan = vm::stopScan,
        onSaveDevice = { name, bleId, macInput -> vm.saveDevice(name, bleId, macInput) },
        onDone = onDone,
    )
}

/** 无 ViewModel 的扫描/配对 UI（供 @Preview）。 */
@Composable
private fun DeviceScanContent(
    scanning: Boolean,
    results: List<BleAdvertisement>,
    scanError: String?,
    device: PairedDevice?,
    adCount: Int,
    permissionGranted: Boolean,
    isIos: Boolean,
    onRequestPermission: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSaveDevice: (name: String?, bleId: String?, macInput: String) -> Boolean,
    onDone: () -> Unit,
) {
    var macInput by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("设备扫描与配对", style = MaterialTheme.typography.headlineSmall)

        // 已保存设备直接可用：扫描列表只含"本次扫描收到的广播"（冷启动时为空），
        // 不应让用户误以为配对丢失、必须重新扫。
        device?.let { saved ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("已保存设备", style = MaterialTheme.typography.titleSmall)
                    Text(saved.name)
                    Text(
                        "MAC ${saved.mac}" +
                                if (saved.bleId != saved.mac) " · BLE ${saved.bleId}" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onDone, modifier = Modifier.padding(top = 8.dp)) {
                        Text("使用已保存设备")
                    }
                }
            }
        }

        if (!permissionGranted) {
            Text(
                "需要蓝牙权限才能扫描与连接——请点「授权」并在系统弹窗中允许。",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(onClick = onRequestPermission) { Text("授权蓝牙权限") }
        }

        SectionTitle("设备 MAC 地址")
        OutlinedTextField(
            value = macInput,
            onValueChange = { macInput = it; error = null },
            label = { Text(if (isIos) "秤身标签 MAC（必填）" else "MAC（留空取扫描地址，可手填覆盖）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("扫描 / 选择设备")
        Row {
            Button(
                onClick = onStartScan,
                enabled = !scanning && permissionGranted,
            ) { Text(if (scanning) "扫描中…" else "开始扫描") }
            OutlinedButton(
                onClick = onStopScan,
                enabled = scanning,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("停止")
            }
        }
        scanError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Text(
            "已收到 $adCount 条广播" +
                    if (adCount == 0 && scanning) "——若一直为 0：请上秤唤醒（秤待机时不广播），并确认系统蓝牙已开" else "",
            style = MaterialTheme.typography.bodySmall,
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
                            Text(
                                adv.identifier,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (adv.isLikelyScale()) {
                            Text(
                                "疑似秤",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        Text("${adv.rssi} dBm")
                    }
                }
            }
        }

        val selected = results.firstOrNull { it.identifier == selectedId }

        if (isIos) {
            val hint = when {
                selected == null ->
                    "iOS 连接需先扫描并选择设备（CoreBluetooth UUID）；MAC 只用于 RootKey，不能代替扫描。"

                !macInput.isValidMacAddress() -> "请填写秤身标签上的 MAC（RootKey 需要）。"
                else -> null
            }
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                val ok = onSaveDevice(
                    selected?.name,
                    selected?.identifier,
                    macInput,
                )
                if (ok) onDone() else error =
                    if (isIos) "请先选择扫描到的设备，并填写秤身标签 MAC"
                    else "MAC 格式无效（应为 XX:XX:XX:XX:XX:XX）"
            },
            enabled = permissionGranted && if (isIos) {
                selected != null && macInput.isValidMacAddress()
            } else {
                selected != null || macInput.isValidMacAddress()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(if (device != null) "保存并更新设备" else "保存设备并进入主界面")
        }
    }
}

@Preview
@Composable
private fun DeviceScanScreenPreview() {
    MaterialTheme {
        DeviceScanContent(
            scanning = true,
            results = listOf(
                BleAdvertisement(
                    name = "HUAWEI Scale 3",
                    identifier = "C8:47:8C:12:34:56",
                    rssi = -42,
                ),
                BleAdvertisement(
                    name = null,
                    identifier = "7A:11:22:33:44:55",
                    rssi = -78,
                ),
            ),
            scanError = null,
            device = null,
            adCount = 2,
            permissionGranted = true,
            isIos = false,
            onRequestPermission = {},
            onStartScan = {},
            onStopScan = {},
            onSaveDevice = { _, _, _ -> true },
            onDone = {},
        )
    }
}

@Preview
@Composable
private fun DeviceScanScreenSavedDevicePreview() {
    MaterialTheme {
        DeviceScanContent(
            scanning = false,
            results = emptyList(),
            scanError = null,
            device = previewDevice,
            adCount = 0,
            permissionGranted = false,
            isIos = false,
            onRequestPermission = {},
            onStartScan = {},
            onStopScan = {},
            onSaveDevice = { _, _, _ -> true },
            onDone = {},
        )
    }
}
