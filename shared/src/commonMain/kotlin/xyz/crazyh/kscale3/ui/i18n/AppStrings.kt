package xyz.crazyh.kscale3.ui.i18n

import androidx.compose.runtime.Immutable

/**
 * 全部用户可见文案（中文/英文各一份实现，见 [ZhStrings] / [EnStrings]）。
 *
 * - 参数化文案用函数（KMP 无 `String.format`，避免拼接语序写死在 UI 里）；
 * - 新增文案必须同时实现两份——编译器强制，不会漏翻译；
 * - 实现均为单例 `object`，可安全通过 [LocalStrings] 下发。
 */
@Immutable
interface AppStrings {

    // ---- 底部 Tab / 档案二级 Tab ----
    val tabProfile: String
    val tabWeighIn: String
    val tabUsers: String
    val tabHistory: String
    val tabMembers: String
    val tabSettings: String

    // ---- 通用按钮 ----
    val commonCancel: String
    val commonClose: String
    val commonHide: String
    val commonRetry: String
    val commonDelete: String
    val commonEdit: String
    val commonSave: String
    val commonBack: String

    // ---- 会话状态（叶子文案；映射见 SessionUtils.kt）----
    val sessionIdle: String
    val sessionConnecting: String
    val sessionHandshaking: String
    val sessionAwaitingTare: String
    val sessionSyncingTime: String
    val sessionSendingProfile: String
    val sessionProfileRecognized: String
    val sessionProfileMissingTare: String
    val sessionProfileMismatch: String
    fun sessionProfileUnknownCode(code: Int): String
    val sessionAwaitingMeasurement: String
    val sessionCommitting: String
    val sessionDone: String
    val sessionPaired: String
    val sessionFailedPrefix: String
    val sessionDialogTitle: String

    // ---- 会话错误 ----
    val errorTokenMismatch: String
    val errorUserNotRecognised: String
    val errorMissingTare: String
    fun errorTimeout(step: String): String
    val errorDisconnected: String
    val errorCrc: String
    val errorMacInvalid: String
    fun errorUnexpectedReply(step: String, code: Int?): String

    /** 扫描失败；[detail] 是平台异常原文（可能为英文技术信息），可为空。 */
    fun scanFailed(detail: String?): String

    // ---- 测量详情（Result 屏与历史详情弹窗共用）----
    val measurementTrueSection: String
    val measurementBodyCard: String
    val metricWeight: String
    val metricFatPct: String
    val metricHeartRate: String
    val measurementEstimatesSection: String
    val measurementCompositionCard: String
    val estimateBadge: String
    val metricFatFreeMass: String
    val metricFatMass: String
    val metricBodyWater: String
    val metricMuscleMass: String
    val metricBoneMass: String
    val measurementOtherCard: String
    val metricBmi: String
    val metricBmr: String
    fun deviceTime(deviceTs: String): String

    // ---- 设备扫描/配对 ----
    val scanTitle: String
    val scanSavedDevice: String
    val scanUseSavedDevice: String
    val scanPermissionRequired: String
    val scanGrantPermission: String
    val scanMacSection: String
    val scanMacLabelIos: String
    val scanMacLabelAndroid: String
    val scanChooseSection: String
    val scanScanning: String
    val scanStart: String
    val scanStop: String
    fun scanBroadcastCount(count: Int): String
    val scanBroadcastWakeHint: String
    val scanUnknownDevice: String
    val scanLikelyScale: String
    val scanIosHint: String
    val scanIosMacHint: String
    val scanSaveErrorIosSelection: String
    val scanSaveErrorInvalidMac: String
    val scanSaveUpdate: String
    val scanSaveAndContinue: String

    // ---- 用户列表 ----
    val usersTitle: String
    val usersAdd: String
    val usersNoDevice: String
    val usersEmpty: String
    val usersRegistered: String
    val usersUnregistered: String
    fun usersCardSummary(age: Int, heightCm: Int, goalKg: Double?, lastKg: Double?): String
    val usersReRegister: String
    val usersRegister: String
    fun usersDeleteTitle(name: String): String
    val usersDeleteMessage: String

    // ---- 用户编辑 ----
    val editNewTitle: String
    val editTitle: String
    val editName: String
    val editMale: String
    val editFemale: String
    val editAge: String
    val editHeight: String
    val editGoal: String
    val editErrorNameRequired: String
    val editErrorAgeRange: String
    val editErrorHeightRange: String
    val editErrorGoalFormat: String
    fun editHuidLine(maskedHuid: String): String
    val editReRegisterNewHuid: String
    val editRegisterMode1: String

    // ---- 称重 ----
    val weighTitle: String
    fun weighCurrentDevice(name: String): String
    val weighNoDevice: String
    val weighGoPair: String
    val weighSelectUser: String
    val weighUnregistered: String
    val weighStart: String
    val weighRecoveryGoRegister: String
    val weighRecoveryReRegister: String
    val weighRecoveryChangeUser: String
    val weighRecoveryEditMac: String

    // ---- 结果 ----
    val resultTitle: String
    val resultSave: String

    // ---- 历史 ----
    val historyTitle: String
    val historyAll: String
    val historyWeightTrend: String
    val historyEmpty: String
    val historyList: String
    val historyTapHint: String

    // ---- 设置 ----
    val settingsTitle: String
    val settingsDeviceInfo: String
    val settingsNoDevice: String
    val settingsName: String
    val settingsMac: String
    val settingsBleId: String
    val settingsWeightUnit: String
    val settingsConnectingScale: String
    val settingsRegisteredUsers: String
    val settingsNoRegisteredUsers: String
    val settingsForgetDevice: String
    val settingsFactoryResetting: String
    val settingsFactoryReset: String
    val settingsLanguage: String
    val settingsDebugMode: String
    val settingsLogFramesOnly: String
    val settingsLogEmpty: String
    val settingsForgetDeviceTitle: String
    val settingsForgetDeviceMessage: String
    val settingsForgetConfirm: String
    val settingsFactoryResetTitle: String
    val settingsFactoryResetMessage: String
    val settingsFactoryResetConfirm: String

    // ---- 秤显示单位 ----
    val unitKg: String
    val unitJin: String
    val unitLb: String

    // ---- 语言选项（选项名保留各自语言写法：中文 / English）----
    val languageFollowSystem: String
    val languageChinese: String
    val languageEnglish: String

    /** 「跟随系统（当前生效语言）」的展示文案，[effectiveName] 为当前生效语言的选项名。 */
    fun languageFollowSystemValue(effectiveName: String): String

    // ================= 重构后新增文案（设计稿 01–07） =================

    // ---- 档案 / 通用状态 ----
    val profileAddMember: String
    val profileNoDeviceBanner: String
    val profileGoPair: String
    val statusHigh: String
    val statusNormal: String

    // ---- 档案 · 称重历史 ----
    val historyLatest: String
    val historyComparedLast: String
    val historyRestingHeartRate: String
    val historyMetricWeight: String
    val historyMetricFat: String
    val historyFatTrend: String
    val historyRange30: String
    val historyRange90: String
    val historyRangeAll: String
    val historyRecords: String
    fun historyAllCount(count: Int): String
    val historyEmptyAction: String
    val historyEmptyBody: String
    val historyNoMembers: String
    val historyAddMemberAction: String
    fun historyHealthyRange(minPct: Int, maxPct: Int): String
    fun historyFatStatusLine(status: String, minPct: Int, maxPct: Int): String

    // ---- 档案 · 成员 ----
    fun membersCount(count: Int): String
    fun membersRegisteredCount(count: Int): String
    val membersRegisterHint: String
    val membersRegisteredOnScale: String
    val membersLastWeigh: String
    val membersNoMeasurements: String
    val membersRegisterFirst: String
    val membersRegisterFooter: String
    val membersNew: String
    fun membersProfileLine(male: Boolean, age: Int, heightCm: Int): String
    fun membersLastWeighLine(weight: String, monthDay: String): String
    fun membersDeleteTitle(name: String): String
    val membersDeleteMessage: String
    val membersEmpty: String

    // ---- 称重 · 准备 ----
    val weighDevicePaired: String
    val weighDeviceUnpaired: String
    val weighDeviceConnecting: String
    val weighMembers: String
    val weighRegisteredShort: String
    val weighAdd: String
    val weighNewMember: String
    val weighGuideTitle: String
    val weighGuideSubtitle: String
    fun weighLastWeigh(monthDay: String, weight: String): String
    val weighCancelOperation: String
    val weighStateConnecting: String
    val weighStateAwaitingTare: String
    val weighStateSyncing: String
    val weighStateMeasuring: String
    val weighStateSaving: String
    val weighStateFailed: String
    val weighStatePaired: String
    val weighNoMembers: String
    val weighAddMemberAction: String

    // ---- 称重 · 结果 ----
    val resultCompleted: String
    fun resultSavedTo(name: String): String
    val resultBodyComposition: String
    val resultReferenceOnly: String
    val resultSaving: String
    val resultSaveDone: String
    val resultDiscard: String
    val resultDiscardTitle: String
    val resultDiscardMessage: String
    val resultSaveFailed: String

    // ---- 测量详情 ----
    val detailTitle: String
    val detailThisWeight: String
    fun detailComparedLast(delta: String): String
    fun detailGoalLine(goalKg: String, diffKg: String): String
    fun detailGoalExceededLine(goalKg: String, extraKg: String): String
    fun detailGoalReached(goalKg: String): String
    val detailRawData: String
    val detailRawDataHint: String
    val detailDeviceTime: String
    val detailFeetImp: String
    val detailHandsImp: String
    val detailFootnote: String
    val detailDelete: String
    val detailDeleteTitle: String
    val detailDeleteMessage: String
    val detailMore: String

    // ---- 设置（重构） ----
    val settingsMyScale: String
    val settingsPreferences: String
    val settingsMaintenance: String
    val settingsAdvanced: String
    val settingsDeleteScale: String
    val settingsDeleteScaleSubtitle: String
    val settingsResetScale: String
    val settingsResetScaleSubtitle: String
    val settingsPaired: String
    val settingsConnected: String
    val settingsUnpaired: String
    val settingsGoPair: String
    val settingsNoDeviceShort: String
    val settingsDebugInfo: String
    val settingsModel: String
    val settingsSerial: String
    val settingsConfigState: String
    val settingsRegisteredHuid: String
    val settingsViewFrameLog: String
    val settingsFrameLogTitle: String
}
