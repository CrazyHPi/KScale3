package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.crazyh.kscale3.domain.Measurement
import xyz.crazyh.kscale3.domain.PairedDevice
import xyz.crazyh.kscale3.domain.Sex
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.ui.components.EmptyState
import xyz.crazyh.kscale3.ui.components.InitialAvatar
import xyz.crazyh.kscale3.ui.components.KCard
import xyz.crazyh.kscale3.ui.components.StatusPill
import xyz.crazyh.kscale3.ui.components.TrendChart
import xyz.crazyh.kscale3.ui.components.rounded
import xyz.crazyh.kscale3.ui.components.toTrendPoints
import xyz.crazyh.kscale3.ui.i18n.LocalAppLanguage
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.i18n.formatDateShort
import xyz.crazyh.kscale3.ui.i18n.formatMonthDay
import xyz.crazyh.kscale3.ui.i18n.formatTime
import xyz.crazyh.kscale3.ui.preview.previewDevice
import xyz.crazyh.kscale3.ui.preview.previewMeasurements
import xyz.crazyh.kscale3.ui.preview.previewUsers
import xyz.crazyh.kscale3.ui.theme.KScaleTheme
import xyz.crazyh.kscale3.ui.theme.LocalKScaleExtraColors
import kotlin.math.abs

private enum class TrendMetric { Weight, Fat }

private enum class TrendRange(val days: Int?) {
    Days30(30),
    Days90(90),
    All(null),
}

private const val DAY_MS = 86_400_000L

/**
 * 档案 · 历史（设计稿 01）：成员筛选 → 最新测量 hero → 趋势卡 → 记录列表。
 * 没有"全部成员"视角；默认第一位成员。
 */
@Composable
internal fun HistoryTab(
    users: List<UserProfile>,
    selectedUser: UserProfile?,
    measurements: List<Measurement>,
    device: PairedDevice?,
    onSelectUser: (Long) -> Unit,
    onOpenMeasurement: (Long) -> Unit,
    onGoWeigh: () -> Unit,
    onAddMember: () -> Unit,
    onGoDevice: () -> Unit,
) {
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current

    if (users.isEmpty() || selectedUser == null) {
        EmptyState(
            title = strings.historyNoMembers,
            body = strings.membersEmpty,
            actionLabel = strings.historyAddMemberAction,
            onAction = onAddMember,
            icon = Icons.Outlined.MonitorWeight,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    var metricIndex by rememberSaveable { mutableIntStateOf(0) }
    var rangeIndex by rememberSaveable { mutableIntStateOf(0) }
    var showAllRecords by rememberSaveable { mutableStateOf(false) }

    val metric = TrendMetric.entries[metricIndex.coerceIn(0, TrendMetric.entries.lastIndex)]
    val range = TrendRange.entries[rangeIndex.coerceIn(0, TrendRange.entries.lastIndex)]
    val latest = measurements.firstOrNull()
    val previous = measurements.getOrNull(1)

    val anchorMs = latest?.measuredAt?.toEpochMilliseconds()
    val ranged = if (range.days == null || anchorMs == null) {
        measurements
    } else {
        val start = anchorMs - range.days * DAY_MS
        measurements.filter { it.measuredAt.toEpochMilliseconds() >= start }
    }

    val chartPoints = ranged.toTrendPoints {
        if (metric == TrendMetric.Weight) it.weightKg else it.fatPct
    }
    val delta = if (chartPoints.size >= 2) {
        chartPoints.last().value - chartPoints.first().value
    } else null
    val unit = if (metric == TrendMetric.Weight) "kg" else "%"
    val decimals = if (metric == TrendMetric.Weight) 1 else 1

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        if (device == null) {
            item(key = "device-banner") {
                DeviceMissingBanner(
                    text = strings.profileNoDeviceBanner,
                    action = strings.profileGoPair,
                    onAction = onGoDevice,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        item(key = "members") {
            LazyRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(users, key = { it.id }) { user ->
                    MemberFilterChip(
                        user = user,
                        selected = user.id == selectedUser.id,
                        onClick = { onSelectUser(user.id) },
                    )
                }
            }
        }

        if (latest == null) {
            item(key = "empty") {
                EmptyState(
                    title = strings.historyEmpty,
                    body = strings.historyEmptyBody,
                    actionLabel = strings.historyEmptyAction,
                    onAction = onGoWeigh,
                )
            }
        } else {
            item(key = "hero") {
                HistoryHero(
                    latest = latest,
                    previous = previous,
                    user = selectedUser,
                )
            }

            item(key = "trend") {
                TrendCard(
                    title = if (metric == TrendMetric.Weight) strings.historyWeightTrend
                    else strings.historyFatTrend,
                    metric = metric,
                    onMetricChange = { metricIndex = it },
                    rangeLabel = rangeLabel(range, strings),
                    onRangeChange = { rangeIndex = it },
                    chartPoints = chartPoints,
                    unit = unit,
                    decimals = decimals,
                    delta = delta,
                )
            }

            item(key = "records-header") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        strings.historyRecords,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (!showAllRecords && measurements.size > 3) {
                        TextButton(onClick = { showAllRecords = true }) {
                            Text(
                                "${strings.historyAllCount(measurements.size)}  →",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            val visible = if (showAllRecords) measurements else measurements.take(3)
            itemsIndexed(visible, key = { _, m -> m.id }) { index, m ->
                MeasurementRow(
                    measurement = m,
                    language = language,
                    onClick = { onOpenMeasurement(m.id) },
                )
                if (index != visible.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

private fun rangeLabel(range: TrendRange, strings: xyz.crazyh.kscale3.ui.i18n.AppStrings): String =
    when (range) {
        TrendRange.Days30 -> strings.historyRange30
        TrendRange.Days90 -> strings.historyRange90
        TrendRange.All -> strings.historyRangeAll
    }

@Composable
private fun DeviceMissingBanner(text: String, action: String, onAction: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun MemberFilterChip(
    user: UserProfile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (selected) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
    ) {
        Row(
            Modifier.padding(start = 5.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(start = 2.dp).size(14.dp),
                )
                Spacer(Modifier.width(2.dp))
            }
            InitialAvatar(user.name, size = 22.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                user.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HistoryHero(
    latest: Measurement,
    previous: Measurement?,
    user: UserProfile,
) {
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current
    val extra = LocalKScaleExtraColors.current
    val (fatMin, fatMax) = healthyFatRange(user.sex)
    val fatHigh = latest.fatPct > fatMax
    val hrNormal = latest.heartRate in 60..100
    val delta = previous?.let { latest.weightKg - it.weightKg }

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                strings.historyLatest,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${formatMonthDay(latest.measuredAt, language)} · ${formatTime(latest.measuredAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                latest.weightKg.rounded(1),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "kg",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            if (delta != null) {
                DeltaPill(delta = delta)
                Spacer(Modifier.width(6.dp))
                Text(
                    strings.historyComparedLast,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }

        HorizontalDivider(
            Modifier.padding(top = 12.dp, bottom = 14.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )

        Row {
            HeroMetric(
                modifier = Modifier.weight(1f),
                label = strings.metricFatPct,
                value = "${latest.fatPct.rounded(1)}%",
                status = if (fatHigh) strings.statusHigh else strings.statusNormal,
                statusColor = if (fatHigh) extra.amber else extra.success,
                statusContainer = if (fatHigh) extra.amberContainer else extra.successContainer,
                sublabel = strings.historyHealthyRange(fatMin, fatMax),
                icon = Icons.Filled.WaterDrop,
                iconTint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(16.dp))
            HeroMetric(
                modifier = Modifier.weight(1f),
                label = strings.metricHeartRate,
                value = "${latest.heartRate} bpm",
                status = if (hrNormal) strings.statusNormal else strings.statusHigh,
                statusColor = if (hrNormal) extra.success else extra.amber,
                statusContainer = if (hrNormal) extra.successContainer else extra.amberContainer,
                sublabel = strings.historyRestingHeartRate,
                icon = Icons.Filled.Favorite,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroMetric(
    label: String,
    value: String,
    status: String,
    statusColor: Color,
    statusContainer: Color,
    sublabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                StatusPill(status, statusColor, statusContainer)
            }
            Text(
                sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeltaPill(delta: Double) {
    val extra = LocalKScaleExtraColors.current
    val down = delta < 0
    val color = if (down) extra.success else extra.amber
    val container = if (down) extra.successContainer else extra.amberContainer
    val arrow = if (delta == 0.0) "" else if (down) "↓ " else "↑ "
    StatusPill(
        text = "$arrow${abs(delta).rounded(1)} kg",
        contentColor = color,
        containerColor = container,
    )
}

@Composable
private fun TrendCard(
    title: String,
    metric: TrendMetric,
    onMetricChange: (Int) -> Unit,
    rangeLabel: String,
    onRangeChange: (Int) -> Unit,
    chartPoints: List<xyz.crazyh.kscale3.ui.components.TrendPoint>,
    unit: String,
    decimals: Int,
    delta: Double?,
) {
    val strings = LocalStrings.current
    var rangeMenu by remember { mutableStateOf(false) }
    val extra = LocalKScaleExtraColors.current

    KCard(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                MetricToggle(
                    metric = metric,
                    onSelect = onMetricChange,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Row(
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { rangeMenu = true }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            rangeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = rangeMenu,
                        onDismissRequest = { rangeMenu = false },
                    ) {
                        TrendRange.entries.forEachIndexed { index, r ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        rangeLabel(
                                            r,
                                            LocalStrings.current
                                        )
                                    )
                                },
                                onClick = {
                                    rangeMenu = false
                                    onRangeChange(index)
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (delta != null) {
                    val down = delta < 0
                    val arrow = if (delta == 0.0) "" else if (down) "↓ " else "↑ "
                    Text(
                        "$arrow${abs(delta).rounded(decimals)} $unit",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (down) extra.success else extra.amber,
                    )
                }
            }

            if (chartPoints.isEmpty()) {
                Text(
                    strings.historyEmpty,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                TrendChart(
                    points = chartPoints,
                    unit = unit,
                    decimals = decimals,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MetricToggle(metric: TrendMetric, onSelect: (Int) -> Unit) {
    val strings = LocalStrings.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
//        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(3.dp)) {
            TrendMetric.entries.forEachIndexed { index, m ->
                val selected = m == metric
                Surface(
                    onClick = { onSelect(index) },
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else Color.Transparent,
                    contentColor = if (selected)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        if (m == TrendMetric.Weight) strings.historyMetricWeight
                        else strings.historyMetricFat,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MeasurementRow(
    measurement: Measurement,
    language: xyz.crazyh.kscale3.ui.i18n.AppLanguage,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                formatDateShort(measurement.measuredAt, language),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                formatTime(measurement.measuredAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${measurement.weightKg.rounded(1)} kg",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${strings.metricFatPct} ${measurement.fatPct.rounded(1)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 8.dp).size(18.dp),
        )
    }
}

/** 体脂健康区间：男性 10–20%，女性 20–30%（设计稿按男性示例标注）。 */
internal fun healthyFatRange(sex: Sex): Pair<Int, Int> =
    if (sex == Sex.Male) 10 to 20 else 20 to 30

@Preview
@Composable
private fun HistoryTabPreview() {
    KScaleTheme {
        HistoryTab(
            users = previewUsers,
            selectedUser = previewUsers.first(),
            measurements = previewMeasurements,
            device = previewDevice,
            onSelectUser = {},
            onOpenMeasurement = {},
            onGoWeigh = {},
            onAddMember = {},
            onGoDevice = {},
        )
    }
}
