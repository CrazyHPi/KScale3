package xyz.crazyh.kscale3.protocol.packets

/**
 * 0x52 时间同步（8B 明文 → 0xDB 单帧）。
 *
 * ⚠️ 私有布局 `<HBBBBBB` = 年(u16le) 月 日 时 分 秒 星期（周一=1），共 8 字节，
 * **不是**标准 GATT CTS 10 字节格式（TECH.md 写 "standard GATT" 有误，以 scale3.py L512 实测为准）。
 *
 * 向量：2026-09-22 14:30:05 周二 → 载荷 `ea0709160e1e0502` → 帧 `db0b00ea0709160e1e050266ff`。
 */
data class TimeSyncPacket(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    /** 星期（周一=1） */
    val dayOfWeek: Int,
) {
    fun encode(): ByteArray = TODO("Phase 1: protocol implementation")
}
