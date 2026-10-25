package xyz.crazyh.kscale3.protocol

/**
 * 帧打包（TECH.md §3）：
 * ```
 * | Magic(1) | Length(1)=payload_len+3 | Seq(1)=((total-1)<<4)|index | Payload 0..15B | CRC-16 LE(2) |
 * ```
 * ⚠️ CRC **按方向分两套规则**（见 [Crc16]）：上行 [CrcRule.Upstream]（XMODEM，含 0000 占位整帧）；
 * 下行 [CrcRule.Downstream]（MODBUS，仅 CRC 前字节）——真机实测，勿混用。
 */
object FrameCodec {

    /**
     * 把 [payload] 按 ≤15 字节分片打成完整帧字节序列（单帧时 total=1、Seq=0x00）。
     * 边界：空载荷=1 帧；15B=1 帧；16B=2 帧(0x10/0x11)；240B=16 帧(0xF0..0xFF)；>240B 拒绝
     *（Seq 的 total-1 仅 4 bit，超限拒绝而非静默截断 nibble）。
     *
     * [crcRule] 默认 [CrcRule.Upstream]（主机→秤）；[CrcRule.Downstream] 供测试/Fake 秤造下行帧。
     * 黄金向量（Upstream）：空载荷 0xDB → `db0300c140`；载荷 `01` 0xDB → `db04000120d9`。
     */
    fun packFrames(
        payload: ByteArray,
        magic: FrameMagic,
        crcRule: CrcRule = CrcRule.Upstream,
    ): List<ByteArray> {
        require(payload.size <= ProtocolConstants.MAX_PAYLOAD_TOTAL) {
            "payload ${payload.size}B exceeds ${ProtocolConstants.MAX_PAYLOAD_TOTAL}B (15x16, seq nibble limit)"
        }
        val max = ProtocolConstants.MAX_PAYLOAD_PER_FRAME
        val total = if (payload.isEmpty()) 1 else (payload.size + max - 1) / max
        return List(total) { i ->
            val start = i * max
            val end = minOf(start + max, payload.size)
            val chunk = if (payload.isEmpty()) ByteArray(0) else payload.copyOfRange(start, end)
            val frame = ByteArray(chunk.size + 5)
            frame[0] = magic.byte.toByte()
            frame[1] = (chunk.size + 3).toByte()
            frame[2] = ((((total - 1) and 0x0F) shl 4) or (i and 0x0F)).toByte()
            chunk.copyInto(frame, 3)
            val crc = when (crcRule) {
                // 尾两字节保持 00 00（CRC 占位——XMODEM 覆盖范围含此占位）
                CrcRule.Upstream -> Crc16.compute(frame)
                // MODBUS 仅覆盖 CRC 前的字节
                CrcRule.Downstream -> Crc16.computeDownstream(frame.copyOfRange(0, frame.size - 2))
            }
            frame[frame.size - 2] = (crc and 0xFF).toByte()
            frame[frame.size - 1] = ((crc ushr 8) and 0xFF).toByte()
            frame
        }
    }
}

/** CRC 规则按帧方向区分（[Crc16] KDoc 有真实字节证据）。 */
enum class CrcRule {
    /** 主机→秤打包：XMODEM over 带 `00 00` 占位的整帧。 */
    Upstream,

    /** 秤→主机校验/测试打包：MODBUS over CRC 前字节。 */
    Downstream,
}

/** [FrameReassembler.feed] 结果。 */
sealed interface FeedResult {
    /** 尚未收满一条完整消息。 */
    data object Incomplete : FeedResult

    /** 收满并重组完成。 */
    data class Complete(val frame: Frame) : FeedResult

    /** 坏包被拒收（重组状态已清空，[session] 层映射 SessionError.CrcError 等）。 */
    data class Rejected(val reason: RejectReason) : FeedResult
}

/** 拒收原因。 */
enum class RejectReason {
    /** 帧不足 5 字节 */
    TooShort,

    /** 首字节非已知魔数 */
    BadMagic,

    /** Length 字段 < 3（payload 长度为负） */
    LengthTooSmall,

    /** Length 字段与实际 notify 长度不符 */
    LengthMismatch,

    /** 单帧 payload > 15B */
    PayloadOverLimit,

    /** CRC 校验失败 */
    BadCrc,

    /** Seq：index >= total */
    SeqOutOfRange,

    /** Seq：组内跳号 / total 与进行中组不符 */
    SeqOutOfOrder,

    /** 组内 magic 不一致 */
    MagicMismatch,

    /** 续帧（index>0）到达时无进行中的组 */
    ContinuationWithoutStart,
}

/**
 * 下行消息重组器：逐帧 [feed] notify 原始字节（**一次 notify 一完整帧**，无粘包），
 * 收满一条消息（含多帧重组）时返回 [FeedResult.Complete]。
 *
 * 与参考实现（scale3.py `FrameReassembler`）的差异：①校验 CRC/长度/序号（Python 全不查）；
 * ②组 magic 取**首帧**（Python 取末帧；真流量全组同 magic，无行为差）；
 * ③坏包返回 [FeedResult.Rejected] 并清空重组状态（Python 静默返回 null）。
 *
 * CRC 校验默认 [CrcRule.Downstream]（真机下行规则；⚠️ 勿改为上行规则——会拒收一切回包）。
 * 测试重组**模拟上行**流量（Fake 秤收集主机帧）时传 [CrcRule.Upstream]。
 */
class FrameReassembler(private val crcRule: CrcRule = CrcRule.Downstream) {
    private var groupMagic: FrameMagic? = null
    private var groupTotal: Int = 0
    private var nextIndex: Int = 0
    private var buffer: ByteArray = ByteArray(0)

    /** 清空半包状态（会话超时/丢弃半条消息时调用）。 */
    fun reset() {
        groupMagic = null
        groupTotal = 0
        nextIndex = 0
        buffer = ByteArray(0)
    }

    fun feed(raw: ByteArray): FeedResult {
        if (raw.size < 5) return reject(RejectReason.TooShort)
        val magic = FrameMagic.fromByte(raw[0].toInt()) ?: return reject(RejectReason.BadMagic)

        val lengthField = raw[1].toInt() and 0xFF
        if (lengthField < 3) return reject(RejectReason.LengthTooSmall)
        val payloadLen = lengthField - 3
        if (payloadLen > ProtocolConstants.MAX_PAYLOAD_PER_FRAME) return reject(RejectReason.PayloadOverLimit)
        if (raw.size != lengthField + 2) return reject(RejectReason.LengthMismatch)

        // CRC 校验（规则按方向，见 Crc16/CrcRule）：与小端存留值比对
        val expected = raw.getU16Le(raw.size - 2)
        val body = raw.copyOfRange(0, raw.size - 2)
        val actual = when (crcRule) {
            CrcRule.Upstream -> Crc16.compute(
                raw.copyOf().also { it[it.size - 2] = 0; it[it.size - 1] = 0 })

            CrcRule.Downstream -> Crc16.computeDownstream(body)
        }
        if (actual != expected) return reject(RejectReason.BadCrc)

        val seq = raw[2].toInt() and 0xFF
        val index = seq and 0x0F
        val total = (seq ushr 4) + 1
        if (index >= total) return reject(RejectReason.SeqOutOfRange)

        if (index == 0) {
            // 开新组（丢弃未完成旧组）；组 magic 取首帧
            groupMagic = magic
            groupTotal = total
            nextIndex = 1
            buffer = raw.copyOfRange(3, raw.size - 2)
        } else {
            if (groupMagic == null) return reject(RejectReason.ContinuationWithoutStart)
            if (total != groupTotal) return reject(RejectReason.SeqOutOfOrder)
            if (magic != groupMagic) return reject(RejectReason.MagicMismatch)
            if (index != nextIndex) return reject(RejectReason.SeqOutOfOrder)
            buffer += raw.copyOfRange(3, raw.size - 2)
            nextIndex = index + 1
        }

        if (index + 1 < total) return FeedResult.Incomplete
        val payload = buffer
        val resultMagic = groupMagic!!
        reset()
        return FeedResult.Complete(Frame(payload, resultMagic))
    }

    private fun reject(reason: RejectReason): FeedResult {
        reset()
        return FeedResult.Rejected(reason)
    }
}
