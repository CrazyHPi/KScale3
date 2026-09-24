package xyz.crazyh.kscale3.session

/** 会话失败原因（PLAN「ScaleSession 状态机」）。 */
sealed interface SessionError {
    /** tokenA 校验失败（tokenB 公式/实现有误，或线上 tokenA 与 §2.3 不符）。 */
    data object TokenMismatch : SessionError

    /** 未知用户 / 0x31 Code 2（tokenB 错或 HUID 不识别；⚠️ RootKey 错（MAC 填错）时也会先在这里暴露）。 */
    data object UserNotRecognised : SessionError

    /** Code 1：档案已收但缺验证称重。 */
    data object MissingTare : SessionError

    /** 某步 notify 超时。[step] 为步骤名（如 "randA"、"tare"、"bia"）。 */
    data class Timeout(val step: String) : SessionError

    data object Disconnected : SessionError

    /** 帧 CRC/长度/序号校验失败（[xyz.crazyh.kscale3.protocol.FeedResult.Rejected] → 此）。 */
    data object CrcError : SessionError

    /** MAC 格式非法，或 BIA 解出不合理值（疑似 MAC 错 → RootKey 错 → 解密乱码）。 */
    data object MacInvalid : SessionError

    /** 设备回了无法识别的状态/载荷（[code] 为短状态码，无法给出时为 null）。 */
    data class UnexpectedReply(val step: String, val code: Int? = null) : SessionError
}

/**
 * 会话内部错误载体：[ScaleSession.measure] 直接抛出；[ScaleSession.pair] 转成
 * [PairingResult.Failure]。
 */
class SessionException(
    val error: SessionError,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: error.toString(), cause)
