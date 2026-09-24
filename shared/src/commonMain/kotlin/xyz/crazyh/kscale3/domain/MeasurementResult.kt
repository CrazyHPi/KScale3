package xyz.crazyh.kscale3.domain

import kotlinx.datetime.Instant

/** 一次称重会话的结果（保存入库前的形态）。 */
data class MeasurementResult(
    // ---- 真值（秤直接值）----
    val weightKg: Double,
    val fatPct: Double,
    val heartRate: Int,
    val feetImp: List<Int>,
    val handsImp: List<Int>,
    val deviceTs: String,
    /** 估算值（UI 全部标"估算值"） */
    val estimates: BodyCompositionEstimates,
)

/** 体成分估算值（经验公式，勿当 DXA/CT 真值——见 [BodyCompositionEstimator]）。 */
data class BodyCompositionEstimates(
    val fatMassKg: Double,
    /** 体水分 */
    val tbwKg: Double,
    /** 骨量 */
    val boneKg: Double,
    /** 肌肉量 */
    val muscleKg: Double,
    val bmi: Double,
    /** 基础代谢 */
    val bmr: Double,
)

/** 保存入库形态（估算值快照展开；[measuredAt] 取保存侧主机时间）。 */
fun MeasurementResult.toMeasurement(userId: Long, measuredAt: Instant): Measurement = Measurement(
    id = 0,
    userId = userId,
    measuredAt = measuredAt,
    weightKg = weightKg,
    fatPct = fatPct,
    heartRate = heartRate,
    feetImp = feetImp,
    handsImp = handsImp,
    deviceTs = deviceTs,
    fatMassKg = estimates.fatMassKg,
    tbwKg = estimates.tbwKg,
    boneKg = estimates.boneKg,
    muscleKg = estimates.muscleKg,
    bmi = estimates.bmi,
    bmr = estimates.bmr,
)
