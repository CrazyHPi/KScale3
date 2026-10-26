package xyz.crazyh.kscale3.ui

import kotlinx.serialization.Serializable

/** 类型安全导航路由（navigation-compose MP）。 */
@Serializable
sealed interface Route {
    /** 设备扫描/配对（含 MAC 输入） */
    @Serializable
    data object DeviceScan : Route

    @Serializable
    data object Users : Route

    /** 用户编辑/新建；[userId] 为 null 表示新建 */
    @Serializable
    data class UserEdit(val userId: Long? = null) : Route

    @Serializable
    data object WeighIn : Route

    @Serializable
    data object Result : Route

    @Serializable
    data object History : Route

    @Serializable
    data object Settings : Route
}
