package xyz.crazyh.kscale3.ui.i18n

import androidx.compose.ui.text.intl.Locale
import xyz.crazyh.kscale3.domain.LanguageSetting

/** 实际生效的界面语言。 */
enum class AppLanguage { Chinese, English }

/**
 * 设置 + 系统语言 → 实际生效语言。
 * [LanguageSetting.System] 时：中文系统 → 中文，其余语言一律英文（产品决策）。
 */
fun resolveLanguage(setting: LanguageSetting, systemLanguage: String): AppLanguage =
    when (setting) {
        LanguageSetting.System ->
            if (systemLanguage.startsWith("zh", ignoreCase = true)) AppLanguage.Chinese
            else AppLanguage.English

        LanguageSetting.Chinese -> AppLanguage.Chinese
        LanguageSetting.English -> AppLanguage.English
    }

/** 生效语言 → 文案表（单例）。 */
fun stringsFor(language: AppLanguage): AppStrings = when (language) {
    AppLanguage.Chinese -> ZhStrings
    AppLanguage.English -> EnStrings
}

/**
 * 系统语言标签：Android = 默认 LocaleList 首项（系统语言变化会重建 Activity）；
 * iOS = `NSLocale.preferredLanguages` 首项（系统改语言会重启 App）。
 */
fun currentSystemLanguage(): String = Locale.current.language
