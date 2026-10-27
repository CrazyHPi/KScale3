package xyz.crazyh.kscale3.protocol.packets

import xyz.crazyh.kscale3.protocol.FrameCodec
import xyz.crazyh.kscale3.protocol.FrameMagic

/**
 * 体重单位设置帧（TECH.md §7.3）：向 0x5A 写 `DB 04 00 <unit> <CRC16_LE>`（无响应写）。
 * 黄金向量：kg `db04000120d9` / lb `db0400027080` / jin `db04000340b7`。
 *
 * 查询命令是通用空载荷 `db0300c140`（[xyz.crazyh.kscale3.protocol.ProtocolConstants.QUERY_ARM]），
 * 写到单位查询特征；回包 `BD 04 00 <code>`：设置确认 code=0，查询回单位码 1/2/3。
 */
object WeightUnitPacket {

    /** 单位码（1=kg / 2=lb / 3=jin）→ 设置帧（单帧明文上行）。 */
    fun setFrame(code: Int): ByteArray {
        require(code in 1..3) { "unit code out of 1..3: $code" }
        return FrameCodec.packFrames(byteArrayOf(code.toByte()), FrameMagic.PlainUpstream).single()
    }

    /** 回包载荷首字节（0=设置确认；1/2/3=单位码）；空载荷返回 null。 */
    fun replyCode(payload: ByteArray): Int? = payload.firstOrNull()?.toInt()?.and(0xFF)
}
