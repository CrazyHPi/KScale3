package xyz.crazyh.kscale3.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.i18n.sessionGuide
import kotlin.math.pow
import kotlin.math.roundToInt

/** 数值格式化（KMP 无 String.format）：四舍五入到 [decimals] 位。 */
fun Double.rounded(decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val v = (this * factor).roundToInt() / factor
    return v.toString()
}

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

/** 可点击设置栏：`[名称  值（可空）>]`；[enabled] 为 false 时不可点（如未配对设备）。 */
@Composable
fun SettingEntry(
    name: String,
    value: String? = null,
    enabled: Boolean = true,
    action: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clickable(enabled = enabled) { action() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!value.isNullOrBlank()) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    val strings = LocalStrings.current
    SectionTitle(strings.measurementTrueSection)
    MetricCard(title = strings.measurementBodyCard) {
        MetricRow(strings.metricWeight, "${weightKg.rounded(2)} kg")
        MetricRow(strings.metricFatPct, "${fatPct.rounded(1)} %")
        MetricRow(strings.metricHeartRate, "${heartRate} bpm")
    }

    SectionTitle(strings.measurementEstimatesSection)
    MetricCard(title = strings.measurementCompositionCard, badge = strings.estimateBadge) {
        MetricRow(strings.metricFatFreeMass, "${(weightKg - estimates.fatMassKg).rounded(2)} kg")
        MetricRow(strings.metricFatMass, "${estimates.fatMassKg.rounded(2)} kg")
        MetricRow(strings.metricBodyWater, "${estimates.tbwKg.rounded(2)} kg")
        MetricRow(strings.metricMuscleMass, "${estimates.muscleKg.rounded(2)} kg")
        MetricRow(strings.metricBoneMass, "${estimates.boneKg.rounded(2)} kg")
    }
    MetricCard(title = strings.measurementOtherCard, badge = strings.estimateBadge) {
        MetricRow(strings.metricBmi, estimates.bmi.rounded(1))
        MetricRow(strings.metricBmr, "${estimates.bmr.rounded(0)} kcal")
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
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(strings.sessionDialogTitle) },
        text = {
            Column {
                Text(strings.sessionGuide(state))
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text(strings.commonCancel) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    dismissed = true
                    onDismiss()
                },
            ) { Text(strings.commonHide) }
        },
    )
}
