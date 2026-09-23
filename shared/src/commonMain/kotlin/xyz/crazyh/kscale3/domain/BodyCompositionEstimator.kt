package xyz.crazyh.kscale3.domain

/**
 * 体成分估算（纯函数 + 单测，PLAN「体成分估算」）。
 *
 * 真值 = weight / fat% / HR（秤直接值）。以下全部为**估算值**，UI 需标注：
 * - BMI = weight / height²
 * - fatMass = weight × fat%；FFM = weight − fatMass
 * - TBW ≈ 0.73 × FFM（Pace & Rathbun 脱脂组织水合率；个体实际 0.70–0.75）
 * - boneMass ≈ 0.0534 × FFM；muscleMass ≈ 0.50 × FFM
 *   （消费级体脂秤常见经验系数，±20% 量级，勿当 DXA/CT 真值）
 * - BMR = Mifflin-St Jeor（男 10w+6.25h−5a+5 / 女 10w+6.25h−5a−161）
 *
 * 系数集中在本处便于日后校准（PLAN 风险项 5）。
 */
object BodyCompositionEstimator {

    /** [weightKg] kg、[fatPct] %、[heightCm] cm。表驱动用例见 PLAN「测试与验证」。 */
    fun estimate(
        weightKg: Double,
        fatPct: Double,
        heightCm: Int,
        sex: Sex,
        age: Int,
    ): BodyCompositionEstimates = TODO("Phase 3: data + domain")
}
