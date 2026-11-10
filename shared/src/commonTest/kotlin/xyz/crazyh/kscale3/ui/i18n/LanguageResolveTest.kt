package xyz.crazyh.kscale3.ui.i18n

import xyz.crazyh.kscale3.domain.LanguageSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LanguageResolveTest {

    @Test
    fun system_chineseVariants_resolveChinese() {
        listOf("zh", "zh-CN", "zh-Hans", "ZH").forEach { system ->
            assertEquals(AppLanguage.Chinese, resolveLanguage(LanguageSetting.System, system))
        }
    }

    @Test
    fun system_nonChinese_resolvesEnglish() {
        listOf("en", "ja", "fr", "").forEach { system ->
            assertEquals(AppLanguage.English, resolveLanguage(LanguageSetting.System, system))
        }
    }

    @Test
    fun explicitSetting_overridesSystem() {
        assertEquals(AppLanguage.Chinese, resolveLanguage(LanguageSetting.Chinese, "en"))
        assertEquals(AppLanguage.English, resolveLanguage(LanguageSetting.English, "zh"))
    }

    @Test
    fun stringsFor_returnsSingletons() {
        assertSame(ZhStrings, stringsFor(AppLanguage.Chinese))
        assertSame(EnStrings, stringsFor(AppLanguage.English))
    }

    @Test
    fun languageSetting_fromStorage_roundTripAndFallback() {
        LanguageSetting.entries.forEach {
            assertEquals(it, LanguageSetting.fromStorage(it.storageValue))
        }
        assertEquals(LanguageSetting.System, LanguageSetting.fromStorage(null))
        assertEquals(LanguageSetting.System, LanguageSetting.fromStorage("unknown"))
    }
}
