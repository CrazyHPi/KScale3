package xyz.crazyh.kscale3.domain

/**
 * 秤显示单位（TECH.md §7.3：0x5A 设置 / 7e6dbc73 查询）。
 * [code] 为线上单位码；展示名由 UI 文案表提供（`AppStrings.weightUnitName`）。
 */
enum class WeightUnit(val code: Int) {
    Kg(1),
    Jin(3),

    Lb(2);

    companion object {
        /** 单位码 → 单位；未知码返回 null。 */
        fun fromCode(code: Int): WeightUnit? = entries.firstOrNull { it.code == code }
    }
}
