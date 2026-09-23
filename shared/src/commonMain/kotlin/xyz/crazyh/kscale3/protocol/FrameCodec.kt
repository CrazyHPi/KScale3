package xyz.crazyh.kscale3.protocol

/**
 * 帧打包（TECH.md §3）：
 * ```
 * | Magic(1) | Length(1)=payload_len+3 | Seq(1)=((total-1)<<4)|index | Payload 0..15B | CRC-16 LE(2) |
 * ```
 * CRC 规则见 [Crc16]（⚠️ 覆盖含 CRC 字段预置 `00 00` 的完整帧）。
 */
object FrameCodec {
    /**
     * 把 [payload] 按 ≤15 字节分片打成完整帧字节序列（单帧时 total=1、Seq=0x00）。
     * 黄金向量：空载荷 0xDB → `db0300c140`；载荷 `01` 0xDB → `db04000120d9`。
     */
    fun packFrames(payload: ByteArray, magic: FrameMagic): List<ByteArray> =
        TODO("Phase 1: protocol implementation")
}

/**
 * 下行消息重组器：逐条 [feed] notify 原始字节，收满一条消息（含多帧重组）时返回 [Frame]。
 *
 * 必须校验 CRC / 长度 / 序号连续性并拒收坏包（参考实现 `FrameReassembler` 未校验 CRC，
 * KMP 版必须校验——PLAN「关键协议事实」）。
 */
class FrameReassembler {
    /** 尚未收满返回 null；CRC/长度/序号非法时的拒收语义由实现约定（抛 [SessionError.CrcError] 或丢弃）。 */
    fun feed(raw: ByteArray): Frame? = TODO("Phase 1: protocol implementation")
}
