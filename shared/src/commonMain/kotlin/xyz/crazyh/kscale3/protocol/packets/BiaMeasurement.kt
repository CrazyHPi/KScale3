package xyz.crazyh.kscale3.protocol.packets

import xyz.crazyh.kscale3.protocol.getU16Le

/**
 * 0x97 实时 BIA 流（解密后 26B 或 38B，TECH.md §7.2）。
 * ⚠️ magic 0xCD → AES(WorkKey) 解密；0xBD → 明文直接用（兼容 SUMMARY §9 差异）。
 * ⚠️ 长度（2026-10-25 真机实测）：
 * - **26B** = 体重 2 + 体脂 2 + 时间戳 **7B**（年 u16/月/日/时/分/秒）+ **标志字节 0xA0**（1B，非星期）
 *   + 脚部阻抗 12B + 心率 2；**无手部阻抗**，`dayOfWeek = 0`（由会话层按日期推算）。
 * - **38B** = 体重 2 + 体脂 2 + 时间戳 **8B**（含星期）+ 脚部 6×u16 + 心率 2 + 手部 6×u16。
 * 参考实现解密后 ≥26B 即解析、hands 缺省为空——此处对齐真机，短于 26B 才拒收。
 * 阻抗为原始计数（12 路入库备用）；肌肉量/水分/骨量由 App 侧经验公式**估算**
 * （见 [xyz.crazyh.kscale3.domain.BodyCompositionEstimator]）。
 *
 * 向量（38B）：`521cd700ea0709160e1e05020001010102010301040105014100000201020202030204020502`
 * → weight=72.50kg、fat=21.5%、2026-09-22T14:30:05(dow=2)、feet=[256..261]、HR=65、hands=[512..517]。
 */
data class BiaMeasurement(
    /** 总体重 kg（u16le/100） */
    val weightKg: Double,
    /** 体脂率 %（u16le/10） */
    val fatPct: Double,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    /** 星期（周一=1 … 周日=7） */
    val dayOfWeek: Int,
    /** 脚部 6 路原始阻抗 */
    val feet: List<Int>,
    /** 心率 BPM */
    val heartRate: Int,
    /** 手部 6 路原始阻抗 */
    val hands: List<Int>,
) {
    /**
     * 合理性过滤（PLAN 风险项 7 兜底）：MAC 填错 → RootKey 错 → WorkKey 对不上 → 解密出乱码，
     * 表现为体重/日期/心率荒谬。过滤后继续等下一帧；若只收到过不合理帧，会话层提示
     * [xyz.crazyh.kscale3.session.SessionError.MacInvalid]（"MAC 可能错误"）。
     * 手部阻抗可为空（26B 真机包），有则须为 6 路。
     */
    fun isPlausible(): Boolean =
        weightKg in 5.0..300.0 &&
            fatPct in 1.0..80.0 &&
            year in 2020..2099 &&
            month in 1..12 &&
            day in 1..31 &&
            hour in 0..23 &&
            minute in 0..59 &&
            second in 0..59 &&
            heartRate in 0..220 &&
            feet.size == 6 && (hands.isEmpty() || hands.size == 6)

    companion object {
        /** 完整包（含手部阻抗）。 */
        const val SIZE: Int = 38

        /** 最小包（无手部阻抗；真机实测格式）。 */
        const val MIN_SIZE: Int = 26

        fun decode(data: ByteArray): BiaMeasurement {
            require(data.size == SIZE || data.size == MIN_SIZE) {
                "BIA payload must be ${MIN_SIZE}B or ${SIZE}B, was ${data.size}B"
            }
            return BiaMeasurement(
                weightKg = data.getU16Le(0) / 100.0,
                fatPct = data.getU16Le(2) / 10.0,
                year = data.getU16Le(4),
                month = data[6].toInt() and 0xFF,
                day = data[7].toInt() and 0xFF,
                hour = data[8].toInt() and 0xFF,
                minute = data[9].toInt() and 0xFF,
                second = data[10].toInt() and 0xFF,
                feet = List(6) { i -> data.getU16Le(12 + 2 * i) },
                heartRate = data.getU16Le(24),
                hands = if (data.size >= SIZE) List(6) { i -> data.getU16Le(26 + 2 * i) } else emptyList(),
                // 38B 布局 offset 11 = 星期；26B 真机该字节是固定 0xA0（非星期），时间戳为 7 字节
                dayOfWeek = if (data.size >= SIZE) data[11].toInt() and 0xFF else 0,
            )
        }
    }
}
