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
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.preview.previewDevice
import xyz.crazyh.kscale3.ui.vm.ScanFailure
import xyz.crazyh.kscale3.ui.vm.SessionViewModel
import xyz.crazyh.kscale3.ui.vm.isLikelyScale

/** 保存设备的本地校验错误（存原因而非已渲染文案，切换语言后可重新映射）。 */
private enum class DeviceScanError { IosSelectionRequired, InvalidMac }

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
    scanError: ScanFailure?,
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
    var error by remember { mutableStateOf<DeviceScanError?>(null) }
    val strings = LocalStrings.current

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(strings.scanTitle, style = MaterialTheme.typography.headlineSmall)

        // 已保存设备直接可用：扫描列表只含"本次扫描收到的广播"（冷启动时为空），
        // 不应让用户误以为配对丢失、必须重新扫。
        device?.let { saved ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(strings.scanSavedDevice, style = MaterialTheme.typography.titleSmall)
                    Text(saved.name)
                    Text(
                        "MAC ${saved.mac}" +
                                if (saved.bleId != saved.mac) " · BLE ${saved.bleId}" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onDone, modifier = Modifier.padding(top = 8.dp)) {
                        Text(strings.scanUseSavedDevice)
                    }
                }
            }
        }

        if (!permissionGranted) {
            Text(
                strings.scanPermissionRequired,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(onClick = onRequestPermission) { Text(strings.scanGrantPermission) }
        }

        SectionTitle(strings.scanMacSection)
        OutlinedTextField(
            value = macInput,
            onValueChange = { macInput = it; error = null },
            label = {
                Text(if (isIos) strings.scanMacLabelIos else strings.scanMacLabelAndroid)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle(strings.scanChooseSection)
        Row {
            Button(
                onClick = onStartScan,
                enabled = !scanning && permissionGranted,
            ) { Text(if (scanning) strings.scanScanning else strings.scanStart) }
            OutlinedButton(
                onClick = onStopScan,
                enabled = scanning,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(strings.scanStop)
            }
        }
        scanError?.let {
            Text(strings.scanFailed(it.detail), color = MaterialTheme.colorScheme.error)
        }

        Text(
            strings.scanBroadcastCount(adCount) +
                    if (adCount == 0 && scanning) strings.scanBroadcastWakeHint else "",
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
                            Text(adv.name ?: strings.scanUnknownDevice)
                            Text(
                                adv.identifier,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (adv.isLikelyScale()) {
                            Text(
                                strings.scanLikelyScale,
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
                selected == null -> strings.scanIosHint
                !macInput.isValidMacAddress() -> strings.scanIosMacHint
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

        error?.let {
            Text(
                when (it) {
                    DeviceScanError.IosSelectionRequired -> strings.scanSaveErrorIosSelection
                    DeviceScanError.InvalidMac -> strings.scanSaveErrorInvalidMac
                },
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = {
                val ok = onSaveDevice(
                    selected?.name,
                    selected?.identifier,
                    macInput,
                )
                if (ok) onDone() else error =
                    if (isIos) DeviceScanError.IosSelectionRequired
                    else DeviceScanError.InvalidMac
            },
            enabled = permissionGranted && if (isIos) {
                selected != null && macInput.isValidMacAddress()
            } else {
                selected != null || macInput.isValidMacAddress()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(if (device != null) strings.scanSaveUpdate else strings.scanSaveAndContinue)
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
