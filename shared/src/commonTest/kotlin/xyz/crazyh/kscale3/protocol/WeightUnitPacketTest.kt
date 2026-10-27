package xyz.crazyh.kscale3.protocol

import xyz.crazyh.kscale3.domain.WeightUnit
import xyz.crazyh.kscale3.protocol.packets.WeightUnitPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/** 体重单位帧黄金向量（TECH.md §7.3：官方 App HCI 抓包实测）。 */
class WeightUnitPacketTest {

    @Test
    fun setFrame_goldenVectors() {
        assertEquals("db04000120d9", WeightUnitPacket.setFrame(1).toHex())
        assertEquals("db0400027080", WeightUnitPacket.setFrame(2).toHex())
        assertEquals("db04000340b7", WeightUnitPacket.setFrame(3).toHex())
    }

    @Test
    fun setFrame_matchesEnumCodes() {
        assertEquals(1, WeightUnit.Kg.code)
        assertEquals(2, WeightUnit.Lb.code)
        assertEquals(3, WeightUnit.Jin.code)
        assertEquals(WeightUnit.Kg, WeightUnit.fromCode(1))
        assertEquals(WeightUnit.Lb, WeightUnit.fromCode(2))
        assertEquals(WeightUnit.Jin, WeightUnit.fromCode(3))
        assertNull(WeightUnit.fromCode(0))
        assertNull(WeightUnit.fromCode(4))
    }

    @Test
    fun setFrame_rejectsUnknownCode() {
        assertFailsWith<IllegalArgumentException> { WeightUnitPacket.setFrame(0) }
        assertFailsWith<IllegalArgumentException> { WeightUnitPacket.setFrame(4) }
    }

    @Test
    fun replyCode_readsFirstPayloadByte() {
        // 设置确认 BD 04 00 00 / 查询回包 BD 04 00 0X（载荷=单位码）
        assertEquals(0, WeightUnitPacket.replyCode(byteArrayOf(0x00)))
        assertEquals(3, WeightUnitPacket.replyCode(byteArrayOf(0x03)))
        assertNull(WeightUnitPacket.replyCode(ByteArray(0)))
    }

    @Test
    fun documentedDownstreamReplies_areWellFormed() {
        // TECH.md §7.3 回包：BD 04 00 <unit> <CRC16_LE>（下行 MODBUS）
        val replies = mapOf(
            0 to "bd0400006589",
            1 to "bd040001a449",
            2 to "bd040002e448",
            3 to "bd0400032588",
        )
        replies.forEach { (code, hex) ->
            val raw = hex.hexToBytes()
            val stored = raw.getU16Le(raw.size - 2)
            assertEquals(stored, Crc16.computeDownstream(raw.copyOfRange(0, raw.size - 2)))
            val complete = assertIs<FeedResult.Complete>(FrameReassembler().feed(raw))
            assertEquals(code, WeightUnitPacket.replyCode(complete.frame.payload))
        }
    }
}
