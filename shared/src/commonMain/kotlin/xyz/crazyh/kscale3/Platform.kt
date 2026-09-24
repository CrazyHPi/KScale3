package xyz.crazyh.kscale3

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/** iOS（含 iPadOS）判定：MAC 输入必填 + 提示"见秤身标签"（PLAN UI-1）。 */
fun isIosPlatform(): Boolean = getPlatform().name.startsWith("iOS")