package xyz.crazyh.kscale3.domain

/**
 * 秤显示单位（TECH.md §7.3：0x5A 设置 / 7e6dbc73 查询）。
 * [code] 为线上单位码；[label] 为设置屏展示文案。
 */
enum class WeightUnit(val code: Int, val label: String) {
    Kg(1, "公斤"),
    Jin(3, "斤"),

    Lb(2, "磅");

    companion object {
        /** 单位码 → 单位；未知码返回 null。 */
        fun fromCode(code: Int): WeightUnit? = entries.firstOrNull { it.code == code }
    }
}
