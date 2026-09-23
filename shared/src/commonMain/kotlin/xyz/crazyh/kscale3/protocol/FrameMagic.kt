package xyz.crazyh.kscale3.protocol

/**
 * 帧魔数（TECH.md §3）。上行 = 主机→秤，下行 = 秤→主机。
 */
enum class FrameMagic(val byte: Int) {
    /** 0xDB 明文上行 */
    PlainUpstream(0xDB),

    /** 0xDC 加密上行 */
    EncryptedUpstream(0xDC),

    /** 0xBD 明文下行 */
    PlainDownstream(0xBD),

    /** 0xCD 加密下行 */
    EncryptedDownstream(0xCD),
}
