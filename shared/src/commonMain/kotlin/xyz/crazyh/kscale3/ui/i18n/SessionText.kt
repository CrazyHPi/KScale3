package xyz.crazyh.kscale3.ui.i18n

import xyz.crazyh.kscale3.domain.LanguageSetting
import xyz.crazyh.kscale3.domain.WeightUnit
import xyz.crazyh.kscale3.session.SessionError
import xyz.crazyh.kscale3.session.SessionState

/** 会话状态 → 引导文案（PLAN UI-3：按 SessionState 显示引导）。 */
fun AppStrings.sessionGuide(state: SessionState): String = when (state) {
    SessionState.Idle -> sessionIdle
    SessionState.Connecting -> sessionConnecting
    SessionState.Handshaking -> sessionHandshaking
    SessionState.AwaitingTare -> sessionAwaitingTare
    SessionState.SyncingTime -> sessionSyncingTime
    SessionState.SendingProfile -> sessionSendingProfile
    is SessionState.ProfileResponded -> when (state.code) {
        0 -> sessionProfileRecognized
        1 -> sessionProfileMissingTare
        2 -> sessionProfileMismatch
        else -> sessionProfileUnknownCode(state.code)
    }

    SessionState.AwaitingMeasurement -> sessionAwaitingMeasurement
    SessionState.Committing -> sessionCommitting
    is SessionState.Done -> sessionDone
    is SessionState.Paired -> sessionPaired
    is SessionState.Failed -> sessionFailedPrefix + errorText(state.error)
}

/** 会话失败 → 原因文案（PLAN UI-3 恢复动作的文案基础）。 */
fun AppStrings.errorText(error: SessionError): String = when (error) {
    SessionError.TokenMismatch -> errorTokenMismatch
    SessionError.UserNotRecognised -> errorUserNotRecognised
    SessionError.MissingTare -> errorMissingTare
    is SessionError.Timeout -> errorTimeout(error.step)
    SessionError.Disconnected -> errorDisconnected
    SessionError.CrcError -> errorCrc
    SessionError.MacInvalid -> errorMacInvalid
    is SessionError.UnexpectedReply -> errorUnexpectedReply(error.step, error.code)
}

/** 秤显示单位 → 设置页展示名。 */
fun AppStrings.weightUnitName(unit: WeightUnit): String = when (unit) {
    WeightUnit.Kg -> unitKg
    WeightUnit.Jin -> unitJin
    WeightUnit.Lb -> unitLb
}

/** 语言设置项 → 选项名（选项名保留各自语言写法）。 */
fun AppStrings.languageOptionName(setting: LanguageSetting): String = when (setting) {
    LanguageSetting.System -> languageFollowSystem
    LanguageSetting.Chinese -> languageChinese
    LanguageSetting.English -> languageEnglish
}

/** 语言设置项当前值：跟随系统时显示实际生效语言，如「跟随系统（中文）」。 */
fun AppStrings.languageSettingValue(
    setting: LanguageSetting,
    effective: AppLanguage,
): String = when (setting) {
    LanguageSetting.System -> languageFollowSystemValue(
        when (effective) {
            AppLanguage.Chinese -> languageChinese
            AppLanguage.English -> languageEnglish
        }
    )

    else -> languageOptionName(setting)
}
