package xyz.crazyh.kscale3.data.repository

import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.crazyh.kscale3.data.db.KScale3Database
import xyz.crazyh.kscale3.domain.LanguageSetting

/** [AppSettingsRepository] 的 SQLDelight 实装（`app_setting` 表）。 */
class SqlAppSettingsRepository(
    private val db: KScale3Database,
) : AppSettingsRepository {

    private val queries get() = db.app_settingQueries

    override fun observeLanguage(): Flow<LanguageSetting> =
        queries.selectValue(KEY_LANGUAGE)
            .asFlow()
            .map { query -> LanguageSetting.fromStorage(query.executeAsOneOrNull()) }

    override suspend fun setLanguage(setting: LanguageSetting) {
        queries.upsert(KEY_LANGUAGE, setting.storageValue)
    }

    private companion object {
        const val KEY_LANGUAGE: String = "language"
    }
}
