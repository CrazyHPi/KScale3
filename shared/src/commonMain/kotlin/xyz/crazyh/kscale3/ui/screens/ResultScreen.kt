package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.crazyh.kscale3.ui.components.MeasurementDetail
import xyz.crazyh.kscale3.ui.vm.SessionViewModel

/**
 * 测量结果屏（PLAN UI-4）：真值卡（体重/体脂/心率）
 * + 估算卡（肌肉量/水分/骨量等，全部标注"估算值"）+ 保存入库。
 */
@Composable
fun ResultScreen(
    vm: SessionViewModel,
    onDone: () -> Unit,
) {
    val pending by vm.pendingResult.collectAsState()
    val saved = pending == null

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("测量结果", style = MaterialTheme.typography.headlineSmall)

        val p = pending
        if (p == null) {
            Text(
                "已保存（或暂无待保存的测量）。",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 24.dp),
            )
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Text("返回称重")
            }
            return@Column
        }

        val r = p.result
        Text("设备时间：${r.deviceTs}", style = MaterialTheme.typography.bodySmall)

        MeasurementDetail(
            weightKg = r.weightKg,
            fatPct = r.fatPct,
            heartRate = r.heartRate,
            estimates = r.estimates,
        )

        if (!saved) {
            Button(
                onClick = { vm.savePendingMeasurement() },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text("保存入库")
            }
        }
        OutlinedButton(
            onClick = { vm.savePendingMeasurement(); onDone() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(if (saved) "完成" else "保存并返回称重")
        }
    }
}
