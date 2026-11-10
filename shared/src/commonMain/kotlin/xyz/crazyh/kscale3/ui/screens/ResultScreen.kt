package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.crazyh.kscale3.ui.components.MeasurementDetail
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.preview.previewResult
import xyz.crazyh.kscale3.ui.vm.PendingMeasurement
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

    ResultContent(
        pending = pending,
        onSave = vm::savePendingMeasurement,
        onDone = onDone,
    )
}

/** 无 ViewModel 的结果 UI（供 @Preview）。 */
@Composable
private fun ResultContent(
    pending: PendingMeasurement?,
    onSave: () -> Unit,
    onDone: () -> Unit,
) {
    val saved = pending == null
    val strings = LocalStrings.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(strings.resultTitle, style = MaterialTheme.typography.headlineSmall)

        if (pending == null) {
            onDone()
            return
        }

        val r = pending.result
        Text(strings.deviceTime(r.deviceTs), style = MaterialTheme.typography.bodySmall)

        MeasurementDetail(
            weightKg = r.weightKg,
            fatPct = r.fatPct,
            heartRate = r.heartRate,
            estimates = r.estimates,
        )

        if (!saved) {
            Button(
                onClick = {
                    onSave()
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(strings.resultSave)
            }
        }
    }
}

@Preview
@Composable
private fun ResultScreenPreview() {
    MaterialTheme {
        ResultContent(
            pending = PendingMeasurement(userId = 1, result = previewResult),
            onSave = {},
            onDone = {},
        )
    }
}
