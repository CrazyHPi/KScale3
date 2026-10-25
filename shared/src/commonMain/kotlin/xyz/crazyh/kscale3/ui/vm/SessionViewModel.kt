package xyz.crazyh.kscale3.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import xyz.crazyh.kscale3.ble.BleAdvertisement
import xyz.crazyh.kscale3.di.AppContainer
import xyz.crazyh.kscale3.domain.MeasurementResult
import xyz.crazyh.kscale3.domain.PairedDevice
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.domain.toDraft
import xyz.crazyh.kscale3.domain.toMeasurement
import xyz.crazyh.kscale3.protocol.isValidMacAddress
import xyz.crazyh.kscale3.session.HardwareInfo
import xyz.crazyh.kscale3.session.PairingResult
import xyz.crazyh.kscale3.session.ScaleSession
import xyz.crazyh.kscale3.session.SessionException
import xyz.crazyh.kscale3.session.SessionOptions
import xyz.crazyh.kscale3.session.SessionState

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
        val connectionId = bleId ?: mac
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

    private var session: ScaleSession? = null

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

    fun cancelSession() {
        session?.close()
        session = null
        // Cancel must dismiss the dialog even if the session already ended (activeJob == null).
        _sessionState.value = SessionState.Idle
    }

    /** Result 屏保存入库。 */
    fun savePendingMeasurement() {
        val pending = _pendingResult.value ?: return
        viewModelScope.launch {
            container.measurementRepository.insert(pending.result.toMeasurement(pending.userId, now()))
            _pendingResult.value = null
        }
    }

    private fun launchOperation(block: suspend (ScaleSession, String) -> Unit) {
        if (_busy.value) return
        val device = device.value ?: return // 未配对设备：UI 层禁用入口
        // 帧日志进 VM（SettingsScreen 调试屏消费）+ println 落 logcat 便于真机诊断
        // （日志仅帧 hex——载荷均加密、MAC 不上线）
        val options = SessionOptions(onLog = { line ->
            println("[KScale3] $line")
            _frameLog.update { (it + line).takeLast(FRAME_LOG_CAPACITY) }
        })
        val s = container.createScaleSession(container.createBlePeripheral(device.bleId), options)
        session = s
        println("[KScale3] session start: mac=${device.mac} bleId=${device.bleId}")
        _busy.value = true
        viewModelScope.launch {
            val stateJob = launch { s.state.collect { _sessionState.value = it } }
            val infoJob = launch { s.hardwareInfo.collect { _hardwareInfo.value = it } }
            try {
                block(s, device.mac)
            } catch (e: SessionException) {
                // 失败原因已在 SessionState.Failed；完整信息（拒收原因/步骤）落 logcat 诊断
                println("[KScale3] session failed: ${e.message}")
            } finally {
                // StateFlow 收集是异步的：先同步终态，避免 cancel 掉 collector 时丢掉
                // 最后一次 Failed/Paired 发射（真机表现为对话框永远停在 AwaitingMeasurement）。
                // 用户取消时协程已非 active——保持 Idle，勿把会话最后状态同步回来。
                _sessionState.value = if (currentCoroutineContext().isActive) s.state.value else SessionState.Idle
                stateJob.cancel()
                infoJob.cancel()
                _busy.value = false
            }
        }
    }

    private fun now(): Instant = kotlin.time.Clock.System.now()

    private companion object {
        const val FRAME_LOG_CAPACITY: Int = 500
    }
}

/** 名称疑似华为秤（PLAN 过滤词）；只作排序/徽章提示，不隐藏其他设备。 */
fun BleAdvertisement.isLikelyScale(): Boolean =
    name?.let { Regex("HaigeBLE|Scale 3|HUAWEI Scale", RegexOption.IGNORE_CASE).containsMatchIn(it) } == true
