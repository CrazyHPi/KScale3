package xyz.crazyh.kscale3.protocol.packets

/**
 * 0x2D 皮重回复（AES(WorkKey) 解密后）：`[status u8][tare u16le /100kg][pad]`。
 * 向量：`005a1800` → status=0、62.34kg。
 * Mode 1 验证称重捕获的皮重是后续 0x31 拿 Code 0 的前提（25s 超时）。
 */
data class TareResponse(
    val status: Int,
    val weightKg: Double,
) {
    companion object {
        fun decode(decrypted: ByteArray): TareResponse = TODO("Phase 1: protocol implementation")
    }
}
