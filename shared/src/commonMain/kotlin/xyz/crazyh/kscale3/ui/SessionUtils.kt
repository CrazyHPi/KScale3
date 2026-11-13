package xyz.crazyh.kscale3.ui

/** HUID 打码显示（设置页；真实 HUID 不落日志/截图）。 */
fun maskHuid(huid: String): String =
    if (huid.length <= 8) "••••" else huid.take(9) + "••••" + huid.takeLast(4)
