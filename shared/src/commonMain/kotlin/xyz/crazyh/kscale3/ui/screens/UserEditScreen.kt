package xyz.crazyh.kscale3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 用户编辑/新建屏（PLAN UI-2）：姓名/性别/年龄/身高/目标体重表单。
 */
@Composable
fun UserEditScreen(userId: Long?) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(if (userId == null) "新建用户" else "编辑用户")
        // TODO 阶段4：表单（姓名/性别/年龄/身高/目标体重）+ 保存
        // TODO 阶段4："注册到秤（Mode 1）"入口（未注册时）
    }
}
