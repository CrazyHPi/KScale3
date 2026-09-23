package xyz.crazyh.kscale3.domain

/** 新建/配对用的档案输入（HUID 由 [HuidGenerator] 生成、体重由验证称重捕获）。 */
data class UserProfileDraft(
    val name: String,
    val sex: Sex,
    val age: Int,
    val heightCm: Int,
    val goalWeightKg: Double?,
)
