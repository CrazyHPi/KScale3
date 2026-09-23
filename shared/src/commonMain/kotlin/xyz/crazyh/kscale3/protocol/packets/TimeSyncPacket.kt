package xyz.crazyh.kscale3.protocol.packets

import xyz.crazyh.kscale3.protocol.putU16Le

/**
 * 0x52 时间同步（8B 明文 → 0xDB 单帧）。
 *
 * ⚠️ 私有布局 `<HBBBBBB` = 年(u16le) 月 日 时 分 秒 星期（周一=1），共 8 字节，
 * **不是**标准 GATT CTS 10 字节格式（TECH.md 写 "standard GATT" 有误，以 scale3.py L512 实测为准）。
 * 取**本地 naive 时间**（无时区/偏移字段）。
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
    /** 星期（周一=1 … 周日=7） */
    val dayOfWeek: Int,
) {
    fun encode(): ByteArray {
        require(year in 0..0xFFFF) { "year out of u16: $year" }
        require(month in 1..12) { "month out of 1..12: $month" }
        require(day in 1..31) { "day out of 1..31: $day" }
        require(hour in 0..23) { "hour out of 0..23: $hour" }
        require(minute in 0..59) { "minute out of 0..59: $minute" }
        require(second in 0..59) { "second out of 0..59: $second" }
        require(dayOfWeek in 1..7) { "dayOfWeek out of 1..7 (Mon=1): $dayOfWeek" }
        val out = ByteArray(8)
        out.putU16Le(0, year)
        out[2] = month.toByte()
        out[3] = day.toByte()
        out[4] = hour.toByte()
        out[5] = minute.toByte()
        out[6] = second.toByte()
        out[7] = dayOfWeek.toByte()
        return out
    }
}
