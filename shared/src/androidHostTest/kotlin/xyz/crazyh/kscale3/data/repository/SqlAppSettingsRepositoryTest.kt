package xyz.crazyh.kscale3.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import xyz.crazyh.kscale3.data.db.KScale3Database
import xyz.crazyh.kscale3.domain.LanguageSetting
import kotlin.test.Test
import kotlin.test.assertEquals

/** 应用设置仓库（JdbcSqliteDriver 内存库）与 v2→v3 迁移。 */
class SqlAppSettingsRepositoryTest {

    private fun createDb(): KScale3Database {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KScale3Database.Schema.create(driver)
        return KScale3Database(driver)
    }

    @Test
    fun language_default_isSystemWhenAbsent() = runTest {
        val repo = SqlAppSettingsRepository(createDb())
        assertEquals(LanguageSetting.System, repo.observeLanguage().first())
    }

    @Test
    fun language_roundTrip_overwritesSingleValue() = runTest {
        val repo = SqlAppSettingsRepository(createDb())
        LanguageSetting.entries.forEach { setting ->
            repo.setLanguage(setting)
            assertEquals(setting, repo.observeLanguage().first())
        }
    }

    @Test
    fun language_unknownStoredValue_fallsBackToSystem() = runTest {
        val db = createDb()
        db.app_settingQueries.upsert("language", "xx")
        val repo = SqlAppSettingsRepository(db)
        assertEquals(LanguageSetting.System, repo.observeLanguage().first())
    }

    @Test
    fun settings_migration_v2_addsAppSettingTable() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        assertEquals(3L, KScale3Database.Schema.version)
        KScale3Database.Schema.migrate(driver, 2, 3)

        val repo = SqlAppSettingsRepository(KScale3Database(driver))
        assertEquals(LanguageSetting.System, repo.observeLanguage().first())
        repo.setLanguage(LanguageSetting.English)
        assertEquals(LanguageSetting.English, repo.observeLanguage().first())
    }
}
