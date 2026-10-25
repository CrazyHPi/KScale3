package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xyz.crazyh.kscale3.di.AppContainer
import xyz.crazyh.kscale3.domain.Sex
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.domain.UserProfileDraft
import xyz.crazyh.kscale3.ui.components.SessionProgressDialog
import xyz.crazyh.kscale3.ui.components.rounded
import xyz.crazyh.kscale3.ui.maskHuid
import xyz.crazyh.kscale3.ui.vm.SessionViewModel

/**
 * 用户编辑/新建屏（PLAN UI-2）：姓名/性别/年龄/身高/目标体重表单 + 保存；
 * 已保存的档案带"注册到秤（Mode 1）"入口（新建须先保存再注册）。
 */
@Composable
fun UserEditScreen(
    container: AppContainer,
    vm: SessionViewModel,
    userId: Long?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<UserProfile?>(null) }
    var name by remember { mutableStateOf("") }
    var male by remember { mutableStateOf(true) }
    var age by remember { mutableStateOf("30") }
    var height by remember { mutableStateOf("170") }
    var goal by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    val sessionState by vm.sessionState.collectAsState()
    val busy by vm.busy.collectAsState()

    LaunchedEffect(userId) {
        if (userId != null) {
            container.userRepository.byId(userId).first()?.let { u ->
                loaded = u
                name = u.name
                male = u.sex == Sex.Male
                age = u.age.toString()
                height = u.heightCm.toString()
                goal = u.goalWeightKg?.rounded(1) ?: ""
            }
        }
    }

    fun save() {
        val ageInt = age.toIntOrNull()
        val heightInt = height.toIntOrNull()
        val goalDouble = goal.trim().toDoubleOrNull()
        when {
            name.isBlank() -> error = "请输入姓名"
            ageInt == null || ageInt !in 10..120 -> error = "年龄应为 10–120"
            heightInt == null || heightInt !in 80..230 -> error = "身高应为 80–230 cm"
            goal.isNotBlank() && goalDouble == null -> error = "目标体重格式无效"
            else -> {
                error = null
                scope.launch {
                    val existing = loaded
                    if (existing == null) {
                        container.userRepository.insert(
                            UserProfileDraft(
                                name = name.trim(),
                                sex = if (male) Sex.Male else Sex.Female,
                                age = ageInt,
                                heightCm = heightInt,
                                goalWeightKg = goalDouble,
                            ),
                        )
                    } else {
                        container.userRepository.update(
                            existing.copy(
                                name = name.trim(),
                                sex = if (male) Sex.Male else Sex.Female,
                                age = ageInt,
                                heightCm = heightInt,
                                goalWeightKg = goalDouble,
                            ),
                        )
                    }
                    saved = true
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(
            if (userId == null) "新建用户" else "编辑用户",
            style = MaterialTheme.typography.headlineSmall,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("姓名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(Modifier.padding(top = 8.dp)) {
            FilterChip(selected = male, onClick = { male = true }, label = { Text("男") })
            FilterChip(
                selected = !male,
                onClick = { male = false },
                label = { Text("女") },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        OutlinedTextField(
            value = age,
            onValueChange = { age = it },
            label = { Text("年龄") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = height,
            onValueChange = { height = it },
            label = { Text("身高（cm）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = goal,
            onValueChange = { goal = it },
            label = { Text("目标体重（kg，可空）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (saved) {
            Text(
                "已保存",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        loaded?.huid?.let {
            Text("HUID：${maskHuid(it)}（已注册）", style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = { save() },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            enabled = !busy,
        ) {
            Text("保存")
        }
        if (userId != null && loaded != null) {
            Button(
                onClick = { loaded?.let { vm.startRegistration(it) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = !busy,
            ) {
                Text(if (loaded?.huid != null) "重新注册到秤（生成新 HUID）" else "注册到秤（Mode 1）")
            }
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            enabled = !busy,
        ) {
            Text("返回")
        }
    }

    SessionProgressDialog(state = sessionState, onCancel = { vm.cancelSession() }, onDismiss = {})
}
