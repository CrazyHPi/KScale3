package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import xyz.crazyh.kscale3.di.AppContainer
import xyz.crazyh.kscale3.domain.Measurement
import xyz.crazyh.kscale3.domain.PairedDevice
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.session.SessionState
import xyz.crazyh.kscale3.ui.ProfileTab
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.preview.previewDevice
import xyz.crazyh.kscale3.ui.preview.previewMeasurements
import xyz.crazyh.kscale3.ui.preview.previewUsers
import xyz.crazyh.kscale3.ui.theme.KScaleTheme
import xyz.crazyh.kscale3.ui.vm.ProfileViewModel
import xyz.crazyh.kscale3.ui.vm.SessionViewModel

/**
 * 档案页（设计稿 01/03）：顶部「档案」+ 添加成员，二级 Tab 历史与成员。
 * 历史与成员的筛选/滚动状态由各自 Composable 保存；从详情返回不丢。
 */
@Composable
fun ProfileScreen(
    container: AppContainer,
    sessionVm: SessionViewModel,
    device: PairedDevice?,
    selectedTab: ProfileTab,
    onSelectTab: (ProfileTab) -> Unit,
    onAddMember: () -> Unit,
    onEditMember: (Long) -> Unit,
    onOpenMeasurement: (Long) -> Unit,
    onGoWeigh: () -> Unit,
    onGoDevice: () -> Unit,
) {
    val vm: ProfileViewModel = viewModel { ProfileViewModel(container) }
    val users by vm.users.collectAsState()
    val selectedUser by vm.selectedUser.collectAsState()
    val measurements by vm.measurements.collectAsState()
    val latestByUser by vm.latestByUser.collectAsState()

    val sessionState by sessionVm.sessionState.collectAsState()
    val busy by sessionVm.busyOrUnit.collectAsState()
    val scope = rememberCoroutineScope()

    ProfileContent(
        users = users,
        selectedUser = selectedUser,
        measurements = measurements,
        latestByUser = latestByUser,
        device = device,
        sessionState = sessionState,
        busy = busy,
        selectedTab = selectedTab,
        onSelectTab = onSelectTab,
        onSelectUser = vm::selectUser,
        onAddMember = onAddMember,
        onEditMember = onEditMember,
        onDeleteMember = { user -> scope.launch { sessionVm.deleteUser(user) } },
        onRegister = sessionVm::startRegistration,
        onCancelSession = sessionVm::cancelSession,
        onOpenMeasurement = onOpenMeasurement,
        onGoWeigh = onGoWeigh,
        onGoDevice = onGoDevice,
    )
}

/** 无 ViewModel 的档案页 UI（供 @Preview 与 [ProfileScreen] 复用）。 */
@Composable
internal fun ProfileContent(
    users: List<UserProfile>,
    selectedUser: UserProfile?,
    measurements: List<Measurement>,
    latestByUser: Map<Long, Measurement>,
    device: PairedDevice?,
    sessionState: SessionState,
    busy: Boolean,
    selectedTab: ProfileTab,
    onSelectTab: (ProfileTab) -> Unit,
    onSelectUser: (Long) -> Unit,
    onAddMember: () -> Unit,
    onEditMember: (Long) -> Unit,
    onDeleteMember: (UserProfile) -> Unit,
    onRegister: (UserProfile) -> Unit,
    onCancelSession: () -> Unit,
    onOpenMeasurement: (Long) -> Unit,
    onGoWeigh: () -> Unit,
    onGoDevice: () -> Unit,
) {
    val strings = LocalStrings.current

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                strings.tabProfile,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAddMember) {
                Icon(
                    Icons.Filled.PersonAdd,
                    contentDescription = strings.profileAddMember,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ProfileTabRow(selected = selectedTab, onSelect = onSelectTab)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

        when (selectedTab) {
            ProfileTab.History -> HistoryTab(
                users = users,
                selectedUser = selectedUser,
                measurements = measurements,
                device = device,
                onSelectUser = onSelectUser,
                onOpenMeasurement = onOpenMeasurement,
                onGoWeigh = onGoWeigh,
                onAddMember = onAddMember,
                onGoDevice = onGoDevice,
            )

            ProfileTab.Members -> MembersTab(
                users = users,
                latestByUser = latestByUser,
                device = device,
                sessionState = sessionState,
                busy = busy,
                onAddMember = onAddMember,
                onEditMember = onEditMember,
                onDeleteMember = onDeleteMember,
                onRegister = onRegister,
                onCancelSession = onCancelSession,
            )
        }
    }
}

/** 二级 Tab：文字 + 72dp 胶囊指示条。 */
@Composable
private fun ProfileTabRow(
    selected: ProfileTab,
    onSelect: (ProfileTab) -> Unit,
) {
    val strings = LocalStrings.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        ProfileTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) }
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = when (tab) {
                        ProfileTab.History -> strings.tabHistory
                        ProfileTab.Members -> strings.tabMembers
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                Box(
                    Modifier
                        .width(72.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                )
            }
        }
    }
}

@Preview
@Composable
private fun ProfileScreenHistoryPreview() {
    KScaleTheme {
        ProfileContent(
            users = previewUsers,
            selectedUser = previewUsers.first(),
            measurements = previewMeasurements,
            latestByUser = mapOf(previewUsers.first().id to previewMeasurements.first()),
            device = previewDevice,
            sessionState = SessionState.Idle,
            busy = false,
            selectedTab = ProfileTab.History,
            onSelectTab = {},
            onSelectUser = {},
            onAddMember = {},
            onEditMember = {},
            onDeleteMember = {},
            onRegister = {},
            onCancelSession = {},
            onOpenMeasurement = {},
            onGoWeigh = {},
            onGoDevice = {},
        )
    }
}

@Preview
@Composable
private fun ProfileScreenMembersPreview() {
    KScaleTheme {
        ProfileContent(
            users = previewUsers,
            selectedUser = previewUsers.first(),
            measurements = previewMeasurements,
            latestByUser = mapOf(previewUsers.first().id to previewMeasurements.first()),
            device = previewDevice,
            sessionState = SessionState.Idle,
            busy = false,
            selectedTab = ProfileTab.Members,
            onSelectTab = {},
            onSelectUser = {},
            onAddMember = {},
            onEditMember = {},
            onDeleteMember = {},
            onRegister = {},
            onCancelSession = {},
            onOpenMeasurement = {},
            onGoWeigh = {},
            onGoDevice = {},
        )
    }
}
