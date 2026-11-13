package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.crazyh.kscale3.domain.LanguageSetting
import xyz.crazyh.kscale3.domain.PairedDevice
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.domain.WeightUnit
import xyz.crazyh.kscale3.session.HardwareInfo
import xyz.crazyh.kscale3.session.SessionError
import xyz.crazyh.kscale3.ui.components.ConfirmDialog
import xyz.crazyh.kscale3.ui.components.KCard
import xyz.crazyh.kscale3.ui.components.MonoText
import xyz.crazyh.kscale3.ui.components.ScaleGlyph
import xyz.crazyh.kscale3.ui.components.SectionLabel
import xyz.crazyh.kscale3.ui.components.SettingRow
import xyz.crazyh.kscale3.ui.components.StatusPill
import xyz.crazyh.kscale3.ui.i18n.AppLanguage
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.i18n.errorText
import xyz.crazyh.kscale3.ui.i18n.languageOptionName
import xyz.crazyh.kscale3.ui.i18n.languageSettingValue
import xyz.crazyh.kscale3.ui.i18n.weightUnitName
import xyz.crazyh.kscale3.ui.maskHuid
import xyz.crazyh.kscale3.ui.preview.previewDevice
import xyz.crazyh.kscale3.ui.preview.previewHardwareInfo
import xyz.crazyh.kscale3.ui.preview.previewUsers
import xyz.crazyh.kscale3.ui.theme.KScaleTheme
import xyz.crazyh.kscale3.ui.theme.LocalKScaleExtraColors
import xyz.crazyh.kscale3.ui.theme.MonoTextStyle
import xyz.crazyh.kscale3.ui.vm.SessionViewModel

/**
 * 设置（设计稿 06/07）：我的秤 / 偏好设置 / 设备维护 / 高级。
 * 删除秤与重置秤都需要二次确认；调试开关控制 HUID 打码与帧日志入口。
 */
@Composable
fun SettingsScreen(
    vm: SessionViewModel,
    languageSetting: LanguageSetting,
    effectiveLanguage: AppLanguage,
    onSetLanguage: (LanguageSetting) -> Unit,
    onForgetDevice: () -> Unit,
    onGoDevice: () -> Unit,
) {
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

    // 不在进屏时主动读秤：秤未开机时会长时间占用 busyOrUnit，
    // 导致档案页的编辑/注册入口被一并禁用。单位值先取数据库缓存，
    // 用户真正切换单位时再走 0x5A 会话。
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
        languageSetting = languageSetting,
        effectiveLanguage = effectiveLanguage,
        onSetWeightUnit = vm::setWeightUnit,
        onSetLanguage = onSetLanguage,
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
        onGoDevice = onGoDevice,
    )
}

@Composable
private fun SettingsContent(
    device: PairedDevice?,
    users: List<UserProfile>,
    info: HardwareInfo,
    log: List<String>,
    busy: Boolean,
    weightUnit: WeightUnit?,
    unitBusy: Boolean,
    unitError: SessionError?,
    resetBusy: Boolean,
    resetError: SessionError?,
    languageSetting: LanguageSetting,
    effectiveLanguage: AppLanguage,
    onSetWeightUnit: (WeightUnit) -> Unit,
    onSetLanguage: (LanguageSetting) -> Unit,
    onFactoryReset: () -> Unit,
    onForgetDevice: () -> Unit,
    onGoDevice: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalKScaleExtraColors.current
    var showDebugInfo by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    var confirmFactoryReset by remember { mutableStateOf(false) }
    var showUnitPicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showFrameLog by remember { mutableStateOf(false) }

    val registered = users.filter { it.huid != null }
    val firstHuid = registered.firstOrNull()?.huid

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Text(
            strings.settingsTitle,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 8.dp, top = 14.dp),
        )

        SectionLabel(strings.settingsMyScale, Modifier.padding(start = 0.dp))
        KCard(
            Modifier.fillMaxWidth(),
            onClick = onGoDevice,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    ScaleGlyph(
                        Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(14.dp))
                if (device == null) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            strings.settingsNoDeviceShort,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            strings.settingsGoPair,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(
                            device.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        MonoText(device.mac)
                        Text(
                            "${strings.settingsModel} ${info.model ?: device.model ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusPill(
                        text = strings.settingsPaired,
                        contentColor = extra.success,
                        containerColor = extra.successContainer,
                        leadingDot = true,
                    )
                }
            }
        }

        SectionLabel(strings.settingsPreferences)
        KCard(Modifier.fillMaxWidth()) {
            SettingRow(
                icon = Icons.Outlined.Language,
                title = strings.settingsLanguage,
                value = strings.languageSettingValue(languageSetting, effectiveLanguage),
                onClick = { showLanguagePicker = true },
                trailing = { Chevron() },
            )
            HorizontalDivider(
                Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            SettingRow(
                icon = Icons.Outlined.MonitorWeight,
                title = strings.settingsWeightUnit,
                value = weightUnit?.let { strings.weightUnitName(it) }
                    ?: if (unitBusy) strings.settingsConnectingScale else "—",
                enabled = device != null && !busy,
                onClick = { showUnitPicker = true },
                trailing = { Chevron() },
            )
        }
        unitError?.let {
            Text(
                strings.errorText(it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp, top = 6.dp),
            )
        }

        SectionLabel(strings.settingsMaintenance)
        KCard(Modifier.fillMaxWidth()) {
            SettingRow(
                icon = Icons.Outlined.LinkOff,
                title = strings.settingsDeleteScale,
                subtitle = strings.settingsDeleteScaleSubtitle,
                destructive = true,
                enabled = device != null && !resetBusy,
                onClick = { confirmForget = true },
            )
            HorizontalDivider(
                Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            SettingRow(
                icon = Icons.Outlined.RestartAlt,
                title = if (resetBusy) strings.settingsFactoryResetting
                else strings.settingsResetScale,
                subtitle = strings.settingsResetScaleSubtitle,
                destructive = true,
                enabled = device != null && !busy,
                onClick = { confirmFactoryReset = true },
            )
        }
        resetError?.let {
            Text(
                strings.errorText(it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp, top = 6.dp),
            )
        }

        SectionLabel(strings.settingsAdvanced)
        KCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    strings.settingsDebugInfo,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = showDebugInfo, onCheckedChange = { showDebugInfo = it })
            }
            HorizontalDivider(
                Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row {
                    InfoCell(
                        label = strings.settingsModel,
                        value = info.model ?: device?.model ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    InfoCell(
                        label = strings.settingsSerial,
                        value = info.serial ?: device?.sn ?: "—",
                        modifier = Modifier.weight(1f),
                        mono = true,
                    )
                }
                Row(Modifier.padding(top = 12.dp)) {
                    InfoCell(
                        label = strings.settingsConfigState,
                        value = info.configCode?.toString() ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    InfoCell(
                        label = strings.settingsRegisteredHuid,
                        value = firstHuid?.let { if (showDebugInfo) it else maskHuid(it) }
                            ?: "—",
                        modifier = Modifier.weight(1f),
                        mono = true,
                    )
                }
                if (showDebugInfo) {
                    TextButton(
                        onClick = { showFrameLog = true },
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                    ) {
                        Text("${strings.settingsViewFrameLog}  ›")
                    }
                }
            }
        }

        Spacer(Modifier.size(24.dp))
    }

    if (showUnitPicker) {
        PickerDialog(
            title = strings.settingsWeightUnit,
            options = WeightUnit.entries.map { it to strings.weightUnitName(it) },
            selected = weightUnit,
            onSelect = {
                showUnitPicker = false
                onSetWeightUnit(it)
            },
            onDismiss = { showUnitPicker = false },
        )
    }

    if (showLanguagePicker) {
        PickerDialog(
            title = strings.settingsLanguage,
            options = LanguageSetting.entries.map { it to strings.languageOptionName(it) },
            selected = languageSetting,
            onSelect = {
                showLanguagePicker = false
                onSetLanguage(it)
            },
            onDismiss = { showLanguagePicker = false },
        )
    }

    if (showFrameLog) {
        AlertDialog(
            onDismissRequest = { showFrameLog = false },
            title = { Text(strings.settingsFrameLogTitle) },
            text = {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                ) {
                    if (log.isEmpty()) {
                        Text(
                            strings.settingsLogEmpty,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        log.takeLast(200).forEach { line ->
                            Text(line, style = MonoTextStyle)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFrameLog = false }) {
                    Text(strings.commonClose)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
        )
    }

    if (confirmForget) {
        ConfirmDialog(
            title = strings.settingsForgetDeviceTitle,
            message = strings.settingsForgetDeviceMessage,
            confirmLabel = strings.settingsForgetConfirm,
            icon = Icons.Outlined.LinkOff,
            onConfirm = {
                confirmForget = false
                onForgetDevice()
            },
            onDismiss = { confirmForget = false },
        )
    }

    if (confirmFactoryReset) {
        ConfirmDialog(
            title = strings.settingsFactoryResetTitle,
            message = strings.settingsFactoryResetMessage,
            confirmLabel = strings.settingsFactoryResetConfirm,
            icon = Icons.Outlined.RestartAlt,
            onConfirm = {
                confirmFactoryReset = false
                onFactoryReset()
            },
            onDismiss = { confirmFactoryReset = false },
        )
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun InfoCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (mono) {
            MonoText(value, color = MaterialTheme.colorScheme.onSurface)
        } else {
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun <T> PickerDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value) },
                        )
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.commonCancel) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    KScaleTheme {
        Surface {
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
                languageSetting = LanguageSetting.System,
                effectiveLanguage = AppLanguage.Chinese,
                onSetWeightUnit = {},
                onSetLanguage = {},
                onFactoryReset = {},
                onForgetDevice = {},
                onGoDevice = {},
            )
        }
    }
}
