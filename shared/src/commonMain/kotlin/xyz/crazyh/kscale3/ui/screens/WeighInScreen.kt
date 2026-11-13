package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.crazyh.kscale3.domain.Measurement
import xyz.crazyh.kscale3.domain.PairedDevice
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.session.SessionError
import xyz.crazyh.kscale3.session.SessionState
import xyz.crazyh.kscale3.ui.components.AvatarBadge
import xyz.crazyh.kscale3.ui.components.InfoBanner
import xyz.crazyh.kscale3.ui.components.InitialAvatar
import xyz.crazyh.kscale3.ui.components.MonoText
import xyz.crazyh.kscale3.ui.components.ScaleGlyph
import xyz.crazyh.kscale3.ui.components.StatusPill
import xyz.crazyh.kscale3.ui.components.rounded
import xyz.crazyh.kscale3.ui.i18n.LocalAppLanguage
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.i18n.errorText
import xyz.crazyh.kscale3.ui.i18n.formatMonthDay
import xyz.crazyh.kscale3.ui.i18n.sessionGuide
import xyz.crazyh.kscale3.ui.preview.previewDevice
import xyz.crazyh.kscale3.ui.preview.previewUsers
import xyz.crazyh.kscale3.ui.theme.KScaleTheme
import xyz.crazyh.kscale3.ui.theme.LocalKScaleExtraColors
import xyz.crazyh.kscale3.ui.vm.SessionViewModel

/**
 * 称重准备（设计稿 04）：设备条 → 成员头像选择 → 上秤引导 → 开始称重。
 * 会话状态全部内联展示（不再弹阻塞对话框），进行中主按钮变"取消称重"。
 */
@Composable
fun WeighInScreen(
    vm: SessionViewModel,
    onResultReady: () -> Unit,
    onGoRegister: () -> Unit,
    onGoDevice: () -> Unit,
    onAddMember: () -> Unit,
) {
    val users by vm.users.collectAsState()
    val device by vm.device.collectAsState()
    val sessionState by vm.sessionState.collectAsState()
    val busy by vm.busyOrUnit.collectAsState()
    val pending by vm.pendingResult.collectAsState()
    val latestByUser by vm.latestByUser.collectAsState()

    LaunchedEffect(pending) {
        if (pending != null) onResultReady()
    }

    WeighInContent(
        users = users,
        device = device,
        sessionState = sessionState,
        busy = busy,
        latestByUser = latestByUser,
        onStartWeighIn = vm::startWeighIn,
        onCancelSession = vm::cancelSession,
        onGoRegister = onGoRegister,
        onGoDevice = onGoDevice,
        onAddMember = onAddMember,
    )
}

@Composable
private fun WeighInContent(
    users: List<UserProfile>,
    device: PairedDevice?,
    sessionState: SessionState,
    busy: Boolean,
    latestByUser: Map<Long, Measurement>,
    onStartWeighIn: (UserProfile) -> Unit,
    onCancelSession: () -> Unit,
    onGoRegister: () -> Unit,
    onGoDevice: () -> Unit,
    onAddMember: () -> Unit,
) {
    var selected by remember { mutableStateOf<UserProfile?>(null) }
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current
    val extra = LocalKScaleExtraColors.current

    // 默认选第一位已注册成员；成员被删除/注册状态变化时同步对象。
    LaunchedEffect(users) {
        val current = selected
        selected = when {
            current != null -> users.firstOrNull { it.id == current.id }
                ?: users.firstOrNull { it.huid != null }
                ?: users.firstOrNull()

            else -> users.firstOrNull { it.huid != null } ?: users.firstOrNull()
        }
    }

    val failed = sessionState as? SessionState.Failed
    val guideTitle = when (sessionState) {
        SessionState.Idle -> strings.weighGuideTitle
        SessionState.Connecting, SessionState.Handshaking -> strings.weighStateConnecting
        SessionState.AwaitingTare -> strings.weighStateAwaitingTare
        SessionState.SyncingTime, SessionState.SendingProfile,
        is SessionState.ProfileResponded -> strings.weighStateSyncing

        SessionState.AwaitingMeasurement -> strings.weighStateMeasuring
        SessionState.Committing, is SessionState.Done -> strings.weighStateSaving
        is SessionState.Paired -> strings.weighStatePaired
        is SessionState.Failed -> strings.weighStateFailed
    }
    val guideSubtitle = when (sessionState) {
        SessionState.Idle -> strings.weighGuideSubtitle
        is SessionState.Failed -> ""
        else -> strings.sessionGuide(sessionState)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                strings.weighTitle,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = onGoDevice) {
                    Icon(
                        Icons.Filled.Bluetooth,
                        contentDescription = strings.weighGoPair,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (device != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 9.dp, end = 9.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(extra.success)
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }
            }
        }

        DeviceBar(
            device = device,
            connecting = busy,
            onClick = onGoDevice,
        )

        Text(
            strings.weighMembers,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 10.dp),
        )

        if (users.isEmpty()) {
            InfoBanner(text = strings.weighNoMembers)
        } else {
            LazyRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                items(users, key = { it.id }) { user ->
                    MemberSelectorItem(
                        user = user,
                        selected = selected?.id == user.id,
                        onClick = {
                            if (user.huid != null) selected = user else onGoRegister()
                        },
                    )
                }
                item(key = "add") {
                    AddMemberItem(onClick = onAddMember)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    ScaleGlyph(
                        modifier = Modifier.size(160.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 12.dp, end = 12.dp)
                                .size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
                Text(
                    guideTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                if (guideSubtitle.isNotBlank()) {
                    Text(
                        guideSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
                    )
                }
            }
        }

        failed?.let { error ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Text(
                    strings.errorText(error.error),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            RecoveryRow(
                error = error.error,
                onRetry = { selected?.let(onStartWeighIn) },
                onGoRegister = onGoRegister,
                onChangeUser = { selected = null },
                onGoDevice = onGoDevice,
            )
            Spacer(Modifier.height(8.dp))
        }

        when {
            device == null -> PrimaryAction(
                label = strings.weighGoPair,
                onClick = onGoDevice,
            )

            users.isEmpty() -> PrimaryAction(
                label = strings.weighAddMemberAction,
                onClick = onAddMember,
            )

            busy -> OutlinedButton(
                onClick = onCancelSession,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(strings.weighCancelOperation)
            }

            else -> PrimaryAction(
                label = if (failed != null) strings.commonRetry else strings.weighStart,
                onClick = { selected?.let(onStartWeighIn) },
                enabled = selected?.huid != null,
            )
        }

        val latest = selected?.let { latestByUser[it.id] }
        if (latest != null) {
            Text(
                strings.weighLastWeigh(
                    monthDay = formatMonthDay(latest.measuredAt, language),
                    weight = "${latest.weightKg.rounded(1)} kg",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DeviceBar(
    device: PairedDevice?,
    connecting: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalKScaleExtraColors.current
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        if (device == null) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "${strings.weighDeviceUnpaired} · ${strings.profileGoPair}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        device.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    MonoText(device.mac)
                }
                if (connecting) {
                    StatusPill(
                        text = strings.weighDeviceConnecting,
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        leadingDot = true,
                    )
                } else {
                    StatusPill(
                        text = strings.weighDevicePaired,
                        contentColor = extra.success,
                        containerColor = extra.successContainer,
                        leadingDot = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberSelectorItem(
    user: UserProfile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalKScaleExtraColors.current
    val registered = user.huid != null
    Column(
        Modifier.width(78.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    Modifier
                        .matchParentSize()
                        .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
            InitialAvatar(
                name = user.name,
                size = 56.dp,
                containerColor = if (registered)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (registered)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                badge = if (registered) AvatarBadge.Registered else AvatarBadge.Locked,
            )
        }
        Text(
            user.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            if (registered) strings.weighRegisteredShort else strings.usersUnregistered,
            style = MaterialTheme.typography.labelSmall,
            color = if (registered) extra.success else extra.amber,
        )
    }
}

@Composable
private fun AddMemberItem(onClick: () -> Unit) {
    val strings = LocalStrings.current
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier.width(78.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(56.dp)) {
                drawCircle(
                    color = outline,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx(), 4.dp.toPx())
                        ),
                    ),
                )
            }
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            strings.weighAdd,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            strings.weighNewMember,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun RecoveryRow(
    error: SessionError,
    onRetry: () -> Unit,
    onGoRegister: () -> Unit,
    onChangeUser: () -> Unit,
    onGoDevice: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (error) {
            SessionError.MissingTare -> TextButton(onClick = onGoRegister) {
                Text(strings.weighRecoveryGoRegister)
            }

            SessionError.UserNotRecognised -> {
                TextButton(onClick = onGoRegister) {
                    Text(strings.weighRecoveryReRegister)
                }
                TextButton(onClick = onChangeUser) {
                    Text(strings.weighRecoveryChangeUser)
                }
            }

            SessionError.MacInvalid -> TextButton(onClick = onGoDevice) {
                Text(strings.weighRecoveryEditMac)
            }

            else -> TextButton(onClick = onRetry) { Text(strings.commonRetry) }
        }
    }
}

@Preview
@Composable
private fun WeighInScreenPreview() {
    KScaleTheme {
        WeighInContent(
            users = previewUsers,
            device = previewDevice,
            sessionState = SessionState.Idle,
            busy = false,
            latestByUser = emptyMap(),
            onStartWeighIn = {},
            onCancelSession = {},
            onGoRegister = {},
            onGoDevice = {},
            onAddMember = {},
        )
    }
}

@Preview
@Composable
private fun WeighInScreenFailedPreview() {
    KScaleTheme {
        WeighInContent(
            users = previewUsers,
            device = previewDevice,
            sessionState = SessionState.Failed(SessionError.UserNotRecognised),
            busy = false,
            latestByUser = emptyMap(),
            onStartWeighIn = {},
            onCancelSession = {},
            onGoRegister = {},
            onGoDevice = {},
            onAddMember = {},
        )
    }
}
