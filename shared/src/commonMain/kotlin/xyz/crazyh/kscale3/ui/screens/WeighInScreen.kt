package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 称重屏（PLAN UI-3）：选用户 → 发起 Mode 2 → 按 [xyz.crazyh.kscale3.session.SessionState]
 * 显示引导（"请上秤站稳并握住把手"…）；超时/Code 1/2 的恢复动作（重试/去注册/换人）。
 */
@Composable
fun WeighInScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("开始称重")
        // TODO 阶段4：用户选择
        // TODO 阶段4：SessionState 引导文案 + 超时/Code 1/2 恢复动作
    }
}
