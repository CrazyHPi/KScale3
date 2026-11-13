package xyz.crazyh.kscale3.ui.i18n

/** 中文文案（默认语言；现有界面原文逐条迁移自此）。 */
object ZhStrings : AppStrings {

    // ---- 底部 Tab / 档案二级 Tab ----
    override val tabProfile: String = "档案"
    override val tabWeighIn: String = "称重"
    override val tabUsers: String = "用户"
    override val tabHistory: String = "历史"
    override val tabMembers: String = "成员"
    override val tabSettings: String = "设置"

    // ---- 通用按钮 ----
    override val commonCancel: String = "取消"
    override val commonClose: String = "关闭"
    override val commonHide: String = "隐藏"
    override val commonRetry: String = "重试"
    override val commonDelete: String = "删除"
    override val commonEdit: String = "编辑"
    override val commonSave: String = "保存"
    override val commonBack: String = "返回"

    // ---- 会话状态 ----
    override val sessionIdle: String = "准备就绪"
    override val sessionConnecting: String = "正在连接秤…"
    override val sessionHandshaking: String = "正在认证…"
    override val sessionAwaitingTare: String = "请上秤站稳（验证称重，赤脚）"
    override val sessionSyncingTime: String = "正在同步时间…"
    override val sessionSendingProfile: String = "正在确认用户…"
    override val sessionProfileRecognized: String = "用户已识别"
    override val sessionProfileMissingTare: String = "缺少验证称重"
    override val sessionProfileMismatch: String = "用户不匹配"
    override fun sessionProfileUnknownCode(code: Int): String = "设备回应码 $code"
    override val sessionAwaitingMeasurement: String = "请上秤站稳\n等待体重/体脂/心率测量"
    override val sessionCommitting: String = "正在完成…"
    override val sessionDone: String = "称重完成"
    override val sessionPaired: String = "注册成功"
    override val sessionFailedPrefix: String = "失败："
    override val sessionDialogTitle: String = "称重/注册"

    // ---- 会话错误 ----
    override val errorTokenMismatch: String = "认证失败（校验未通过）"
    override val errorUserNotRecognised: String = "体重秤无法识别用户——请更换用户或重新注册"
    override val errorMissingTare: String = "缺少验证称重——请重新注册"
    override fun errorTimeout(step: String): String =
        "等待超时（$step）——请靠近秤并保持上秤姿势后重试"

    override val errorDisconnected: String = "连接断开——请靠近秤后重试"
    override val errorCrc: String = "通信校验失败——请重试"
    override val errorMacInvalid: String = "MAC 可能错误——请核对秤身标签后修改"
    override fun errorUnexpectedReply(step: String, code: Int?): String =
        "设备回应异常（$step${code?.let { "，码 $it" } ?: ""}）"

    override fun scanFailed(detail: String?): String =
        detail ?: "扫描失败（请检查蓝牙权限与开关）"

    // ---- 测量详情 ----
    override val measurementTrueSection: String = "真值（秤直接测量）"
    override val measurementBodyCard: String = "身体数据"
    override val metricWeight: String = "体重"
    override val metricFatPct: String = "体脂率"
    override val metricHeartRate: String = "心率"
    override val measurementEstimatesSection: String = "估算值（经验公式，仅供参考）"
    override val measurementCompositionCard: String = "体成分"
    override val estimateBadge: String = "估算值"
    override val metricFatFreeMass: String = "去脂体重"
    override val metricFatMass: String = "脂肪量"
    override val metricBodyWater: String = "体水分"
    override val metricMuscleMass: String = "肌肉量"
    override val metricBoneMass: String = "骨量"
    override val measurementOtherCard: String = "其他"
    override val metricBmi: String = "BMI"
    override val metricBmr: String = "基础代谢"
    override fun deviceTime(deviceTs: String): String = "设备时间：$deviceTs"

    // ---- 设备扫描/配对 ----
    override val scanTitle: String = "设备扫描与配对"
    override val scanSavedDevice: String = "已保存设备"
    override val scanUseSavedDevice: String = "使用已保存设备"
    override val scanPermissionRequired: String =
        "需要蓝牙权限才能扫描与连接——请点「授权」并在系统弹窗中允许。"

    override val scanGrantPermission: String = "授权蓝牙权限"
    override val scanMacSection: String = "设备 MAC 地址"
    override val scanMacLabelIos: String = "秤身标签 MAC（必填）"
    override val scanMacLabelAndroid: String = "MAC（留空取扫描地址，可手填覆盖）"
    override val scanChooseSection: String = "扫描 / 选择设备"
    override val scanScanning: String = "扫描中…"
    override val scanStart: String = "开始扫描"
    override val scanStop: String = "停止"
    override fun scanBroadcastCount(count: Int): String = "已收到 $count 条广播"
    override val scanBroadcastWakeHint: String =
        "——若一直为 0：请上秤唤醒（秤待机时不广播），并确认系统蓝牙已开"

    override val scanUnknownDevice: String = "(未知设备)"
    override val scanLikelyScale: String = "疑似秤"
    override val scanIosHint: String =
        "iOS 连接需先扫描并选择设备（CoreBluetooth UUID）；MAC 只用于 RootKey，不能代替扫描。"

    override val scanIosMacHint: String = "请填写秤身标签上的 MAC（RootKey 需要）。"
    override val scanSaveErrorIosSelection: String = "请先选择扫描到的设备，并填写秤身标签 MAC"
    override val scanSaveErrorInvalidMac: String = "MAC 格式无效（应为 XX:XX:XX:XX:XX:XX）"
    override val scanSaveUpdate: String = "保存并更新设备"
    override val scanSaveAndContinue: String = "保存设备并进入主界面"

    // ---- 用户列表 ----
    override val usersTitle: String = "成员管理"
    override val usersAdd: String = "添加成员"
    override val usersNoDevice: String = "尚未配对设备——请先在「设备」页扫描配对（注册需要连接秤）。"
    override val usersEmpty: String = "暂无用户，点右上角「添加用户」建立档案。"
    override val usersRegistered: String = "已注册"
    override val usersUnregistered: String = "未注册"
    override fun usersCardSummary(
        age: Int,
        heightCm: Int,
        goalKg: Double?,
        lastKg: Double?,
    ): String =
        "${age} 岁 · ${heightCm} cm" +
                (goalKg?.let { " · 目标 $it kg" } ?: "") +
                (lastKg?.let { " · 上次 $it kg" } ?: "")

    override val usersReRegister: String = "重新注册到秤"
    override val usersRegister: String = "注册到秤"
    override fun usersDeleteTitle(name: String): String = "删除用户「$name」？"
    override val usersDeleteMessage: String =
        "将删除本机档案与该用户的全部历史测量，操作不可撤销。\n" +
                "秤上已注册的 HUID 不受影响。"

    // ---- 用户编辑 ----
    override val editNewTitle: String = "新建成员"
    override val editTitle: String = "编辑成员"
    override val editName: String = "姓名"
    override val editMale: String = "男"
    override val editFemale: String = "女"
    override val editAge: String = "年龄"
    override val editHeight: String = "身高（cm）"
    override val editGoal: String = "目标体重（kg，可空）"
    override val editErrorNameRequired: String = "请输入姓名"
    override val editErrorAgeRange: String = "年龄应为 10–120"
    override val editErrorHeightRange: String = "身高应为 80–230 cm"
    override val editErrorGoalFormat: String = "目标体重格式无效"
    override fun editHuidLine(maskedHuid: String): String = "HUID：$maskedHuid（已注册）"
    override val editReRegisterNewHuid: String = "重新注册到秤（生成新 HUID）"
    override val editRegisterMode1: String = "注册到秤（Mode 1）"

    // ---- 称重 ----
    override val weighTitle: String = "称重"
    override fun weighCurrentDevice(name: String): String = "当前配对设备：$name"
    override val weighNoDevice: String = "尚未配对设备——请先在「设备」页扫描配对。"
    override val weighGoPair: String = "去配对设备"
    override val weighSelectUser: String = "选择用户"
    override val weighUnregistered: String = "未注册（需先注册到秤）"
    override val weighStart: String = "开始称重"
    override val weighRecoveryGoRegister: String = "去注册（补验证称重）"
    override val weighRecoveryReRegister: String = "重新注册"
    override val weighRecoveryChangeUser: String = "换人"
    override val weighRecoveryEditMac: String = "修改 MAC"

    // ---- 结果 ----
    override val resultTitle: String = "测量结果"
    override val resultSave: String = "保存入库"

    // ---- 历史 ----
    override val historyTitle: String = "历史与趋势"
    override val historyAll: String = "全部"
    override val historyWeightTrend: String = "体重趋势"
    override val historyEmpty: String = "暂无测量数据"
    override val historyList: String = "测量列表"
    override val historyTapHint: String = "点击卡片查看详细数据"

    // ---- 设置 ----
    override val settingsTitle: String = "设置"
    override val settingsDeviceInfo: String = "设备信息"
    override val settingsNoDevice: String = "尚未配对设备。"
    override val settingsName: String = "名称"
    override val settingsMac: String = "MAC 地址"
    override val settingsBleId: String = "BLE 标识"
    override val settingsWeightUnit: String = "秤显示单位"
    override val settingsConnectingScale: String = "连接秤..."
    override val settingsRegisteredUsers: String = "已注册成员"
    override val settingsNoRegisteredUsers: String = "暂无——可在「档案 · 成员」页注册到秤。"
    override val settingsForgetDevice: String = "删除秤"
    override val settingsFactoryResetting: String = "正在恢复出厂设置…"
    override val settingsFactoryReset: String = "秤恢复出厂设置"
    override val settingsLanguage: String = "语言"
    override val settingsDebugMode: String = "调试模式"
    override val settingsLogFramesOnly: String = "日志 仅显示帧 (hex)"
    override val settingsLogEmpty: String = "（暂无——发起一次称重/注册后显示）"
    override val settingsForgetDeviceTitle: String = "删除秤？"
    override val settingsForgetDeviceMessage: String =
        "将清除本机保存的配对信息。成员档案与称重历史会保留，再次使用需要重新扫描配对。"

    override val settingsForgetConfirm: String = "删除"
    override val settingsFactoryResetTitle: String = "重置秤？"
    override val settingsFactoryResetMessage: String =
        "将清除秤上的全部用户与绑定，本机配对也会移除。成员档案与称重历史会保留。"

    override val settingsFactoryResetConfirm: String = "重置"

    // ---- 秤显示单位 ----
    override val unitKg: String = "公斤"
    override val unitJin: String = "斤"
    override val unitLb: String = "磅"

    // ---- 语言选项 ----
    override val languageFollowSystem: String = "跟随系统"
    override val languageChinese: String = "中文（简体）"
    override val languageEnglish: String = "English"
    override fun languageFollowSystemValue(effectiveName: String): String =
        "跟随系统（$effectiveName）"

    // ================= 重构后新增文案（设计稿 01–07） =================

    override val profileAddMember: String = "添加成员"
    override val profileNoDeviceBanner: String = "尚未配对秤，配对后即可开始称重"
    override val profileGoPair: String = "去配对"
    override val statusHigh: String = "偏高"
    override val statusNormal: String = "正常"

    override val historyLatest: String = "最新测量"
    override val historyComparedLast: String = "较上次"
    override val historyRestingHeartRate: String = "静息心率"
    override val historyMetricWeight: String = "体重"
    override val historyMetricFat: String = "体脂"
    override val historyFatTrend: String = "体脂趋势"
    override val historyRange30: String = "近 30 天"
    override val historyRange90: String = "近 90 天"
    override val historyRangeAll: String = "全部"
    override val historyRecords: String = "称重记录"
    override fun historyAllCount(count: Int): String = "全部 $count 条"
    override val historyEmptyAction: String = "去称一次"
    override val historyEmptyBody: String = "完成一次称重后，这里会显示最新数据、趋势与记录。"
    override val historyNoMembers: String = "还没有成员"
    override val historyAddMemberAction: String = "添加成员"
    override fun historyHealthyRange(minPct: Int, maxPct: Int): String =
        "健康范围 $minPct–$maxPct%"

    override fun historyFatStatusLine(status: String, minPct: Int, maxPct: Int): String =
        "$status · 健康 $minPct–$maxPct%"

    override fun membersCount(count: Int): String = "$count 位成员"
    override fun membersRegisteredCount(count: Int): String = "$count 位已注册到秤"
    override val membersRegisterHint: String = "只有已注册到秤的成员才能发起称重"
    override val membersRegisteredOnScale: String = "已注册到秤"
    override val membersLastWeigh: String = "最近称重"
    override val membersNoMeasurements: String = "暂无称重记录"
    override val membersRegisterFirst: String = "注册后可开始称重"
    override val membersRegisterFooter: String = "注册后，秤可自动识别成员并归属测量记录。"
    override val membersNew: String = "新成员"
    override fun membersProfileLine(male: Boolean, age: Int, heightCm: Int): String =
        "${if (male) editMale else editFemale} · $age 岁 · $heightCm cm"

    override fun membersLastWeighLine(weight: String, monthDay: String): String =
        "$weight · $monthDay"

    override fun membersDeleteTitle(name: String): String = "删除成员「$name」？"
    override val membersDeleteMessage: String = "将同时删除该成员的称重历史，此操作不可撤销。"
    override val membersEmpty: String = "还没有成员，点右下角「添加成员」建立档案。"

    override val weighDevicePaired: String = "已配对"
    override val weighDeviceUnpaired: String = "未配对"
    override val weighDeviceConnecting: String = "连接中…"
    override val weighMembers: String = "称重成员"
    override val weighRegisteredShort: String = "已注册"
    override val weighAdd: String = "添加"
    override val weighNewMember: String = "新成员"
    override val weighGuideTitle: String = "请上秤并握住把手"
    override val weighGuideSubtitle: String = "赤脚站立，保持约 10 秒"
    override fun weighLastWeigh(monthDay: String, weight: String): String =
        "上次称重 $monthDay · $weight"

    override val weighCancelOperation: String = "取消称重"
    override val weighStateConnecting: String = "正在连接秤…"
    override val weighStateAwaitingTare: String = "请上秤完成验证"
    override val weighStateSyncing: String = "正在同步档案…"
    override val weighStateMeasuring: String = "请站稳并握住把手"
    override val weighStateSaving: String = "正在保存…"
    override val weighStateFailed: String = "称重未完成"
    override val weighStatePaired: String = "注册成功"
    override val weighNoMembers: String = "还没有成员，先添加一位成员"
    override val weighAddMemberAction: String = "添加成员"

    override val resultCompleted: String = "测量完成"
    override fun resultSavedTo(name: String): String = "将保存到 $name 的档案"
    override val resultBodyComposition: String = "身体成分"
    override val resultReferenceOnly: String = "仅供参考"
    override val resultSaving: String = "保存中…"
    override val resultSaveDone: String = "保存并完成"
    override val resultDiscard: String = "放弃本次结果"
    override val resultDiscardTitle: String = "放弃本次结果？"
    override val resultDiscardMessage: String = "本次测量尚未保存，放弃后将无法恢复。"
    override val resultSaveFailed: String = "保存失败，请重试"

    override val detailTitle: String = "测量详情"
    override val detailThisWeight: String = "本次体重"
    override fun detailComparedLast(delta: String): String = "较上次 $delta"
    override fun detailGoalLine(goalKg: String, diffKg: String): String =
        "目标 $goalKg · 还差 $diffKg"

    override fun detailGoalExceededLine(goalKg: String, extraKg: String): String =
        "目标 $goalKg · 已超出 $extraKg"

    override fun detailGoalReached(goalKg: String): String = "目标 $goalKg · 已达标"
    override val detailRawData: String = "原始数据"
    override val detailRawDataHint: String = "设备时间 · 脚部/手部阻抗 · 心率"
    override val detailDeviceTime: String = "设备时间"
    override val detailFeetImp: String = "脚部阻抗"
    override val detailHandsImp: String = "手部阻抗"
    override val detailFootnote: String = "估算值由经验公式计算，仅供参考。"
    override val detailDelete: String = "删除记录"
    override val detailDeleteTitle: String = "删除这条记录？"
    override val detailDeleteMessage: String = "此操作不可撤销。"
    override val detailMore: String = "更多"

    override val settingsMyScale: String = "我的秤"
    override val settingsPreferences: String = "偏好设置"
    override val settingsMaintenance: String = "设备维护"
    override val settingsAdvanced: String = "高级"
    override val settingsDeleteScale: String = "删除秤"
    override val settingsDeleteScaleSubtitle: String = "清除本机配对，保留档案与历史"
    override val settingsResetScale: String = "重置秤"
    override val settingsResetScaleSubtitle: String = "清除秤上全部用户与绑定"
    override val settingsPaired: String = "已配对"
    override val settingsConnected: String = "已连接"
    override val settingsUnpaired: String = "未配对"
    override val settingsGoPair: String = "去配对"
    override val settingsNoDeviceShort: String = "尚未配对秤"
    override val settingsDebugInfo: String = "调试信息"
    override val settingsModel: String = "型号"
    override val settingsSerial: String = "序列号"
    override val settingsConfigState: String = "配置状态"
    override val settingsRegisteredHuid: String = "已注册 HUID"
    override val settingsViewFrameLog: String = "查看帧日志"
    override val settingsFrameLogTitle: String = "帧日志"
}
