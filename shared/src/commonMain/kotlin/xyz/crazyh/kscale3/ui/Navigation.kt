package xyz.crazyh.kscale3.ui

import kotlinx.serialization.Serializable

/** 类型安全导航路由（navigation-compose MP）。 */
@Serializable
sealed interface Route {
    /** 设备扫描/配对（含 MAC 输入） */
    @Serializable
    data object DeviceScan : Route

    /** 档案：内部二级 Tab（历史 / 成员）。 */
    @Serializable
    data object Profile : Route

    /** 成员编辑/新建；[userId] 为 null 表示新建 */
    @Serializable
    data class UserEdit(val userId: Long? = null) : Route

    @Serializable
    data object WeighIn : Route

    @Serializable
    data object Result : Route

    /** 单次测量详情（整页，替换原历史弹窗） */
    @Serializable
    data class MeasurementDetail(val measurementId: Long) : Route

    @Serializable
    data object Settings : Route
}

/** 档案页二级 Tab。 */
enum class ProfileTab { History, Members }
