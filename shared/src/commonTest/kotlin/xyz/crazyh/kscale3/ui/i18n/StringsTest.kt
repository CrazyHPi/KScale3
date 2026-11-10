package xyz.crazyh.kscale3.ui.i18n

import xyz.crazyh.kscale3.domain.LanguageSetting
import xyz.crazyh.kscale3.domain.WeightUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringsTest {

    @Test
    fun parameterizedStrings_includeArguments() {
        listOf(ZhStrings, EnStrings).forEach { s ->
            assertContains(s.errorTimeout("randA"), "randA")
            assertContains(s.errorUnexpectedReply("bia", 7), "bia")
            assertContains(s.errorUnexpectedReply("bia", 7), "7")
            assertContains(s.errorUnexpectedReply("bia", null), "bia")
            assertContains(s.sessionProfileUnknownCode(9), "9")
            assertContains(s.scanBroadcastCount(3), "3")

            val summary = s.usersCardSummary(age = 30, heightCm = 175, goalKg = 68.0, lastKg = 70.2)
            assertContains(summary, "30")
            assertContains(summary, "175")
            assertContains(summary, "68.0")
            assertContains(summary, "70.2")
        }
    }

    @Test
    fun weightUnitNames_areDistinctAndNonBlank() {
        listOf(ZhStrings, EnStrings).forEach { s ->
            val names = WeightUnit.entries.map { s.weightUnitName(it) }
            names.forEach { assertTrue(it.isNotBlank()) }
            assertEquals(names.size, names.toSet().size)
        }
    }

    @Test
    fun languageNames_coverAllSettings() {
        listOf(ZhStrings, EnStrings).forEach { s ->
            LanguageSetting.entries.forEach { setting ->
                assertTrue(s.languageOptionName(setting).isNotBlank())
                assertTrue(s.languageSettingValue(setting, AppLanguage.Chinese).isNotBlank())
                assertTrue(s.languageSettingValue(setting, AppLanguage.English).isNotBlank())
            }
        }
    }

    @Test
    fun englishRepresentativeStrings_haveNoCjk() {
        fun hasCjk(text: String) = text.any { it in '一'..'鿿' }

        val s = EnStrings
        listOf(
            s.tabWeighIn,
            s.tabUsers,
            s.tabHistory,
            s.tabSettings,
            s.commonCancel,
            s.sessionConnecting,
            s.errorTimeout("step"),
            s.scanTitle,
            s.scanFailed(null),
            s.usersTitle,
            s.settingsTitle,
            s.settingsLanguage,
            s.weightUnitName(WeightUnit.Kg),
            s.weightUnitName(WeightUnit.Lb),
        ).forEach { text -> assertTrue(!hasCjk(text), "CJK leaked into English: $text") }
    }
}
