package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 用户列表屏（PLAN UI-2）：档案列表、"已注册/未注册"徽章、
 * "注册到秤（Mode 1）"入口、目标体重。
 */
@Composable
fun UsersScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("用户列表")
        // TODO 阶段4：档案列表 + 注册状态徽章
        // TODO 阶段4：添加用户 → UserEditScreen
    }
}
