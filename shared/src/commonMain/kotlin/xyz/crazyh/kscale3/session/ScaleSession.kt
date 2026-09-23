package xyz.crazyh.kscale3.session

import kotlinx.coroutines.flow.StateFlow
import xyz.crazyh.kscale3.domain.MeasurementResult
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.domain.UserProfileDraft
import xyz.crazyh.kscale3.protocol.MacAddress

/**
 * 秤会话契约（PLAN「ScaleSession 状态机」）。
 * 内部用 CompletableDeferred 对应参考实现的各 asyncio.Event；订阅常驻会话期间。
 * Mode 1 约 40s（含验证称重）；Mode 2 约 25s。
 */
interface ScaleSession {
    /** 全部步骤状态，供 UI 显示引导文案。 */
    val state: StateFlow<SessionState>

    /** Mode 1：首次配对/写闪存（0x45 武装 → 0x2D HUID → 验证称重 → … → 持久化 HUID）。 */
    suspend fun pair(user: UserProfileDraft, mac: MacAddress): PairingResult

    /** Mode 2：日常称重（0x31 识别 → … → 0x97 BIA → 0x31 提交）。 */
    suspend fun measure(user: UserProfile): MeasurementResult

    fun close()
}

/** Mode 1 配对结果。 */
sealed interface PairingResult {
    data class Success(val huid: String) : PairingResult
    data class Failure(val error: SessionError) : PairingResult
}
