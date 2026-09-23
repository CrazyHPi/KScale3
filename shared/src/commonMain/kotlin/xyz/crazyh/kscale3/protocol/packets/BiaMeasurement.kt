package xyz.crazyh.kscale3.protocol.packets

import xyz.crazyh.kscale3.protocol.getU16Le

/**
 * 0x97 实时 BIA 流（38B 解密后，TECH.md §7.2）。
 * ⚠️ magic 0xCD → AES(WorkKey) 解密；0xBD → 明文直接用（兼容 SUMMARY §9 差异）。
 * ⚠️ 严格 38B 拒收短包（参考实现解密后 ≥26B 就解析完置事件——hands 缺省为空、漏读 dow 字节；
 * 此处按 PLAN 8B 时间戳布局解析 dow，属收紧而非偏离线上）。
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
    companion object {
        const val SIZE: Int = 38

        fun decode(data: ByteArray): BiaMeasurement {
            require(data.size == SIZE) { "BIA payload must be ${SIZE}B, was ${data.size}B" }
            return BiaMeasurement(
                weightKg = data.getU16Le(0) / 100.0,
                fatPct = data.getU16Le(2) / 10.0,
                year = data.getU16Le(4),
                month = data[6].toInt() and 0xFF,
                day = data[7].toInt() and 0xFF,
                hour = data[8].toInt() and 0xFF,
                minute = data[9].toInt() and 0xFF,
                second = data[10].toInt() and 0xFF,
                dayOfWeek = data[11].toInt() and 0xFF,
                feet = List(6) { i -> data.getU16Le(12 + 2 * i) },
                heartRate = data.getU16Le(24),
                hands = List(6) { i -> data.getU16Le(26 + 2 * i) },
            )
        }
    }
}
