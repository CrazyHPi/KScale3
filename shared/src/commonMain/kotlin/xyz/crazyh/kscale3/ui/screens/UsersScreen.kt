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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.session.SessionState
import xyz.crazyh.kscale3.ui.components.SessionProgressDialog
import xyz.crazyh.kscale3.ui.errorText
import xyz.crazyh.kscale3.ui.sessionGuide
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
    val busy by vm.busy.collectAsState()
    var pendingDelete by remember { mutableStateOf<UserProfile?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("用户管理", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { onEditUser(null) }, enabled = !busy) { Text("添加用户") }
        }

        if (device == null) {
            Text(
                "尚未配对设备——请先在「设备」页扫描配对（注册需要连接秤）。",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        if (users.isEmpty()) {
            Text("暂无用户，点右上角「添加用户」建立档案。", modifier = Modifier.padding(top = 24.dp))
        }

        // 终态结论条
        when (val s = sessionState) {
            is SessionState.Failed -> Text(
                errorText(s.error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            is SessionState.Paired -> Text(
                sessionGuide(s),
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
                    onRegister = { vm.startRegistration(user) },
                    onDelete = { pendingDelete = user },
                )
            }
        }
    }

    pendingDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除用户「${user.name}」？") },
            text = {
                Text(
                    "将删除本机档案与该用户的全部历史测量，操作不可撤销。\n" +
                            "秤上已注册的 HUID 不受影响。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch { vm.deleteUser(user) }
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    SessionProgressDialog(state = sessionState, onCancel = { vm.cancelSession() }, onDismiss = {})
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
                    label = { Text(if (user.huid != null) "已注册" else "未注册") },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                "${user.age} 岁 · ${user.heightCm} cm" +
                        (user.goalWeightKg?.let { " · 目标 ${it} kg" } ?: "") +
                        (user.lastWeightKg?.let { " · 上次 $it kg" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Row(Modifier.padding(top = 4.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    enabled = !busy
                ) {
                    Text("编辑")
                }
                Button(
                    onClick = onRegister,
                    enabled = canRegister && !busy,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(if (user.huid != null) "重新注册到秤" else "注册到秤")
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text("删除")
                }
            }
        }
    }
}
