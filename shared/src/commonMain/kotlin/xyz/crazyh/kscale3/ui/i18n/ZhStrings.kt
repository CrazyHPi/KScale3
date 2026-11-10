package xyz.crazyh.kscale3.ui.i18n

/** 中文文案（默认语言；现有界面原文逐条迁移自此）。 */
object ZhStrings : AppStrings {

    // ---- 底部 Tab ----
    override val tabWeighIn: String = "称重"
    override val tabUsers: String = "用户"
    override val tabHistory: String = "历史"
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
    override val usersTitle: String = "用户管理"
    override val usersAdd: String = "添加用户"
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
    override val editNewTitle: String = "新建用户"
    override val editTitle: String = "编辑用户"
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
    override val weighTitle: String = "开始称重"
    override fun weighCurrentDevice(name: String): String = "当前配对设备：$name"
    override val weighNoDevice: String = "尚未配对设备——请先在「设备」页扫描配对。"
    override val weighGoPair: String = "去配对设备"
    override val weighSelectUser: String = "选择用户"
    override val weighUnregistered: String = "未注册（需先注册到秤）"
    override val weighStart: String = "开始称重（Mode 2）"
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
    override val settingsRegisteredUsers: String = "已注册用户"
    override val settingsNoRegisteredUsers: String = "暂无——可在「用户」页注册到秤。"
    override val settingsForgetDevice: String = "忘记已配对的秤"
    override val settingsFactoryResetting: String = "正在恢复出厂设置…"
    override val settingsFactoryReset: String = "秤恢复出厂设置"
    override val settingsLanguage: String = "语言"
    override val settingsDebugMode: String = "调试模式"
    override val settingsLogFramesOnly: String = "日志 仅显示帧 (hex)"
    override val settingsLogEmpty: String = "（暂无——发起一次称重/注册后显示）"
    override val settingsForgetDeviceTitle: String = "忘记已配对的秤？"
    override val settingsForgetDeviceMessage: String =
        "将清除本机保存的秤\n" +
                "再次连接需要重新配对\n" +
                "用户档案与历史体重不会被清除"

    override val settingsForgetConfirm: String = "忘记"
    override val settingsFactoryResetTitle: String = "秤恢复出厂设置？"
    override val settingsFactoryResetMessage: String =
        "将清除秤上的绑定与用户数据\n" +
                "本机配对也会被移除，需要重新配对并注册用户\n" +
                "本机用户档案与历史体重不会被清除"

    override val settingsFactoryResetConfirm: String = "恢复出厂设置"

    // ---- 秤显示单位 ----
    override val unitKg: String = "公斤"
    override val unitJin: String = "斤"
    override val unitLb: String = "磅"

    // ---- 语言选项 ----
    override val languageFollowSystem: String = "跟随系统"
    override val languageChinese: String = "中文"
    override val languageEnglish: String = "English"
    override fun languageFollowSystemValue(effectiveName: String): String =
        "跟随系统（$effectiveName）"
}
