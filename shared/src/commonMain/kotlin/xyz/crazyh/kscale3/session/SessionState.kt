package xyz.crazyh.kscale3.session

import xyz.crazyh.kscale3.domain.MeasurementResult

/**
 * 称重/配对会话状态机状态（PLAN「ScaleSession 状态机」）。
 * 全部步骤暴露为 StateFlow 供 UI 显示引导文案。
 */
sealed interface SessionState {
    data object Connecting : SessionState

    /** 公共握手：0x21/0x25/0x29/0xD7。 */
    data object Handshaking : SessionState

    /** Mode 1：提示用户上秤验证称重（25s 超时，0x2D 回皮重）。 */
    data object AwaitingTare : SessionState

    data object SyncingTime : SessionState

    data object SendingProfile : SessionState

    /** 0x31 首次回复：code 0=识别 ✓；1=缺验证称重→引导注册；2=不匹配→换人/重注册。 */
    data class ProfileResponded(val code: Int) : SessionState

    /** 0x97 武装后等 38B BIA 帧（120s 超时）。 */
    data object AwaitingMeasurement : SessionState

    /** 0x31 提交 type=2。 */
    data object Committing : SessionState

    data class Done(val result: MeasurementResult) : SessionState

    data class Failed(val error: SessionError) : SessionState
}
