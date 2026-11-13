package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.crazyh.kscale3.domain.Measurement
import xyz.crazyh.kscale3.domain.PairedDevice
import xyz.crazyh.kscale3.domain.Sex
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.session.SessionState
import xyz.crazyh.kscale3.ui.components.AvatarBadge
import xyz.crazyh.kscale3.ui.components.ConfirmDialog
import xyz.crazyh.kscale3.ui.components.EmptyState
import xyz.crazyh.kscale3.ui.components.InfoBanner
import xyz.crazyh.kscale3.ui.components.InitialAvatar
import xyz.crazyh.kscale3.ui.components.KCard
import xyz.crazyh.kscale3.ui.components.SessionProgressDialog
import xyz.crazyh.kscale3.ui.components.StatusPill
import xyz.crazyh.kscale3.ui.components.rounded
import xyz.crazyh.kscale3.ui.i18n.LocalAppLanguage
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.i18n.errorText
import xyz.crazyh.kscale3.ui.i18n.formatMonthDay
import xyz.crazyh.kscale3.ui.i18n.sessionGuide
import xyz.crazyh.kscale3.ui.preview.previewDevice
import xyz.crazyh.kscale3.ui.preview.previewMeasurements
import xyz.crazyh.kscale3.ui.preview.previewUsers
import xyz.crazyh.kscale3.ui.theme.KScaleTheme
import xyz.crazyh.kscale3.ui.theme.LocalKScaleExtraColors

/**
 * 档案 · 成员（设计稿 03）：摘要、注册提示、成员卡、扩展 FAB。
 * 只有已注册到秤的成员能发起称重；删除会连带该成员的历史（二次确认）。
 */
@Composable
internal fun MembersTab(
    users: List<UserProfile>,
    latestByUser: Map<Long, Measurement>,
    device: PairedDevice?,
    sessionState: SessionState,
    busy: Boolean,
    onAddMember: () -> Unit,
    onEditMember: (Long) -> Unit,
    onDeleteMember: (UserProfile) -> Unit,
    onRegister: (UserProfile) -> Unit,
    onCancelSession: () -> Unit,
) {
    val strings = LocalStrings.current
    var pendingDelete by remember { mutableStateOf<UserProfile?>(null) }
    val registeredCount = users.count { it.huid != null }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (users.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = strings.historyNoMembers,
                        body = strings.membersEmpty,
                        actionLabel = strings.usersAdd,
                        onAction = onAddMember,
                    )
                }
            } else {
                item(key = "summary") {
                    Column(Modifier.padding(start = 8.dp, top = 8.dp)) {
                        Text(
                            strings.membersCount(users.size),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            strings.membersRegisteredCount(registeredCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item(key = "hint") {
                    InfoBanner(
                        text = strings.membersRegisterHint,
                        icon = Icons.Outlined.Info,
                    )
                }

                if (device == null) {
                    item(key = "no-device") {
                        InfoBanner(
                            text = strings.profileNoDeviceBanner,
                            icon = Icons.Outlined.Info,
                        )
                    }
                }

                when (val s = sessionState) {
                    is SessionState.Failed -> item(key = "session-failed") {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                strings.errorText(s.error),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }

                    is SessionState.Paired -> item(key = "session-paired") {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                strings.sessionGuide(s),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }

                    else -> Unit
                }

                items(users, key = { it.id }) { user ->
                    MemberCard(
                        user = user,
                        latest = latestByUser[user.id],
                        busy = busy,
                        canRegister = device != null,
                        onEdit = { onEditMember(user.id) },
                        onRegister = { onRegister(user) },
                        onDelete = { pendingDelete = user },
                    )
                }

                item(key = "footer") {
                    Text(
                        strings.membersRegisterFooter,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAddMember,
            icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
            text = { Text(strings.usersAdd) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        )
    }

    pendingDelete?.let { user ->
        ConfirmDialog(
            title = strings.membersDeleteTitle(user.name),
            message = strings.membersDeleteMessage,
            confirmLabel = strings.commonDelete,
            onConfirm = {
                pendingDelete = null
                onDeleteMember(user)
            },
            onDismiss = { pendingDelete = null },
        )
    }

    SessionProgressDialog(state = sessionState, onCancel = onCancelSession, onDismiss = {})
}

@Composable
private fun MemberCard(
    user: UserProfile,
    latest: Measurement?,
    busy: Boolean,
    canRegister: Boolean,
    onEdit: () -> Unit,
    onRegister: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current
    val extra = LocalKScaleExtraColors.current
    val registered = user.huid != null

    KCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                InitialAvatar(
                    name = user.name,
                    size = 52.dp,
                    badge = if (registered) AvatarBadge.Registered else AvatarBadge.Locked,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        user.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        strings.membersProfileLine(
                            male = user.sex == Sex.Male,
                            age = user.age,
                            heightCm = user.heightCm,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        strings.membersLastWeigh,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (latest != null) {
                        Text(
                            strings.membersLastWeighLine(
                                weight = "${latest.weightKg.rounded(1)} kg",
                                monthDay = formatMonthDay(latest.measuredAt, language),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Text(
                            strings.membersNoMeasurements,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!registered) {
                            Text(
                                strings.membersRegisterFirst,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    text = if (registered) strings.membersRegisteredOnScale
                    else strings.usersUnregistered,
                    contentColor = if (registered) extra.success else extra.amber,
                    containerColor = if (registered) extra.successContainer
                    else extra.amberContainer,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit, enabled = !busy) {
                    Text(strings.commonEdit)
                }
                if (!registered) {
                    TextButton(
                        onClick = onRegister,
                        enabled = canRegister && !busy,
                    ) {
                        Text(strings.usersRegister)
                    }
                }
                TextButton(onClick = onDelete, enabled = !busy) {
                    Text(
                        strings.commonDelete,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun MembersTabPreview() {
    KScaleTheme {
        MembersTab(
            users = previewUsers,
            latestByUser = mapOf(previewUsers.first().id to previewMeasurements.first()),
            device = previewDevice,
            sessionState = SessionState.Idle,
            busy = false,
            onAddMember = {},
            onEditMember = {},
            onDeleteMember = {},
            onRegister = {},
            onCancelSession = {},
        )
    }
}
