package xyz.crazyh.kscale3.session

import kotlinx.coroutines.flow.StateFlow
import xyz.crazyh.kscale3.domain.MeasurementResult
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.domain.UserProfileDraft
import xyz.crazyh.kscale3.domain.WeightUnit
import xyz.crazyh.kscale3.protocol.MacAddress

/**
 * 秤会话契约（PLAN「ScaleSession 状态机」）。
 * 订阅常驻整个会话过程（不像参考实现逐步启停）；pair/measure 为互斥的一次完整会话。
 * Mode 1 约 40s（含验证称重）；Mode 2 约 25s。
 */
interface ScaleSession {
    /** 全部步骤状态，供 UI 显示引导文案。 */
    val state: StateFlow<SessionState>

    /** 查询捕获的设备信息（0x5A SN / 0x7A 型号 / 0x62 配置码 / 0x8F 原始历史，供 SettingsScreen）。 */
    val hardwareInfo: StateFlow<HardwareInfo>

    /**
     * Mode 1：首次配对/写闪存（0x45 武装 → 0x2D HUID → 验证称重 → … → 0x97 BIA → 0x31 提交）。
     * HUID 在内部生成；失败返回 [PairingResult.Failure]（不抛）。
     * 注册成功后 BIA 尾段失败仍算成功（HUID 已写入），此时 [PairingResult.Success.result] 为 null
     * ——与参考实现"配对即保存配置"一致。
     *
     * @param mac 真实物理 MAC（RootKey 输入，见 [xyz.crazyh.kscale3.protocol.ScaleCrypto.deriveRootKey]）。
     */
    suspend fun pair(user: UserProfileDraft, mac: MacAddress): PairingResult

    /**
     * Mode 2：日常称重（0x31 识别 → … → 0x97 BIA → 0x31 提交）。
     * 要求 [UserProfile.huid] 已注册，否则抛 [IllegalArgumentException]；
     * 会话失败抛 [SessionException]（[SessionException.error] 即 [SessionState.Failed] 的原因）。
     *
     * @param mac 真实物理 MAC（RootKey 输入）。
     */
    suspend fun measure(user: UserProfile, mac: MacAddress): MeasurementResult

    /**
     * 读取秤当前显示单位（TECH.md §7.3：向 `7e6dbc73` 写通用查询，回 `BD 04 00 <unit>`）。
     * 需连接（内部完成握手）；失败抛 [SessionException]。
     */
    suspend fun readWeightUnit(mac: MacAddress): WeightUnit

    /**
     * 设置秤显示单位（TECH.md §7.3：向 0x5A 写 `DB 04 00 <unit>`，等 status 0 确认）。
     * 成功后 [hardwareInfo] 的 [HardwareInfo.weightUnit] 即为 [unit]；失败抛 [SessionException]。
     */
    suspend fun setWeightUnit(unit: WeightUnit, mac: MacAddress)

    /** 中止进行中的会话（取消 pair/measure 协程并断连）。 */
    fun close()
}

/** Mode 1 配对结果。 */
sealed interface PairingResult {
    /**
     * 注册成功：HUID 已写入秤闪存并拿到 0x31 Code 0。
     * [result] 为配对流程尾段的 BIA 测量（可入库）；BIA 超时/失败时为 null（注册仍算成功）。
     */
    data class Success(val huid: String, val result: MeasurementResult? = null) : PairingResult

    data class Failure(val error: SessionError) : PairingResult
}

/** 查询步骤捕获的设备信息（0x8F 历史格式未知，[historyRaw] 留 hex 原文备用）。 */
data class HardwareInfo(
    /** 0x5A 序列号 */
    val serial: String? = null,
    /** 0x7A 硬件型号（如 M00F7） */
    val model: String? = null,
    /** 0x62 配置状态码（实测恒 1） */
    val configCode: Int? = null,
    /** 0x8F 离线历史回复（hex 原文，v1 不解码） */
    val historyRaw: String? = null,
    /** 秤当前显示单位（0x5A / 单位查询；仅设置或查询单位后才有值） */
    val weightUnit: WeightUnit? = null,
)
