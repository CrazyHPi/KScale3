package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 历史屏（PLAN UI-5）：按用户筛选、测量列表、体重趋势 Canvas 折线图
 * （实现趋势图前先读 dataviz skill——PLAN 要求）。
 */
@Composable
fun HistoryScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("历史记录")
        // TODO 阶段4：用户筛选 + 测量列表
        // TODO 阶段4：体重趋势 Canvas 折线图（先读 dataviz skill）
    }
}
