package xyz.crazyh.kscale3.domain

/**
 * 界面语言设置（持久化在 `app_setting` 表；[System] = 跟随系统）。
 * [storageValue] 是稳定存储码，不随枚举命名变化——旧库升级后仍可解析。
 */
enum class LanguageSetting(val storageValue: String) {
    System("system"),
    Chinese("zh"),
    English("en");

    companion object {
        /** 未知/缺失值回落到 [System]（向前兼容）。 */
        fun fromStorage(value: String?): LanguageSetting =
            entries.firstOrNull { it.storageValue == value } ?: System
    }
}
