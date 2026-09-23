package xyz.crazyh.kscale3.protocol.packets

/**
 * 0x97 实时 BIA 流（38B 解密后，TECH.md §7.2）。
 * ⚠️ magic 0xCD → AES(WorkKey) 解密；0xBD → 明文直接用（兼容 SUMMARY §9 差异）。
 * 阻抗为原始计数（12 路入库备用）；换算 Ω 或厂商 BIA 模型超出范围，
 * App 侧的肌肉量/水分/骨量是经验公式**估算**（见 BodyCompositionEstimator）。
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
    /** 星期（周一=1） */
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

        fun decode(data: ByteArray): BiaMeasurement = TODO("Phase 1: protocol implementation")
    }
}
