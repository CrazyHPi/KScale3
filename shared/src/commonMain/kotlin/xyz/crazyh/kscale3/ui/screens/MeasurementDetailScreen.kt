package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xyz.crazyh.kscale3.di.AppContainer
import xyz.crazyh.kscale3.domain.Measurement
import xyz.crazyh.kscale3.domain.Sex
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.domain.toEstimates
import xyz.crazyh.kscale3.ui.components.ConfirmDialog
import xyz.crazyh.kscale3.ui.components.EstimateGrid
import xyz.crazyh.kscale3.ui.components.FatRing
import xyz.crazyh.kscale3.ui.components.HeartRateSparkline
import xyz.crazyh.kscale3.ui.components.KCard
import xyz.crazyh.kscale3.ui.components.ScaleGlyph
import xyz.crazyh.kscale3.ui.components.StatusPill
import xyz.crazyh.kscale3.ui.components.rounded
import xyz.crazyh.kscale3.ui.i18n.LocalAppLanguage
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.i18n.formatDateLong
import xyz.crazyh.kscale3.ui.i18n.formatTime
import xyz.crazyh.kscale3.ui.theme.LocalKScaleExtraColors
import kotlin.math.abs

/**
 * 单次测量详情（设计稿 02）：整页替换原历史弹窗。
 * 真值 = 体重/体脂/心率；体成分为估算值，统一标注。
 */
@Composable
fun MeasurementDetailScreen(
    container: AppContainer,
    measurementId: Long,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current
    val extra = LocalKScaleExtraColors.current
    val scope = rememberCoroutineScope()

    val measurementFlow = remember(measurementId) {
        container.measurementRepository.byId(measurementId)
    }
    val measurementState by measurementFlow.collectAsState(initial = null)
    var deleteConfirm by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var rawExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(measurementFlow) {
        if (measurementFlow.first() == null) onBack()
    }

    val measurement = measurementState ?: run {
        Box(Modifier.fillMaxSize())
        return
    }

    val user by produceState<UserProfile?>(initialValue = null, key1 = measurement.userId) {
        value = container.userRepository.byId(measurement.userId).first()
    }
    val history by produceState<List<Measurement>>(
        initialValue = emptyList(),
        key1 = measurement.userId,
    ) {
        value = container.measurementRepository.forUser(measurement.userId)
            .first()
            .sortedByDescending { it.measuredAt }
    }
    val previous = history.firstOrNull { it.measuredAt < measurement.measuredAt }
    val (fatMin, fatMax) = healthyFatRange(user?.sex ?: Sex.Male)
    val fatHigh = measurement.fatPct > fatMax
    val fatStatus = if (fatHigh) strings.statusHigh else strings.statusNormal
    val fatColor = if (fatHigh) extra.amber else extra.success
    val delta = previous?.let { measurement.weightKg - it.weightKg }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = strings.commonBack,
                )
            }
            Text(
                strings.detailTitle,
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
                                strings.detailDelete,
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            deleteConfirm = true
                        },
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(
                    formatDateLong(measurement.measuredAt, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatTime(measurement.measuredAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Hero
            KCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = extra.heroSurface,
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                strings.detailThisWeight,
                                style = MaterialTheme.typography.labelMedium,
                                color = extra.onHeroSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (delta != null) {
                                StatusPill(
                                    text = strings.detailComparedLast(
                                        "${arrow(delta)} ${abs(delta).rounded(1)} kg"
                                    ),
                                    contentColor = extra.onHeroSurface,
                                    containerColor = extra.onHeroSurface.copy(alpha = 0.09f),
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                measurement.weightKg.rounded(1),
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
                        user?.goalWeightKg?.let { goal ->
                            val diff = measurement.weightKg - goal
                            Text(
                                when {
                                    diff > 0.05 -> strings.detailGoalLine(
                                        goalKg = "${goal.rounded(1)} kg",
                                        diffKg = "${diff.rounded(1)} kg",
                                    )

                                    diff < -0.05 -> strings.detailGoalExceededLine(
                                        goalKg = "${goal.rounded(1)} kg",
                                        extraKg = "${abs(diff).rounded(1)} kg",
                                    )

                                    else -> strings.detailGoalReached("${goal.rounded(1)} kg")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = extra.onHeroSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
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

            // 体脂 / 心率
            Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                KCard(Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            strings.metricFatPct,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FatRing(
                            fatPct = measurement.fatPct,
                            ringColor = fatColor,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                        )
                        Text(
                            strings.historyFatStatusLine(fatStatus, fatMin, fatMax),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (fatHigh) extra.amber
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                KCard(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                strings.metricHeartRate,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${measurement.heartRate}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(
                                "bpm",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                            )
                        }
                        HeartRateSparkline(Modifier.padding(top = 8.dp))
                        Text(
                            strings.historyRestingHeartRate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 身体成分（估算值）
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
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }

            EstimateGrid(estimates = measurement.toEstimates())

            // 原始数据
            KCard(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                onClick = { rawExpanded = !rawExpanded },
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                strings.detailRawData,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                strings.detailRawDataHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (rawExpanded) {
                        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            RawRow(strings.detailDeviceTime, measurement.deviceTs)
                            RawRow(
                                strings.detailFeetImp,
                                measurement.feetImp.joinToString(" · "),
                            )
                            RawRow(
                                strings.detailHandsImp,
                                measurement.handsImp.joinToString(" · "),
                            )
                            RawRow(
                                strings.metricHeartRate,
                                "${measurement.heartRate} bpm",
                            )
                        }
                    }
                }
            }

            Text(
                strings.detailFootnote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        }
    }

    if (deleteConfirm) {
        ConfirmDialog(
            title = strings.detailDeleteTitle,
            message = strings.detailDeleteMessage,
            confirmLabel = strings.commonDelete,
            onConfirm = {
                deleteConfirm = false
                scope.launch {
                    container.measurementRepository.delete(measurement.id)
                    onBack()
                }
            },
            onDismiss = { deleteConfirm = false },
        )
    }
}

private fun arrow(delta: Double): String = when {
    delta < 0 -> "↓"
    delta > 0 -> "↑"
    else -> ""
}

@Composable
private fun RawRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
