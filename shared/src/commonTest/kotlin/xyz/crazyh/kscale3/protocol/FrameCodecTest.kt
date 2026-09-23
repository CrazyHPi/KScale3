package xyz.crazyh.kscale3.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** 黄金向量 3/4 + 分帧边界 + 重组器负例全表（PLAN「测试与验证」）。 */
class FrameCodecTest {

    // ---- 打包（向量 3/4）----

    @Test
    fun packFrames_emptyPayload_db() {
        val frames = FrameCodec.packFrames(ByteArray(0), FrameMagic.PlainUpstream)
        assertEquals(1, frames.size)
        assertEquals("db0300c140", frames[0].toHex())
        assertContentEquals(ProtocolConstants.QUERY_ARM, frames[0])
    }

    @Test
    fun packFrames_singleBytePayload_db() {
        val frames = FrameCodec.packFrames(byteArrayOf(0x01), FrameMagic.PlainUpstream)
        assertEquals(1, frames.size)
        assertEquals("db04000120d9", frames[0].toHex())
    }

    @Test
    fun packFrames_timeSyncPayload_db() {
        val payload = "ea0709160e1e0502".hexToBytes() // 2026-09-22 14:30:05 周二
        val frames = FrameCodec.packFrames(payload, FrameMagic.PlainUpstream)
        assertEquals(1, frames.size)
        assertEquals("db0b00ea0709160e1e050266ff", frames[0].toHex())
    }

    @Test
    fun packFrames_38BytePayload_cd_threeFrames() {
        val frames = FrameCodec.packFrames(HexFixtures.PAYLOAD_38, FrameMagic.EncryptedDownstream)
        assertEquals(3, frames.size)
        assertEquals("cd1220303132333435363738393a3b3c3d3e1159", frames[0].toHex())
        assertEquals("cd12213f404142434445464748494a4b4c4df104", frames[1].toHex())
        assertEquals("cd0b224e4f5051525354552baf", frames[2].toHex())
    }

    @Test
    fun packFrames_boundaries() {
        // 15B → 1 帧 seq 0x00
        assertEquals(1, FrameCodec.packFrames(ByteArray(15), FrameMagic.PlainUpstream).size)
        // 16B → 2 帧 seq 0x10/0x11
        val two = FrameCodec.packFrames(ByteArray(16), FrameMagic.PlainUpstream)
        assertEquals(2, two.size)
        assertEquals(0x10, two[0][2].toInt() and 0xFF)
        assertEquals(0x11, two[1][2].toInt() and 0xFF)
        // 240B → 16 帧末 seq 0xFF
        val many = FrameCodec.packFrames(ByteArray(240), FrameMagic.PlainUpstream)
        assertEquals(16, many.size)
        assertEquals(0xFF, many[15][2].toInt() and 0xFF)
        // 241B → 拒绝（seq nibble 上限）
        assertFailsWith<IllegalArgumentException> {
            FrameCodec.packFrames(ByteArray(241), FrameMagic.PlainUpstream)
        }
    }

    // ---- 重组（往返）----

    @Test
    fun reassembler_roundTrip_threeFrames() {
        val r = FrameReassembler()
        val frames = FrameCodec.packFrames(HexFixtures.PAYLOAD_38, FrameMagic.EncryptedDownstream)
        assertIs<FeedResult.Incomplete>(r.feed(frames[0]))
        assertIs<FeedResult.Incomplete>(r.feed(frames[1]))
        val result = assertIs<FeedResult.Complete>(r.feed(frames[2]))
        assertContentEquals(HexFixtures.PAYLOAD_38, result.frame.payload)
        assertEquals(FrameMagic.EncryptedDownstream, result.frame.magic)
    }

    @Test
    fun reassembler_singleFrame_immediatelyComplete() {
        val r = FrameReassembler()
        val result = assertIs<FeedResult.Complete>(r.feed("db0300c140".hexToBytes()))
        assertContentEquals(ByteArray(0), result.frame.payload)
        assertEquals(FrameMagic.PlainUpstream, result.frame.magic)
    }

    // ---- 重组（负例全表）----

    @Test
    fun reassembler_rejects_badCrc() {
        val r = FrameReassembler()
        val frame = "db0300c140".hexToBytes()
        frame[4] = (frame[4].toInt() xor 0x01).toByte() // 翻转 CRC 低位
        val result = assertIs<FeedResult.Rejected>(r.feed(frame))
        assertEquals(RejectReason.BadCrc, result.reason)
    }

    @Test
    fun reassembler_rejects_tooShort() {
        val result = assertIs<FeedResult.Rejected>(FrameReassembler().feed(ByteArray(4)))
        assertEquals(RejectReason.TooShort, result.reason)
    }

    @Test
    fun reassembler_rejects_badMagic() {
        val frame = "db0300c140".hexToBytes()
        frame[0] = 0x00
        val result = assertIs<FeedResult.Rejected>(FrameReassembler().feed(frame))
        assertEquals(RejectReason.BadMagic, result.reason)
    }

    @Test
    fun reassembler_rejects_lengthBelow3() {
        // lengthField=2 → payload 长度为负（参考实现负切片静默空载荷——此处拒绝）
        val result = assertIs<FeedResult.Rejected>(
            FrameReassembler().feed(byteArrayOf(0xDB.toByte(), 2, 0, 0, 0)),
        )
        assertEquals(RejectReason.LengthTooSmall, result.reason)
    }

    @Test
    fun reassembler_rejects_lengthMismatch() {
        val frame = "db0300c140".hexToBytes() + byteArrayOf(0x00) // 多 1 字节
        val result = assertIs<FeedResult.Rejected>(FrameReassembler().feed(frame))
        assertEquals(RejectReason.LengthMismatch, result.reason)
    }

    @Test
    fun reassembler_rejects_payloadOverLimit() {
        // lengthField=19 → payload 16B > 15B
        val raw = ByteArray(21) { 0 }
        raw[0] = 0xDB.toByte()
        raw[1] = 19
        val result = assertIs<FeedResult.Rejected>(FrameReassembler().feed(raw))
        assertEquals(RejectReason.PayloadOverLimit, result.reason)
    }

    @Test
    fun reassembler_rejects_seqOutOfRange() {
        // seq=0x05 → total=1, index=5
        val result = assertIs<FeedResult.Rejected>(
            FrameReassembler().feed(buildRawFrame(0xDB, 0x05, ByteArray(0))),
        )
        assertEquals(RejectReason.SeqOutOfRange, result.reason)
    }

    @Test
    fun reassembler_rejects_seqOutOfOrder() {
        val r = FrameReassembler()
        assertIs<FeedResult.Incomplete>(r.feed(buildRawFrame(0xCD, 0x20, ByteArray(15)))) // total=3, index=0
        // index=2 但期望 1 → 跳号
        val result = assertIs<FeedResult.Rejected>(r.feed(buildRawFrame(0xCD, 0x22, ByteArray(15))))
        assertEquals(RejectReason.SeqOutOfOrder, result.reason)
    }

    @Test
    fun reassembler_rejects_totalMismatch() {
        val r = FrameReassembler()
        assertIs<FeedResult.Incomplete>(r.feed(buildRawFrame(0xCD, 0x20, ByteArray(15)))) // total=3
        // total=2 与进行中组不符
        val result = assertIs<FeedResult.Rejected>(r.feed(buildRawFrame(0xCD, 0x11, ByteArray(15))))
        assertEquals(RejectReason.SeqOutOfOrder, result.reason)
    }

    @Test
    fun reassembler_rejects_magicMismatch() {
        val r = FrameReassembler()
        assertIs<FeedResult.Incomplete>(r.feed(buildRawFrame(0xDB, 0x10, ByteArray(15))))
        val result = assertIs<FeedResult.Rejected>(r.feed(buildRawFrame(0xBD, 0x11, ByteArray(15))))
        assertEquals(RejectReason.MagicMismatch, result.reason)
    }

    @Test
    fun reassembler_rejects_continuationWithoutStart() {
        val result = assertIs<FeedResult.Rejected>(
            FrameReassembler().feed(buildRawFrame(0xDB, 0x11, ByteArray(15))),
        )
        assertEquals(RejectReason.ContinuationWithoutStart, result.reason)
    }

    @Test
    fun reassembler_recovery_afterReject() {
        val r = FrameReassembler()
        // 半个组后坏包 → 状态清空
        assertIs<FeedResult.Incomplete>(r.feed(buildRawFrame(0xCD, 0x10, HexFixtures.PAYLOAD_38.copyOfRange(0, 15))))
        assertIs<FeedResult.Rejected>(r.feed(ByteArray(4)))
        // 之后合法单帧可正常完成
        val result = assertIs<FeedResult.Complete>(r.feed("db0300c140".hexToBytes()))
        assertContentEquals(ByteArray(0), result.frame.payload)
    }

    /** 手工构帧（CRC 占位 00 00 计算后写回），用于负例注入。 */
    private fun buildRawFrame(magic: Int, seq: Int, payload: ByteArray): ByteArray {
        val frame = ByteArray(payload.size + 5)
        frame[0] = magic.toByte()
        frame[1] = (payload.size + 3).toByte()
        frame[2] = seq.toByte()
        payload.copyInto(frame, 3)
        val crc = Crc16.compute(frame)
        frame[frame.size - 2] = (crc and 0xFF).toByte()
        frame[frame.size - 1] = ((crc ushr 8) and 0xFF).toByte()
        return frame
    }
}
