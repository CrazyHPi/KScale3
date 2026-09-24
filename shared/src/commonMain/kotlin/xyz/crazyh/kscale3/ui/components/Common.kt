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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.roundToInt
import xyz.crazyh.kscale3.session.SessionState
import xyz.crazyh.kscale3.ui.sessionGuide

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
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
    if (!visible) return
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
            TextButton(onClick = onDismiss) { Text("隐藏") }
        },
    )
}
