package xyz.crazyh.kscale3.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 当前生效的文案表。默认中文，保证 `@Preview` 与未包裹场景仍可渲染；
 * 根节点 `App` 按语言设置 provides 对应实现，切换语言即整棵 UI 重组。
 */
val LocalStrings = staticCompositionLocalOf<AppStrings> { ZhStrings }
