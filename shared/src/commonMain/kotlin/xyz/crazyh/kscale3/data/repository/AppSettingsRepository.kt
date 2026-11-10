package xyz.crazyh.kscale3.data.repository

import kotlinx.coroutines.flow.Flow
import xyz.crazyh.kscale3.domain.LanguageSetting

/** 应用级设置仓库（对应 `app_setting` 键值表）。 */
interface AppSettingsRepository {
    /** 界面语言设置；从未写入过时发射 [LanguageSetting.System]。 */
    fun observeLanguage(): Flow<LanguageSetting>

    /** 持久化界面语言设置（覆盖旧值）。 */
    suspend fun setLanguage(setting: LanguageSetting)
}
