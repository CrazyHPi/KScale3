package xyz.crazyh.kscale3.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import xyz.crazyh.kscale3.ble.BleAdvertisement
import xyz.crazyh.kscale3.di.AppContainer
import xyz.crazyh.kscale3.domain.MeasurementResult
import xyz.crazyh.kscale3.domain.PairedDevice
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.domain.WeightUnit
import xyz.crazyh.kscale3.domain.toDraft
import xyz.crazyh.kscale3.domain.toMeasurement
import xyz.crazyh.kscale3.isIosPlatform
import xyz.crazyh.kscale3.protocol.isValidMacAddress
import xyz.crazyh.kscale3.session.HardwareInfo
import xyz.crazyh.kscale3.session.PairingResult
import xyz.crazyh.kscale3.session.ScaleSession
import xyz.crazyh.kscale3.session.SessionException
import xyz.crazyh.kscale3.session.SessionOptions
import xyz.crazyh.kscale3.session.SessionState
import xyz.crazyh.kscale3.ui.errorText

/** Result 屏待保存的测量（归属用户 + 会话产物）。 */
data class PendingMeasurement(val userId: Long, val result: MeasurementResult)

/** 已配对设备读取状态：区分"库还没读到"与"确实没有设备"（前者不能直接判为未配对）。 */
sealed interface DeviceState {
    data object Loading : DeviceState
    data object None : DeviceState
    data class Found(val device: PairedDevice) : DeviceState
}

/**
 * 设备扫描/配对/称重/结果暂存的共享 ViewModel（DeviceScan/Users/WeighIn/Result 共用一个实例）。
 * 每次操作新建 ScaleSession（PLAN：每次称重/配对新建会话）。
 */
class SessionViewModel(private val container: AppContainer) : ViewModel() {

    /**
     * 已配对设备读取状态（Loading = 尚未读到首帧）。
     * 单一 Flow 原子切换，避免"已加载"与"设备值"分开发射时 App 以错误起点建导航图。
     */
    val deviceState: StateFlow<DeviceState> =
        container.deviceRepository.observe()
            .map { device -> if (device == null) DeviceState.None else DeviceState.Found(device) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, DeviceState.Loading)

    val device: StateFlow<PairedDevice?> =
        deviceState
            .map { (it as? DeviceState.Found)?.device }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val users: StateFlow<List<UserProfile>> =
        container.userRepository.getAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- 扫描 ----

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val _scanResults = MutableStateFlow<List<BleAdvertisement>>(emptyList())
    val scanResults: StateFlow<List<BleAdvertisement>> = _scanResults.asStateFlow()

    /** 收到的原始广播条数（诊断用：区分"没在扫/扫不到"与"被过滤"）。 */
    private val _adCount = MutableStateFlow(0)
    val adCount: StateFlow<Int> = _adCount.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (scanJob?.isActive == true) return
        _scanning.value = true
        _scanError.value = null
        _adCount.value = 0
        scanJob = viewModelScope.launch {
            try {
                // 不按名称过滤（名称可能为空/不符而误杀全部）——疑似秤排前，其余也显示
                container.createBleScanner().scan()
                    .collect { adv ->
                        _adCount.value = _adCount.value + 1
                        _scanResults.update { list ->
                            (list.filter { it.identifier != adv.identifier } + adv).sortedWith(
                                compareByDescending<BleAdvertisement> { it.isLikelyScale() }
                                    .thenByDescending { it.rssi },
                            )
                        }
                    }
            } catch (e: Exception) {
                // 权限缺失/蓝牙关闭等（Kable 会抛 IllegalStateException）——转为提示，不崩溃
                _scanError.value = e.message ?: "扫描失败（请检查蓝牙权限与开关）"
            } finally {
                _scanning.value = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    /**
     * 保存配对设备。
     * @param bleId 扫描到的连接标识（Android=MAC / iOS=CoreBluetooth UUID）；手填 MAC 未扫时用 mac 兜底。
     * @param macInput 手填 MAC（可空：Android 取扫描地址）；iOS 必填（秤身标签）。
     * @return false = MAC 非法/缺失。
     */
    fun saveDevice(name: String?, bleId: String?, macInput: String): Boolean {
        val typedMac = macInput.trim()
        val mac = when {
            typedMac.isValidMacAddress() -> typedMac
            typedMac.isEmpty() && bleId != null && bleId.isValidMacAddress() -> bleId
            else -> return false
        }
        // iOS 的连接标识是 CoreBluetooth UUID，不能拿 MAC 兜底（Uuid.parse 会失败）
        if (bleId == null && isIosPlatform()) return false
        val connectionId = bleId ?: mac
        // 设备变了：旧连接不再有意义，关闭后由下一次操作重新连接
        val newKey = "$connectionId|$mac"
        if (sessionKey != null && sessionKey != newKey) closeSession()
        viewModelScope.launch {
            container.deviceRepository.save(
                PairedDevice(
                    id = 0,
                    mac = mac,
                    bleId = connectionId,
                    name = name ?: "HUAWEI Scale 3",
                    model = null,
                    sn = null,
                    lastSeen = kotlin.time.Clock.System.now(),
                ),
            )
        }
        return true
    }

    /**
     * 忘记已配对的秤：仅清 `paired_device`（用户档案/HUID/测量记录保留）。
     * 纯本地操作，不需要连接：可打断进行中的单位查询/会话（[closeSession] 取消）。
     * 恢复出厂设置进行中不打断——它成功后自行清库并跳转。
     * 清空后 [deviceState] 变为 [DeviceState.None]，调用方跳回扫描配对页。
     */
    suspend fun forgetDevice() {
        if (_resetBusy.value) return
        closeSession()
        container.deviceRepository.clear()
    }

    /** 删除用户档案（连同其全部历史测量，同事务）；秤上已注册的 HUID 不受影响。 */
    suspend fun deleteUser(user: UserProfile) {
        if (_busy.value) return
        container.userRepository.delete(user.id)
        // 丢弃属于该用户的待保存结果，避免之后入库时引用已删除的 user_id
        if (_pendingResult.value?.userId == user.id) _pendingResult.value = null
    }

    // ---- 会话 ----

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _hardwareInfo = MutableStateFlow(HardwareInfo())
    val hardwareInfo: StateFlow<HardwareInfo> = _hardwareInfo.asStateFlow()

    private val _pendingResult = MutableStateFlow<PendingMeasurement?>(null)
    val pendingResult: StateFlow<PendingMeasurement?> = _pendingResult.asStateFlow()

    private val _frameLog = MutableStateFlow<List<String>>(emptyList())
    val frameLog: StateFlow<List<String>> = _frameLog.asStateFlow()

    /**
     * 持久会话：首次操作创建（连接+握手），之后复用；仅设备变化/忘记设备/VM 销毁时关闭。
     * [sessionKey] = bleId|mac，用于识别设备是否变化。
     */
    private var session: ScaleSession? = null
    private var sessionKey: String? = null

    /** Mode 2 日常称重。 */
    fun startWeighIn(user: UserProfile) = launchOperation { s, mac ->
        val result = s.measure(user, mac)
        _pendingResult.value = PendingMeasurement(user.id, result)
    }

    /** Mode 1 注册/重注册（重注册生成新 HUID，对照 Python --reconfig）。 */
    fun startRegistration(user: UserProfile) = launchOperation { s, mac ->
        when (val result = s.pair(user.toDraft(), mac)) {
            is PairingResult.Success -> {
                container.userRepository.markRegistered(user.id, result.huid, now())
                result.result?.let { m ->
                    container.measurementRepository.insert(m.toMeasurement(user.id, now()))
                }
            }

            is PairingResult.Failure -> println("[KScale3] session failed: ${result.error}") // SessionState.Failed 已带原因
        }
    }

    /**
     * 取消当前操作，但**保持连接**（连接与 WorkKey 留给下一次操作复用）。
     * 只有忘记设备/更换设备/VM 销毁才真正断连。
     */
    fun cancelSession() {
        session?.cancelOperation()
        // Cancel must dismiss the dialog even if the session already ended (activeJob == null).
        _sessionState.value = SessionState.Idle
    }

    override fun onCleared() {
        closeSession(resetInfo = false)
        super.onCleared()
    }

    // ---- 秤显示单位（TECH.md §7.3） ----

    /** 用户刚选择、尚未被会话确认的单位（乐观显示，操作结束后清空）。 */
    private val _pendingUnit = MutableStateFlow<WeightUnit?>(null)

    /**
     * 秤显示单位：用户选择优先（乐观反馈），其次实时值，最后数据库中的上次值。
     * null = 从未与秤通信过且库中无记录。
     */
    val weightUnit: StateFlow<WeightUnit?> =
        combine(deviceState, _hardwareInfo, _pendingUnit) { state, info, pending ->
            pending ?: info.weightUnit ?: (state as? DeviceState.Found)?.device?.weightUnit
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * 把会话捕获到的显示单位持久化（按 MAC 限定）。
     * 与操作开始时的设备快照相同则跳过；DB 失败只记日志——不因记账失败丢操作结果。
     */
    private suspend fun persistWeightUnit(device: PairedDevice, info: HardwareInfo) {
        val unit = info.weightUnit ?: return
        if (unit == device.weightUnit) return
        try {
            withContext(NonCancellable) {
                container.deviceRepository.updateWeightUnit(device.mac, unit)
            }
        } catch (e: Exception) {
            println("[KScale3] persist weight unit failed: ${e.message}")
        }
    }

    /** 单位读写的进行中标记（设置栏禁用 + 进度提示）。 */
    private val _unitBusy = MutableStateFlow(false)
    val unitBusy: StateFlow<Boolean> = _unitBusy.asStateFlow()

    private val _unitError = MutableStateFlow<String?>(null)
    val unitError: StateFlow<String?> = _unitError.asStateFlow()

    /** 恢复出厂设置的进行中标记/错误（独立于单位设置，错误显示在对应按钮下方）。 */
    private val _resetBusy = MutableStateFlow(false)
    val resetBusy: StateFlow<Boolean> = _resetBusy.asStateFlow()

    private val _resetError = MutableStateFlow<String?>(null)
    val resetError: StateFlow<String?> = _resetError.asStateFlow()

    /** 任一会话（称重/注册/单位读写/恢复出厂设置）进行中——UI 用它禁用入口，避免同一时刻两条 BLE 会话。 */
    val busyOrUnit: StateFlow<Boolean> =
        combine(_busy, _unitBusy, _resetBusy) { session, unit, reset -> session || unit || reset }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * 从秤读取当前显示单位（进入设置屏时调用；未配对/忙时忽略）。
     * 失败只落帧日志，不弹错误条——进屏自动查询不应因秤未开机而打扰用户。
     */
    fun refreshWeightUnit() {
        val device = device.value ?: return
        runUnitOperation(device, reportError = false) { it.readWeightUnit(device.mac) }
    }

    /** 设置秤显示单位（选中即乐观显示 [unit]；失败回落到原值并显示 [unitError]）。 */
    fun setWeightUnit(unit: WeightUnit) {
        val device = device.value ?: return
        if (_unitBusy.value || _busy.value || _resetBusy.value) return
        // 立即反馈所选单位：连接/握手/等待确认期间不再显示旧值
        _pendingUnit.value = unit
        runUnitOperation(device, reportError = true) { it.setWeightUnit(unit, device.mac) }
    }

    /**
     * 秤恢复出厂设置：成功后秤端绑定被清除，本机配对也一并移除并关闭会话；
     * 返回 true 时调用方跳回扫描配对页（与官方 App 行为一致）。
     * 失败只设置 [resetError]，不影响本机配对。
     */
    suspend fun factoryReset(): Boolean {
        if (_busy.value || _unitBusy.value || _resetBusy.value) return false
        val device = device.value ?: return false
        _resetBusy.value = true
        _resetError.value = null
        return try {
            val s = obtainSession(device)
            s.factoryReset(device.mac)
            closeSession()
            container.deviceRepository.clear()
            true
        } catch (e: SessionException) {
            _resetError.value = errorText(e.error)
            false
        } finally {
            _resetBusy.value = false
        }
    }

    private fun runUnitOperation(
        device: PairedDevice,
        reportError: Boolean,
        block: suspend (ScaleSession) -> Unit,
    ) {
        if (_unitBusy.value || _busy.value || _resetBusy.value) return
        _unitBusy.value = true
        _unitError.value = null
        viewModelScope.launch {
            val s = obtainSession(device)
            // 单位读写结果经 hardwareInfo 回传（与称重会话同一出口）
            val infoJob = launch { s.hardwareInfo.collect { _hardwareInfo.value = it } }
            try {
                block(s)
            } catch (e: SessionException) {
                if (reportError) _unitError.value = errorText(e.error)
                else println("[KScale3] unit read failed: ${e.message}")
            } finally {
                // 收集是异步的：先同步最后一次 hardwareInfo（单位结果），避免 cancel 时丢掉。
                // session === s 守卫：换设备/忘记设备已替换会话时，旧会话结果不覆盖新设备信息。
                val info = s.hardwareInfo.value
                if (session === s) {
                    _hardwareInfo.value = info
                    persistWeightUnit(device, info)
                }
                infoJob.cancel()
                // 操作结束：乐观值回落——成功时会话已写入所选单位，失败时显示原值 + 错误
                _pendingUnit.value = null
                _unitBusy.value = false
            }
        }
    }

    fun savePendingMeasurement() {
        val pending = _pendingResult.value ?: return
        viewModelScope.launch {
            container.measurementRepository.insert(
                pending.result.toMeasurement(
                    pending.userId,
                    now()
                )
            )
            _pendingResult.value = null
        }
    }

    /**
     * 取得设备的持久会话：同一设备复用（连接与 WorkKey 保持）；设备变化时关闭旧连接。
     * 连接本身由会话在首次操作时建立——不预连接（秤可能未唤醒）。
     */
    private fun obtainSession(device: PairedDevice): ScaleSession {
        val key = "${device.bleId}|${device.mac}"
        val current = session
        if (current != null && sessionKey == key) return current
        current?.close()
        return container.createScaleSession(
            container.createBlePeripheral(device.bleId),
            logOptions(),
        ).also {
            session = it
            sessionKey = key
        }
    }

    /** 关闭持久会话（断连）；默认同时清空设备信息（换设备/忘记设备）。 */
    private fun closeSession(resetInfo: Boolean = true) {
        session?.close()
        session = null
        sessionKey = null
        if (resetInfo) _hardwareInfo.value = HardwareInfo()
    }

    private fun launchOperation(block: suspend (ScaleSession, String) -> Unit) {
        if (_busy.value || _unitBusy.value || _resetBusy.value) return
        val device = device.value ?: return // 未配对设备：UI 层禁用入口
        // 帧日志进 VM（SettingsScreen 调试屏消费）+ println 落 logcat 便于真机诊断
        // （日志仅帧 hex——载荷均加密、MAC 不上线）
        val s = obtainSession(device)
        println("[KScale3] session start: mac=${device.mac} bleId=${device.bleId}")
        _busy.value = true
        _sessionState.value = SessionState.Idle
        viewModelScope.launch {
            val stateJob = launch { s.state.collect { _sessionState.value = it } }
            val infoJob = launch { s.hardwareInfo.collect { _hardwareInfo.value = it } }
            var cancelled = false
            try {
                block(s, device.mac)
            } catch (e: SessionException) {
                // 失败原因已在 SessionState.Failed；完整信息（拒收原因/步骤）落 logcat 诊断
                println("[KScale3] session failed: ${e.message}")
            } catch (e: CancellationException) {
                // 用户取消（cancelOperation）：只中止操作，连接保持；UI 回 Idle
                cancelled = true
                throw e
            } finally {
                // StateFlow 收集是异步的：先同步终态，避免 cancel 掉 collector 时丢掉
                // 最后一次 Failed/Paired 发射（真机表现为对话框永远停在 AwaitingMeasurement）。
                // 用户取消时协程已非 active——保持 Idle，勿把会话最后状态同步回来。
                _sessionState.value =
                    if (!cancelled && currentCoroutineContext().isActive) s.state.value
                    else SessionState.Idle
                // 重连自动查询到的单位同样先同步再取消收集器（session 已被替换时不回写）
                val info = s.hardwareInfo.value
                if (session === s) {
                    _hardwareInfo.value = info
                    persistWeightUnit(device, info)
                }
                stateJob.cancel()
                infoJob.cancel()
                _busy.value = false
            }
        }
    }

    private fun now(): Instant = kotlin.time.Clock.System.now()

    /** 会话选项：帧日志进 VM（调试屏）+ println 落 logcat（日志仅帧 hex，MAC 不上线）。 */
    private fun logOptions(): SessionOptions = SessionOptions(onLog = { line ->
        println("[KScale3] $line")
        _frameLog.update { (it + line).takeLast(FRAME_LOG_CAPACITY) }
    })

    private companion object {
        const val FRAME_LOG_CAPACITY: Int = 500
    }
}

/** 名称疑似华为秤（PLAN 过滤词）；只作排序/徽章提示，不隐藏其他设备。 */
fun BleAdvertisement.isLikelyScale(): Boolean =
    name?.let {
        Regex(
            "HaigeBLE|Scale 3|HUAWEI Scale",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(it)
    } == true
