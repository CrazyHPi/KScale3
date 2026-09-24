package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import xyz.crazyh.kscale3.di.AppContainer
import xyz.crazyh.kscale3.ui.components.MetricRow
import xyz.crazyh.kscale3.ui.components.SectionTitle
import xyz.crazyh.kscale3.ui.components.WeightTrendChart
import xyz.crazyh.kscale3.ui.components.rounded
import xyz.crazyh.kscale3.ui.components.toTrendPoints
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("历史与趋势", style = MaterialTheme.typography.headlineSmall)

        // 用户筛选（单行 chips）
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        ) {
            FilterChip(
                selected = selectedId == null,
                onClick = { vm.selectUser(null) },
                label = { Text("全部") },
            )
            users.forEach { user ->
                FilterChip(
                    selected = selectedId == user.id,
                    onClick = { vm.selectUser(user.id) },
                    label = { Text(user.name) },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        SectionTitle("体重趋势（kg）")
        if (measurements.isEmpty()) {
            Text("暂无测量数据。", style = MaterialTheme.typography.bodySmall)
        } else {
            WeightTrendChart(points = measurements.toTrendPoints())
        }

        SectionTitle("测量列表")
        LazyColumn(Modifier.fillMaxSize()) {
            items(measurements, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(formatInstant(m.measuredAt), fontWeight = FontWeight.Bold)
                        MetricRow("体重", "${m.weightKg.rounded(2)} kg")
                        MetricRow("体脂率", "${m.fatPct.rounded(1)} %")
                        MetricRow("心率", "${m.heartRate} bpm")
                    }
                }
            }
        }
    }
}

private fun formatInstant(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    fun p2(v: Int) = v.toString().padStart(2, '0')
    return "${dt.year}-${p2(dt.monthNumber)}-${p2(dt.dayOfMonth)} ${p2(dt.hour)}:${p2(dt.minute)}"
}
