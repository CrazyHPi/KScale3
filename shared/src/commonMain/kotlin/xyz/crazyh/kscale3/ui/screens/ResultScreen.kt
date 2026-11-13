package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.crazyh.kscale3.domain.Measurement
import xyz.crazyh.kscale3.domain.Sex
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.ui.components.ConfirmDialog
import xyz.crazyh.kscale3.ui.components.EstimateGrid
import xyz.crazyh.kscale3.ui.components.FatHealthBand
import xyz.crazyh.kscale3.ui.components.HeartRateSparkline
import xyz.crazyh.kscale3.ui.components.KCard
import xyz.crazyh.kscale3.ui.components.ScaleGlyph
import xyz.crazyh.kscale3.ui.components.StatusPill
import xyz.crazyh.kscale3.ui.components.rounded
import xyz.crazyh.kscale3.ui.i18n.LocalAppLanguage
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.i18n.formatDeviceTs
import xyz.crazyh.kscale3.ui.preview.previewMeasurements
import xyz.crazyh.kscale3.ui.preview.previewResult
import xyz.crazyh.kscale3.ui.preview.previewUsers
import xyz.crazyh.kscale3.ui.theme.KScaleTheme
import xyz.crazyh.kscale3.ui.theme.LocalKScaleExtraColors
import xyz.crazyh.kscale3.ui.vm.PendingMeasurement
import xyz.crazyh.kscale3.ui.vm.SessionViewModel
import kotlin.math.abs

/**
 * 称重结果（设计稿 05）：隐藏底栏，真值 + 估算值 + 保存/放弃。
 * 保存失败保留本页并可重试；放弃需二次确认。
 */
@Composable
fun ResultScreen(
    vm: SessionViewModel,
    onDone: () -> Unit,
) {
    val pending by vm.pendingResult.collectAsState()
    val users by vm.users.collectAsState()
    val latestByUser by vm.latestByUser.collectAsState()
    val saving by vm.saving.collectAsState()
    val saveError by vm.saveError.collectAsState()

    val p = pending
    if (p == null) {
        LaunchedEffect(Unit) { onDone() }
        Box(Modifier.fillMaxSize())
        return
    }

    val user = users.firstOrNull { it.id == p.userId }
    ResultContent(
        pending = p,
        user = user,
        previous = latestByUser[p.userId],
        saving = saving,
        saveError = saveError,
        onSave = vm::savePendingMeasurement,
        onDiscard = vm::discardPendingMeasurement,
    )
}

@Composable
private fun ResultContent(
    pending: PendingMeasurement,
    user: UserProfile?,
    previous: Measurement?,
    saving: Boolean,
    saveError: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current
    val extra = LocalKScaleExtraColors.current
    var discardConfirm by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val r = pending.result
    val userName = user?.name.orEmpty()
    val (fatMin, fatMax) = healthyFatRange(user?.sex ?: Sex.Male)
    val fatHigh = r.fatPct > fatMax
    val fatColor = if (fatHigh) extra.amber else extra.success
    val delta = previous?.let { r.weightKg - it.weightKg }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { discardConfirm = true }) {
                Icon(Icons.Filled.Close, contentDescription = strings.commonClose)
            }
            Text(
                strings.resultTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = strings.detailMore)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                strings.resultDiscard,
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            discardConfirm = true
                        },
                    )
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Hero
            KCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = extra.heroSurface,
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = extra.onHeroSurface,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                strings.resultCompleted,
                                style = MaterialTheme.typography.labelLarge,
                                color = extra.onHeroSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatDeviceTs(r.deviceTs, language),
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.onHeroSurfaceVariant,
                            )
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                r.weightKg.rounded(1),
                                style = MaterialTheme.typography.displayLarge,
                                color = extra.onHeroSurface,
                            )
                            Text(
                                "kg",
                                style = MaterialTheme.typography.titleLarge,
                                color = extra.onHeroSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (delta != null) {
                                StatusPill(
                                    text = "${arrow(delta)} ${abs(delta).rounded(1)} kg ${
                                        strings.historyComparedLast
                                    }",
                                    contentColor = extra.onHeroSurface,
                                    containerColor = extra.onHeroSurface.copy(alpha = 0.09f),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                        if (userName.isNotEmpty()) {
                            Row(
                                Modifier.padding(top = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = extra.onHeroSurfaceVariant,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    strings.resultSavedTo(userName),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = extra.onHeroSurfaceVariant,
                                )
                            }
                        }
                    }
                    ScaleGlyph(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 20.dp)
                            .size(76.dp),
                        tint = extra.onHeroSurface.copy(alpha = 0.16f),
                    )
                }
            }

            // 真值卡
            Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                KCard(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(
                            strings.metricFatPct,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                r.fatPct.rounded(1),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(
                                "%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 3.dp, bottom = 4.dp),
                            )
                        }
                        FatHealthBand(
                            fatPct = r.fatPct,
                            healthyMin = fatMin,
                            healthyMax = fatMax,
                            markerColor = fatColor,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            strings.historyHealthyRange(fatMin, fatMax),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                KCard(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                strings.metricHeartRate,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${r.heartRate}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(
                                "bpm",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 3.dp, bottom = 4.dp),
                            )
                        }
                        HeartRateSparkline(Modifier.padding(top = 6.dp))
                        Text(
                            strings.historyRestingHeartRate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 身体成分
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.resultBodyComposition, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                StatusPill(
                    text = strings.estimateBadge,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    strings.resultReferenceOnly,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            EstimateGrid(estimates = r.estimates)
            Spacer(Modifier.height(12.dp))
        }

        // 底部固定操作区：结果保存前始终可见，避免长内容把按钮推走。
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            if (userName.isNotEmpty()) {
                Text(
                    strings.resultSavedTo(userName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                )
            }

            if (saveError) {
                Text(
                    strings.resultSaveFailed,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                )
            }

            Button(
                onClick = onSave,
                enabled = !saving,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.resultSaving)
                } else {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.resultSaveDone, style = MaterialTheme.typography.titleMedium)
                }
            }

            TextButton(
                onClick = { discardConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    strings.resultDiscard,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (discardConfirm) {
        ConfirmDialog(
            title = strings.resultDiscardTitle,
            message = strings.resultDiscardMessage,
            confirmLabel = strings.resultDiscard,
            onConfirm = {
                discardConfirm = false
                onDiscard()
            },
            onDismiss = { discardConfirm = false },
        )
    }
}

private fun arrow(delta: Double): String = when {
    delta < 0 -> "↓"
    delta > 0 -> "↑"
    else -> ""
}

@Preview
@Composable
private fun ResultScreenPreview() {
    KScaleTheme {
        ResultContent(
            pending = PendingMeasurement(userId = 1, result = previewResult),
            user = previewUsers.first(),
            previous = previewMeasurements.getOrNull(1),
            saving = false,
            saveError = false,
            onSave = {},
            onDiscard = {},
        )
    }
}
