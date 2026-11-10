package xyz.crazyh.kscale3.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.crazyh.kscale3.data.repository.AppSettingsRepository
import xyz.crazyh.kscale3.domain.LanguageSetting

/**
 * 应用级设置（当前仅界面语言）。挂在根级，与 BLE/设备会话无关。
 */
class AppSettingsViewModel(
    private val repository: AppSettingsRepository,
) : ViewModel() {

    /**
     * 界面语言设置；`null` = 首帧尚未从数据库读到（App 用它做启动闸门，避免先闪中文）。
     * 读库失败时回落到跟随系统，避免根节点永远停在空白等待帧。
     */
    val language: StateFlow<LanguageSetting?> =
        repository.observeLanguage()
            .catch { e ->
                println("[KScale3] read language failed: ${e.message}")
                emit(LanguageSetting.System)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setLanguage(setting: LanguageSetting) {
        viewModelScope.launch {
            runCatching { repository.setLanguage(setting) }
                .onFailure { println("[KScale3] set language failed: ${it.message}") }
        }
    }
}
