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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xyz.crazyh.kscale3.di.AppContainer
import xyz.crazyh.kscale3.domain.Sex
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.domain.UserProfileDraft
import xyz.crazyh.kscale3.session.SessionState
import xyz.crazyh.kscale3.ui.components.SessionProgressDialog
import xyz.crazyh.kscale3.ui.components.rounded
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.maskHuid
import xyz.crazyh.kscale3.ui.preview.previewUsers
import xyz.crazyh.kscale3.ui.vm.SessionViewModel

/** 表单校验错误（存原因而非已渲染文案，切换语言后可重新映射）。 */
private enum class EditError { NameRequired, AgeRange, HeightRange, GoalFormat }

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
    val sessionState by vm.sessionState.collectAsState()
    val busy by vm.busy.collectAsState()

    LaunchedEffect(userId) {
        if (userId != null) {
            container.userRepository.byId(userId).first()?.let { loaded = it }
        }
    }

    UserEditContent(
        isNew = userId == null,
        loaded = loaded,
        busy = busy,
        sessionState = sessionState,
        onSave = { draft ->
            scope.launch {
                val existing = loaded
                if (existing == null) {
                    container.userRepository.insert(draft)
                } else {
                    container.userRepository.update(
                        existing.copy(
                            name = draft.name,
                            sex = draft.sex,
                            age = draft.age,
                            heightCm = draft.heightCm,
                            goalWeightKg = draft.goalWeightKg,
                        ),
                    )
                }
                onBack()
            }
        },
        onRegister = { loaded?.let { vm.startRegistration(it) } },
        onBack = onBack,
        onCancelSession = vm::cancelSession,
    )
}

/** 无 ViewModel 的用户编辑 UI（供 @Preview）。 */
@Composable
private fun UserEditContent(
    isNew: Boolean,
    loaded: UserProfile?,
    busy: Boolean,
    sessionState: SessionState,
    onSave: (UserProfileDraft) -> Unit,
    onRegister: () -> Unit,
    onBack: () -> Unit,
    onCancelSession: () -> Unit,
) {
    var name by remember(loaded) { mutableStateOf(loaded?.name ?: "") }
    var male by remember(loaded) { mutableStateOf(loaded?.sex != Sex.Female) }
    var age by remember(loaded) { mutableStateOf(loaded?.age?.toString() ?: "30") }
    var height by remember(loaded) { mutableStateOf(loaded?.heightCm?.toString() ?: "170") }
    var goal by remember(loaded) { mutableStateOf(loaded?.goalWeightKg?.rounded(1) ?: "") }
    var error by remember { mutableStateOf<EditError?>(null) }
    val strings = LocalStrings.current

    fun save() {
        val ageInt = age.toIntOrNull()
        val heightInt = height.toIntOrNull()
        val goalDouble = goal.trim().toDoubleOrNull()
        when {
            name.isBlank() -> error = EditError.NameRequired
            ageInt == null || ageInt !in 10..120 -> error = EditError.AgeRange
            heightInt == null || heightInt !in 80..230 -> error = EditError.HeightRange
            goal.isNotBlank() && goalDouble == null -> error = EditError.GoalFormat
            else -> {
                error = null
                onSave(
                    UserProfileDraft(
                        name = name.trim(),
                        sex = if (male) Sex.Male else Sex.Female,
                        age = ageInt,
                        heightCm = heightInt,
                        goalWeightKg = goalDouble,
                    ),
                )
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(
            if (isNew) strings.editNewTitle else strings.editTitle,
            style = MaterialTheme.typography.headlineSmall,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(strings.editName) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(Modifier.padding(top = 8.dp)) {
            FilterChip(
                selected = male,
                onClick = { male = true },
                label = { Text(strings.editMale) },
            )
            FilterChip(
                selected = !male,
                onClick = { male = false },
                label = { Text(strings.editFemale) },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        OutlinedTextField(
            value = age,
            onValueChange = { age = it },
            label = { Text(strings.editAge) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = height,
            onValueChange = { height = it },
            label = { Text(strings.editHeight) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = goal,
            onValueChange = { goal = it },
            label = { Text(strings.editGoal) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        error?.let { e ->
            Text(
                when (e) {
                    EditError.NameRequired -> strings.editErrorNameRequired
                    EditError.AgeRange -> strings.editErrorAgeRange
                    EditError.HeightRange -> strings.editErrorHeightRange
                    EditError.GoalFormat -> strings.editErrorGoalFormat
                },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        loaded?.huid?.let {
            Text(strings.editHuidLine(maskHuid(it)), style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = { save() },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            enabled = !busy,
        ) {
            Text(strings.commonSave)
        }
        if (!isNew && loaded != null) {
            Button(
                onClick = onRegister,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = !busy,
            ) {
                Text(
                    if (loaded.huid != null) strings.editReRegisterNewHuid
                    else strings.editRegisterMode1
                )
            }
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            enabled = !busy,
        ) {
            Text(strings.commonBack)
        }
    }

    SessionProgressDialog(state = sessionState, onCancel = onCancelSession, onDismiss = {})
}

@Preview
@Composable
private fun UserEditScreenPreview() {
    MaterialTheme {
        UserEditContent(
            isNew = false,
            loaded = previewUsers.first(),
            busy = false,
            sessionState = SessionState.Idle,
            onSave = {},
            onRegister = {},
            onBack = {},
            onCancelSession = {},
        )
    }
}

@Preview
@Composable
private fun UserEditScreenNewPreview() {
    MaterialTheme {
        UserEditContent(
            isNew = true,
            loaded = null,
            busy = false,
            sessionState = SessionState.Idle,
            onSave = {},
            onRegister = {},
            onBack = {},
            onCancelSession = {},
        )
    }
}
