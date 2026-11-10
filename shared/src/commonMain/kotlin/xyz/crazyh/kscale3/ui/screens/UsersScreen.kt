package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.crazyh.kscale3.domain.PairedDevice
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.session.SessionState
import xyz.crazyh.kscale3.ui.components.SessionProgressDialog
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.i18n.errorText
import xyz.crazyh.kscale3.ui.i18n.sessionGuide
import xyz.crazyh.kscale3.ui.preview.previewDevice
import xyz.crazyh.kscale3.ui.preview.previewUsers
import xyz.crazyh.kscale3.ui.vm.SessionViewModel

/**
 * 用户列表屏（PLAN UI-2）：档案列表、"已注册/未注册"徽章、目标体重、
 * "注册到秤（Mode 1）"入口（重注册=重新生成 HUID）、删除档案（二次确认，连带历史测量）。
 */
@Composable
fun UsersScreen(
    vm: SessionViewModel,
    onEditUser: (Long?) -> Unit,
) {
    val users by vm.users.collectAsState()
    val device by vm.device.collectAsState()
    val sessionState by vm.sessionState.collectAsState()
    val busy by vm.busyOrUnit.collectAsState()
    val scope = rememberCoroutineScope()

    UsersContent(
        users = users,
        device = device,
        sessionState = sessionState,
        busy = busy,
        onEditUser = onEditUser,
        onRegister = vm::startRegistration,
        onDeleteUser = { user -> scope.launch { vm.deleteUser(user) } },
        onCancelSession = vm::cancelSession,
    )
}

/** 无 ViewModel 的用户列表 UI（供 @Preview）。 */
@Composable
private fun UsersContent(
    users: List<UserProfile>,
    device: PairedDevice?,
    sessionState: SessionState,
    busy: Boolean,
    onEditUser: (Long?) -> Unit,
    onRegister: (UserProfile) -> Unit,
    onDeleteUser: (UserProfile) -> Unit,
    onCancelSession: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<UserProfile?>(null) }
    val strings = LocalStrings.current

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.usersTitle, style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { onEditUser(null) }, enabled = !busy) { Text(strings.usersAdd) }
        }

        if (device == null) {
            Text(
                strings.usersNoDevice,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        if (users.isEmpty()) {
            Text(strings.usersEmpty, modifier = Modifier.padding(top = 24.dp))
        }

        // 终态结论条
        when (val s = sessionState) {
            is SessionState.Failed -> Text(
                strings.errorText(s.error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            is SessionState.Paired -> Text(
                strings.sessionGuide(s),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            else -> Unit
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(users, key = { it.id }) { user ->
                UserCard(
                    user = user,
                    busy = busy,
                    canRegister = device != null,
                    onEdit = { onEditUser(user.id) },
                    onRegister = { onRegister(user) },
                    onDelete = { pendingDelete = user },
                )
            }
        }
    }

    pendingDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(strings.usersDeleteTitle(user.name)) },
            text = { Text(strings.usersDeleteMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteUser(user)
                    },
                ) {
                    Text(strings.commonDelete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(strings.commonCancel) }
            },
        )
    }

    SessionProgressDialog(state = sessionState, onCancel = onCancelSession, onDismiss = {})
}

@Preview
@Composable
private fun UsersScreenPreview() {
    MaterialTheme {
        UsersContent(
            users = previewUsers,
            device = previewDevice,
            sessionState = SessionState.Idle,
            busy = false,
            onEditUser = {},
            onRegister = {},
            onDeleteUser = {},
            onCancelSession = {},
        )
    }
}

@Composable
private fun UserCard(
    user: UserProfile,
    busy: Boolean,
    canRegister: Boolean,
    onEdit: () -> Unit,
    onRegister: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (user.huid != null) strings.usersRegistered
                            else strings.usersUnregistered
                        )
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                strings.usersCardSummary(
                    age = user.age,
                    heightCm = user.heightCm,
                    goalKg = user.goalWeightKg,
                    lastKg = user.lastWeightKg,
                ),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Row(Modifier.padding(top = 4.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    enabled = !busy
                ) {
                    Text(strings.commonEdit)
                }
                Button(
                    onClick = onRegister,
                    enabled = canRegister && !busy,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(
                        if (user.huid != null) strings.usersReRegister
                        else strings.usersRegister
                    )
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(strings.commonDelete)
                }
            }
        }
    }
}
