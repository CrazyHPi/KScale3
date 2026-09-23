package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 测量结果屏（PLAN UI-4）：真值卡（体重/体脂/心率）
 * + 估算卡（肌肉量/水分/骨量等，全部标注"估算值"）+ 保存入库。
 */
@Composable
fun ResultScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("测量结果")
        // TODO 阶段4：真值卡（体重/体脂/心率）
        // TODO 阶段4：估算卡（标注"估算值"）+ 保存按钮
    }
}
