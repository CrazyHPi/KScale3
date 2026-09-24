package xyz.crazyh.kscale3.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
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

/**
 * 设备扫描/配对/称重/结果暂存的共享 ViewModel（DeviceScan/Users/WeighIn/Result 共用一个实例）。
 * 每次操作新建 ScaleSession（PLAN：每次称重/配对新建会话）。
 */
class SessionViewModel(private val container: AppContainer) : ViewModel() {

    val device: StateFlow<PairedDevice?> =
        container.deviceRepository.observe()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val users: StateFlow<List<UserProfile>> =
        container.userRepository.getAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- 扫描 ----

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _scanResults = MutableStateFlow<List<BleAdvertisement>>(emptyList())
    val scanResults: StateFlow<List<BleAdvertisement>> = _scanResults.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (scanJob?.isActive == true) return
        _scanning.value = true
        scanJob = viewModelScope.launch {
            try {
                container.createBleScanner().scan()
                    .filter { adv -> adv.name?.let { SCALE_NAME_FILTER.containsMatchIn(it) } == true }
                    .collect { adv ->
                        _scanResults.update { list ->
                            (list.filter { it.identifier != adv.identifier } + adv).sortedByDescending { it.rssi }
                        }
                    }
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

            is PairingResult.Failure -> Unit // SessionState.Failed 已带原因
        }
    }

    fun cancelSession() {
        session?.close()
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
        // 帧日志进 VM（SettingsScreen 调试屏消费）：日志仅帧 hex——载荷均加密、MAC 不上线
        val options = SessionOptions(onLog = { line ->
            _frameLog.update { (it + line).takeLast(FRAME_LOG_CAPACITY) }
        })
        val s = container.createScaleSession(container.createBlePeripheral(device.bleId), options)
        session = s
        _busy.value = true
        viewModelScope.launch {
            val stateJob = launch { s.state.collect { _sessionState.value = it } }
            val infoJob = launch { s.hardwareInfo.collect { _hardwareInfo.value = it } }
            try {
                block(s, device.mac)
            } catch (_: SessionException) {
                // 失败原因已在 SessionState.Failed
            } finally {
                stateJob.cancel()
                infoJob.cancel()
                _busy.value = false
            }
        }
    }

    private fun now(): Instant = kotlin.time.Clock.System.now()

    private companion object {
        val SCALE_NAME_FILTER = Regex("HaigeBLE|Scale 3|HUAWEI Scale", RegexOption.IGNORE_CASE)
        const val FRAME_LOG_CAPACITY: Int = 500
    }
}
