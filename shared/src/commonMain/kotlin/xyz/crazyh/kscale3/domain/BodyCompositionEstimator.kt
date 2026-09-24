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
    ): BodyCompositionEstimates {
        require(weightKg > 0.0) { "weightKg must be positive: $weightKg" }
        require(fatPct in 0.0..100.0) { "fatPct out of 0..100: $fatPct" }
        require(heightCm > 0) { "heightCm must be positive: $heightCm" }
        require(age > 0) { "age must be positive: $age" }

        val fatMass = weightKg * fatPct / 100.0
        val ffm = weightKg - fatMass
        val heightM = heightCm / 100.0
        val bmr = when (sex) {
            Sex.Male -> 10.0 * weightKg + 6.25 * heightCm - 5.0 * age + 5.0
            Sex.Female -> 10.0 * weightKg + 6.25 * heightCm - 5.0 * age - 161.0
        }
        return BodyCompositionEstimates(
            fatMassKg = fatMass,
            tbwKg = TBW_RATE * ffm,
            boneKg = BONE_RATE * ffm,
            muscleKg = MUSCLE_RATE * ffm,
            bmi = weightKg / (heightM * heightM),
            bmr = bmr,
        )
    }

    /** 脱脂组织水合率（Pace & Rathbun；个体 0.70–0.75）。 */
    private const val TBW_RATE = 0.73

    /** 骨量/FFM 经验系数（消费级体脂秤常见值）。 */
    private const val BONE_RATE = 0.0534

    /** 肌肉量/FFM 经验系数。 */
    private const val MUSCLE_RATE = 0.50
}
