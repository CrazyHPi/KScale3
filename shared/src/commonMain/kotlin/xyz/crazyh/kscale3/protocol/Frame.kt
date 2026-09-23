package xyz.crazyh.kscale3.protocol

/**
 * 重组完成的单条协议消息（仅载荷与魔数；帧头/CRC 在 [FrameReassembler] 消化）。
 * 0x97 BIA 流按 magic 决定是否解密（0xCD 解密 / 0xBD 明文直接用，SUMMARY §9）。
 */
data class Frame(
    val payload: ByteArray,
    val magic: FrameMagic,
)
