package xyz.crazyh.kscale3.session

/** 会话失败原因（PLAN「ScaleSession 状态机」）。 */
sealed interface SessionError {
    /** tokenB 错（→ 0x31 持续 Code 2）。 */
    data object TokenMismatch : SessionError

    /** 未知用户 / Code 2。 */
    data object UserNotRecognised : SessionError

    /** Code 1：档案已收但缺验证称重。 */
    data object MissingTare : SessionError

    /** 某步 notify 超时。[step] 为步骤名（如 "randA"、"tare"、"bia"）。 */
    data class Timeout(val step: String) : SessionError

    data object Disconnected : SessionError

    /** 帧 CRC/长度/序号校验失败。 */
    data object CrcError : SessionError

    /** MAC 格式非法或疑似错误（BIA 解出不合理值时提示）。 */
    data object MacInvalid : SessionError
}
