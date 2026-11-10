package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import xyz.crazyh.kscale3.di.AppContainer
import xyz.crazyh.kscale3.domain.Measurement
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.domain.toEstimates
import xyz.crazyh.kscale3.ui.components.MeasurementDetail
import xyz.crazyh.kscale3.ui.components.MetricRow
import xyz.crazyh.kscale3.ui.components.SectionTitle
import xyz.crazyh.kscale3.ui.components.WeightTrendChart
import xyz.crazyh.kscale3.ui.components.rounded
import xyz.crazyh.kscale3.ui.components.toTrendPoints
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.preview.previewMeasurements
import xyz.crazyh.kscale3.ui.preview.previewUsers
import xyz.crazyh.kscale3.ui.vm.HistoryViewModel

/**
 * 历史屏（PLAN UI-5）：按用户筛选、测量列表、体重趋势折线图
 * （图表规范来自 dataviz skill：单序列免图例、端值直标、点按 tooltip）。
 */
@Composable
fun HistoryScreen(
    container: AppContainer,
) {
    val vm: HistoryViewModel = viewModel { HistoryViewModel(container) }
    val users by vm.users.collectAsState()
    val selectedId by vm.selectedUserId.collectAsState()
    val measurements by vm.measurements.collectAsState()

    HistoryContent(
        users = users,
        selectedUserId = selectedId,
        measurements = measurements,
        onSelectUser = vm::selectUser,
    )
}

/** 无 ViewModel 的历史 UI（供 @Preview）。 */
@Composable
private fun HistoryContent(
    users: List<UserProfile>,
    selectedUserId: Long?,
    measurements: List<Measurement>,
    onSelectUser: (Long?) -> Unit,
) {
    var detail by remember { mutableStateOf<Measurement?>(null) }
    val strings = LocalStrings.current

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(strings.historyTitle, style = MaterialTheme.typography.headlineSmall)

        // 用户筛选（单行 chips）
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        ) {
            FilterChip(
                selected = selectedUserId == null,
                onClick = { onSelectUser(null) },
                label = { Text(strings.historyAll) },
            )
            users.forEach { user ->
                FilterChip(
                    selected = selectedUserId == user.id,
                    onClick = { onSelectUser(user.id) },
                    label = { Text(user.name) },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        SectionTitle(strings.historyWeightTrend)
        if (measurements.isEmpty()) {
            Text(
                strings.historyEmpty,
                fontSize = 16.sp,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp).align(Alignment.CenterHorizontally)
            )
        } else {
            WeightTrendChart(points = measurements.toTrendPoints())

            SectionTitle(strings.historyList)
            Text(
                strings.historyTapHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(measurements, key = { it.id }) { m ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { detail = m },
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(formatInstant(m.measuredAt), fontWeight = FontWeight.Bold)
                            MetricRow(strings.metricWeight, "${m.weightKg.rounded(2)} kg")
                            MetricRow(strings.metricFatPct, "${m.fatPct.rounded(1)} %")
                            MetricRow(strings.metricHeartRate, "${m.heartRate} bpm")
                        }
                    }
                }
            }
        }
    }

    // 历史卡片详情弹窗：与称重结果屏共用真值/估算值展示
    detail?.let { m ->
        MeasurementDetailDialog(measurement = m, onDismiss = { detail = null })
    }
}

/** 历史记录详情：真值 + 估算值（与 [ResultScreen] 同一套内容）。 */
@Composable
private fun MeasurementDetailDialog(
    measurement: Measurement,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(formatInstant(measurement.measuredAt)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    strings.deviceTime(measurement.deviceTs),
                    style = MaterialTheme.typography.bodySmall,
                )
                MeasurementDetail(
                    weightKg = measurement.weightKg,
                    fatPct = measurement.fatPct,
                    heartRate = measurement.heartRate,
                    estimates = measurement.toEstimates(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.commonClose) }
        },
    )
}

@Preview
@Composable
private fun HistoryScreenPreview() {
    MaterialTheme {
        HistoryContent(
            users = previewUsers,
            selectedUserId = null,
            measurements = previewMeasurements,
            onSelectUser = {},
        )
    }
}

private fun formatInstant(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    fun p2(v: Int) = v.toString().padStart(2, '0')
    return "${dt.year}-${p2(dt.monthNumber)}-${p2(dt.dayOfMonth)} ${p2(dt.hour)}:${p2(dt.minute)}"
}
