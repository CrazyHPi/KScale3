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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.crazyh.kscale3.domain.PairedDevice
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.session.SessionError
import xyz.crazyh.kscale3.session.SessionState
import xyz.crazyh.kscale3.ui.components.SessionProgressDialog
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.i18n.errorText
import xyz.crazyh.kscale3.ui.i18n.sessionGuide
import xyz.crazyh.kscale3.ui.preview.previewDevice
import xyz.crazyh.kscale3.ui.preview.previewUsers
import xyz.crazyh.kscale3.ui.vm.SessionViewModel

/**
 * 称重屏（PLAN UI-3）：选用户 → 发起 Mode 2 → 按 [SessionState] 显示引导
 * （"请上秤站稳并握住把手"…）；超时/Code 1/2 的恢复动作（重试/去注册/换人）。
 */
@Composable
fun WeighInScreen(
    vm: SessionViewModel,
    onResultReady: () -> Unit,
    onGoRegister: () -> Unit,
    onGoDevice: () -> Unit,
) {
    val users by vm.users.collectAsState()
    val device by vm.device.collectAsState()
    val sessionState by vm.sessionState.collectAsState()
    val busy by vm.busyOrUnit.collectAsState()
    val pending by vm.pendingResult.collectAsState()

    // 测量完成 → 结果屏
    LaunchedEffect(pending) {
        if (pending != null) onResultReady()
    }

    WeighInContent(
        users = users,
        device = device,
        sessionState = sessionState,
        busy = busy,
        onStartWeighIn = vm::startWeighIn,
        onCancelSession = vm::cancelSession,
        onGoRegister = onGoRegister,
        onGoDevice = onGoDevice,
    )
}

/** 无 ViewModel 的称重 UI（供 @Preview）。 */
@Composable
private fun WeighInContent(
    users: List<UserProfile>,
    device: PairedDevice?,
    sessionState: SessionState,
    busy: Boolean,
    onStartWeighIn: (UserProfile) -> Unit,
    onCancelSession: () -> Unit,
    onGoRegister: () -> Unit,
    onGoDevice: () -> Unit,
) {
    var selected by remember { mutableStateOf<UserProfile?>(null) }
    val strings = LocalStrings.current

    // 用户可能在其他 Tab 被删除：列表刷新后清掉失效选择，避免用已删档案发起会话
    LaunchedEffect(users) {
        val selectedId = selected?.id ?: return@LaunchedEffect
        if (users.none { it.id == selectedId }) selected = null
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(strings.weighTitle, style = MaterialTheme.typography.headlineSmall)

        // 已保存设备常驻显示：冷启动不再经过扫描页，用户能确认配对仍在
        device?.let {
            Text(
                strings.weighCurrentDevice(it.name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (device == null) {
            Text(
                strings.weighNoDevice,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(onClick = onGoDevice) { Text(strings.weighGoPair) }
        }

        SectionTitle2(strings.weighSelectUser)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(users, key = { it.id }) { user ->
                Card(
                    onClick = { selected = user },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected?.id == user.id,
                            onClick = { selected = user })
                        Column(Modifier.weight(1f)) {
                            Text(user.name, fontWeight = FontWeight.Bold)
                            Text(
                                if (user.huid != null) strings.usersRegistered
                                else strings.weighUnregistered,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        // 会话引导（非弹窗态也在屏上显示当前步骤）
        when (val s = sessionState) {
            is SessionState.Failed -> {
                Text(
                    strings.errorText(s.error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                RecoveryRow(
                    error = s.error,
                    onRetry = { selected?.let { onStartWeighIn(it) } },
                    onGoRegister = onGoRegister,
                    onChangeUser = { selected = null },
                    onGoDevice = onGoDevice,
                )
            }

            is SessionState.Done -> Text(
                strings.sessionGuide(s),
                color = MaterialTheme.colorScheme.primary,
            )

            SessionState.Idle, SessionState.Connecting, SessionState.Handshaking,
            SessionState.AwaitingTare, SessionState.SyncingTime, SessionState.SendingProfile,
            is SessionState.ProfileResponded, SessionState.AwaitingMeasurement,
            SessionState.Committing, is SessionState.Paired,
                -> if (busy) {
                Text(
                    strings.sessionGuide(s),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }

        Button(
            onClick = { selected?.let { onStartWeighIn(it) } },
            enabled = device != null && selected?.huid != null && !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(strings.weighStart)
        }
    }

    SessionProgressDialog(state = sessionState, onCancel = onCancelSession, onDismiss = {})
}

@Preview
@Composable
private fun WeighInScreenPreview() {
    MaterialTheme {
        WeighInContent(
            users = previewUsers,
            device = previewDevice,
            sessionState = SessionState.Idle,
            busy = false,
            onStartWeighIn = {},
            onCancelSession = {},
            onGoRegister = {},
            onGoDevice = {},
        )
    }
}

@Preview
@Composable
private fun WeighInScreenFailedPreview() {
    MaterialTheme {
        WeighInContent(
            users = previewUsers,
            device = previewDevice,
            sessionState = SessionState.Failed(SessionError.UserNotRecognised),
            busy = false,
            onStartWeighIn = {},
            onCancelSession = {},
            onGoRegister = {},
            onGoDevice = {},
        )
    }
}

/** 超时/Code 1/2 的恢复动作（PLAN UI-3）。 */
@Composable
private fun RecoveryRow(
    error: SessionError,
    onRetry: () -> Unit,
    onGoRegister: () -> Unit,
    onChangeUser: () -> Unit,
    onGoDevice: () -> Unit,
) {
    val strings = LocalStrings.current
    Row {
        when (error) {
            SessionError.MissingTare ->
                Button(onClick = onGoRegister) { Text(strings.weighRecoveryGoRegister) }

            SessionError.UserNotRecognised -> {
                Button(onClick = onGoRegister) { Text(strings.weighRecoveryReRegister) }
                OutlinedButton(
                    onClick = onChangeUser,
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text(strings.weighRecoveryChangeUser) }
            }

            SessionError.MacInvalid ->
                Button(onClick = onGoDevice) { Text(strings.weighRecoveryEditMac) }

            else -> Button(onClick = onRetry) { Text(strings.commonRetry) }
        }
    }
}

@Composable
private fun SectionTitle2(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}
