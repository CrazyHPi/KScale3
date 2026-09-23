package xyz.crazyh.kscale3.ui

import kotlinx.serialization.Serializable

/** 类型安全导航路由（navigation-compose MP）。 */
@Serializable
sealed interface Route {
    /** 设备扫描/配对（含 MAC 输入） */
    @Serializable
    data object DeviceScan : Route

    /** 用户列表 */
    @Serializable
    data object Users : Route

    /** 用户编辑/新建；[userId] 为 null 表示新建 */
    @Serializable
    data class UserEdit(val userId: Long? = null) : Route

    /** 称重 */
    @Serializable
    data object WeighIn : Route

    /** 测量结果 */
    @Serializable
    data object Result : Route

    /** 历史与趋势 */
    @Serializable
    data object History : Route

    /** 设置 */
    @Serializable
    data object Settings : Route
}
