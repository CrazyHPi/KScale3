package xyz.crazyh.kscale3.ui

/** HUID 打码显示（SettingsScreen；真实 HUID 不落日志/截图）。 */
fun maskHuid(huid: String): String =
    if (huid.length <= 7) "****" else "****" + huid.takeLast(4)
