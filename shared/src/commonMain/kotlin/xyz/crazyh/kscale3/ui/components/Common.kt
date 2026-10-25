package xyz.crazyh.kscale3.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import xyz.crazyh.kscale3.domain.BodyCompositionEstimates
import xyz.crazyh.kscale3.session.SessionState
import xyz.crazyh.kscale3.ui.sessionGuide
import kotlin.math.pow
import kotlin.math.roundToInt

/** 数值格式化（KMP 无 String.format）：四舍五入到 [decimals] 位。 */
fun Double.rounded(decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val v = (this * factor).roundToInt() / factor
    return v.toString()
}

/** 小节标题。 */
@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/** 名称 + 数值两列的指标行。 */
@Composable
fun MetricRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** 指标卡片（真值卡/估算卡共用；估算卡由 [badge] 标注"估算值"）。 */
@Composable
fun MetricCard(title: String, badge: String? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (badge != null) {
                    Text(
                        "  $badge",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            content()
        }
    }
}

/**
 * 测量详情（真值卡 + 估算卡）：结果屏与历史详情弹窗共用，保证两处展示一致。
 * 真值 = [weightKg] / [fatPct] / [heartRate]；其余来自 [estimates]（UI 标注"估算值"）。
 */
@Composable
fun MeasurementDetail(
    weightKg: Double,
    fatPct: Double,
    heartRate: Int,
    estimates: BodyCompositionEstimates,
) {
    SectionTitle("真值（秤直接测量）")
    MetricCard(title = "身体数据") {
        MetricRow("体重", "${weightKg.rounded(2)} kg")
        MetricRow("体脂率", "${fatPct.rounded(1)} %")
        MetricRow("心率", "${heartRate} bpm")
    }

    SectionTitle("估算值（经验公式，仅供参考）")
    MetricCard(title = "体成分", badge = "估算值") {
        MetricRow("去脂体重", "${(weightKg - estimates.fatMassKg).rounded(2)} kg")
        MetricRow("脂肪量", "${estimates.fatMassKg.rounded(2)} kg")
        MetricRow("体水分", "${estimates.tbwKg.rounded(2)} kg")
        MetricRow("肌肉量", "${estimates.muscleKg.rounded(2)} kg")
        MetricRow("骨量", "${estimates.boneKg.rounded(2)} kg")
    }
    MetricCard(title = "其他", badge = "估算值") {
        MetricRow("BMI", estimates.bmi.rounded(1))
        MetricRow("基础代谢", "${estimates.bmr.rounded(0)} kcal")
    }
}

/**
 * 会话进度对话框：按 [SessionState] 显示引导文案 + 不确定进度 + 可取消。
 * [state] 为 null 或终态（Done/Paired/Failed/Idle）时自动消失（终态的结论由各屏自行展示）。
 */
@Composable
fun SessionProgressDialog(
    state: SessionState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val visible = when (state) {
        SessionState.Idle, is SessionState.Done, is SessionState.Paired, is SessionState.Failed -> false
        else -> true
    }
    // 「隐藏」只收起对话框、不打断会话；进入新的进行中状态时自动重新出现
    var dismissed by remember(state) { mutableStateOf(false) }
    if (!visible || dismissed) return
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("称重/注册") },
        text = {
            Column {
                Text(sessionGuide(state))
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    dismissed = true
                    onDismiss()
                },
            ) { Text("隐藏") }
        },
    )
}
