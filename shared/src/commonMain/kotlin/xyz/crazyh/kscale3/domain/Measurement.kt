package xyz.crazyh.kscale3.domain

import kotlinx.datetime.Instant

/**
 * 入库测量记录（对应 `measurement` 表）。
 * 真值 = weight / fat% / HR；其余为 [BodyCompositionEstimator] 估算值快照（按测量时入库）。
 * 12 路原始阻抗以紧凑文本（CSV of u16）存储备用。
 */
data class Measurement(
    val id: Long,
    val userId: Long,
    val measuredAt: Instant,
    val weightKg: Double,
    val fatPct: Double,
    val heartRate: Int,
    /** 脚部 6 路原始阻抗 */
    val feetImp: List<Int>,
    /** 手部 6 路原始阻抗 */
    val handsImp: List<Int>,
    /** 设备时间戳原文（BIA 帧内 Y-m-d-h-m-s+dow） */
    val deviceTs: String,
    // ---- 估算值快照（UI 标注"估算值"）----
    val fatMassKg: Double,
    val tbwKg: Double,
    val boneKg: Double,
    val muscleKg: Double,
    val bmi: Double,
    val bmr: Double,
)

/** 入库快照 → 详情 UI 使用的估算值形态。 */
fun Measurement.toEstimates(): BodyCompositionEstimates = BodyCompositionEstimates(
    fatMassKg = fatMassKg,
    tbwKg = tbwKg,
    boneKg = boneKg,
    muscleKg = muscleKg,
    bmi = bmi,
    bmr = bmr,
)
