package xyz.crazyh.kscale3.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.crazyh.kscale3.session.SessionState
import xyz.crazyh.kscale3.ui.i18n.LocalStrings
import xyz.crazyh.kscale3.ui.i18n.sessionGuide
import kotlin.math.pow
import kotlin.math.roundToInt

/** 数值格式化（KMP 无 String.format）：四舍五入到 [decimals] 位。 */
fun Double.rounded(decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val v = (this * factor).roundToInt() / factor
    return if (decimals <= 0) v.toInt().toString() else v.toString()
}

/** 分区标题（设备扫描页等沿用）。 */
@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/**
 * 会话进度对话框：按 [SessionState] 显示引导文案 + 不确定进度 + 可取消。
 * 称重页改为内联展示，此组件保留给成员注册等压栈操作。
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
