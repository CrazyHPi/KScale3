package xyz.crazyh.kscale3.session

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import xyz.crazyh.kscale3.ble.BlePeripheral
import xyz.crazyh.kscale3.ble.GattUuids
import xyz.crazyh.kscale3.protocol.CrcRule
import xyz.crazyh.kscale3.protocol.FeedResult
import xyz.crazyh.kscale3.protocol.FrameCodec
import xyz.crazyh.kscale3.protocol.FrameMagic
import xyz.crazyh.kscale3.protocol.FrameReassembler
import xyz.crazyh.kscale3.protocol.HexFixtures
import xyz.crazyh.kscale3.protocol.ProtocolConstants
import xyz.crazyh.kscale3.protocol.ScaleCrypto
import xyz.crazyh.kscale3.protocol.hexToBytes
import xyz.crazyh.kscale3.protocol.packets.UserProfilePacket
import xyz.crazyh.kscale3.protocol.putU16Le
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Test MAC (golden RootKey vector: d0d01fae597b65147ee89b03e26dd8ec). */
internal const val TEST_MAC: String = "AA:BB:CC:DD:EE:FF"

/** Scriptable BlePeripheral: records writes, delivers notifies, can simulate disconnect. */
internal class FakePeripheral : BlePeripheral {

    /** iOS-like UUID identifier: must NOT be used as RootKey input. */
    override val identifier: String = "00000000-0000-0000-0000-00000000f00d"

    override val name: String? = "HaigeBLE-Scale3"

    private val connected = MutableStateFlow(false)

    override val isConnected: StateFlow<Boolean> get() = connected

    data class Write(val uuid: String, val data: ByteArray, val withResponse: Boolean)

    /** Every write() call including failed attempts (safeWrite flip assertions). */
    val attempts = mutableListOf<Write>()

    private val writeChannel = Channel<Write>(Channel.UNLIMITED)

    /** Failure injector: return an exception to throw instead of accepting the write. */
    var onWrite: ((Write) -> Exception?)? = null

    private val inflows = mutableMapOf<String, MutableSharedFlow<ByteArray>>()

    /** Number of successful connect() calls (persistent-link assertions). */
    var connectCount = 0
        private set

    override suspend fun connect() {
        connectCount++
        connected.value = true
    }

    override suspend fun disconnect() {
        connected.value = false
    }

    override suspend fun write(uuid: String, data: ByteArray, withResponse: Boolean) {
        if (!connected.value) throw Exception("not connected")
        val write = Write(uuid, data.copyOf(), withResponse)
        attempts += write
        val failure = onWrite?.invoke(write)
        if (failure != null) throw failure
        writeChannel.send(write)
    }

    /** Gate for observe() subscription callbacks (regression: first write must wait for CCCD). */
    var subscriptionGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override fun observe(uuid: String, onSubscribed: (() -> Unit)?): Flow<ByteArray> = flow {
        // Fake 无 CCCD：开始收集即视为订阅就绪；gate 用于测试"写入前必须等订阅"
        subscriptionGate?.await()
        onSubscribed?.invoke()
        emitAll(inflow(uuid))
    }

    private fun inflow(uuid: String): MutableSharedFlow<ByteArray> =
        inflows.getOrPut(uuid) { MutableSharedFlow(extraBufferCapacity = 256) }

    /** Suspend until the session performs the next successful write. */
    suspend fun awaitWrite(): Write = writeChannel.receive()

    /** Push one raw notify frame. */
    fun notify(uuid: String, raw: ByteArray) {
        check(inflow(uuid).tryEmit(raw)) { "notify buffer full" }
    }

    fun simulateDisconnect() {
        connected.value = false
    }
}

/**
 * Fake scale bot: drives the device side of a session (handshake crypto, canned replies).
 * All frame building goes through the real FrameCodec/CRC so wire format stays honest.
 */
internal class TestScale(
    val peripheral: FakePeripheral = FakePeripheral(),
    val mac: String = TEST_MAC,
    val randA: ByteArray = HexFixtures.RAND_A,
) {
    val rootKey: ByteArray = ScaleCrypto.deriveRootKey(mac)
    lateinit var workKey: ByteArray
        private set
    lateinit var randB: ByteArray
        private set

    /** Handshake up to (and including) the tokenA reply: 0x21 + 0x25. For the tokenA-mismatch scenario. */
    suspend fun handshakeTokenAOnly(tokenAOverride: ByteArray? = null) {
        val authReq = peripheral.awaitWrite()
        assertEquals(GattUuids.AUTH_REQ, authReq.uuid)
        assertContentEquals(ProtocolConstants.QUERY_ARM, authReq.data)
        sendFrames(GattUuids.AUTH_REQ, randA, FrameMagic.PlainDownstream)

        val authPayload = collectFrames(GattUuids.AUTH_TOKENS, expectedFrames = 4)
        assertEquals(48, authPayload.size)
        randB = authPayload.copyOfRange(0, 16)
        val tokenB = authPayload.copyOfRange(16, 48)
        assertContentEquals(ScaleCrypto.generateTokenB(randA, randB), tokenB)
        sendFrames(
            GattUuids.AUTH_TOKENS,
            tokenAOverride ?: ScaleCrypto.generateTokenA(randA, randB),
            FrameMagic.PlainDownstream,
        )
    }

    /**
     * Drive the common handshake (0x21/0x25/0x29/0xD7). [tokenAOverride] injects a wrong tokenA;
     * [workKeyCode] != 0 makes the session abort before the 0xD7 write.
     */
    suspend fun handshake(tokenAOverride: ByteArray? = null, workKeyCode: Int = 0) {
        handshakeTokenAOnly(tokenAOverride)

        val wire = collectFrames(GattUuids.WORK_KEY, expectedFrames = 3)
        workKey = ScaleCrypto.decryptAesCtr(rootKey, wire)
        assertEquals(16, workKey.size)
        peripheral.notify(GattUuids.WORK_KEY, statusReply(workKeyCode))
        if (workKeyCode != 0) return // session aborts before 0xD7

        val cap = peripheral.awaitWrite()
        assertEquals(GattUuids.CAPABILITIES, cap.uuid)
        assertContentEquals(ProtocolConstants.CAPABILITY_BYTES, cap.data)
        assertEquals(false, cap.withResponse)
    }

    /** Consume [expectedFrames] writes to [uuid] and reassemble the message payload. */
    suspend fun collectFrames(uuid: String, expectedFrames: Int): ByteArray {
        val raws = List(expectedFrames) {
            val write = peripheral.awaitWrite()
            assertEquals(uuid, write.uuid)
            write.data
        }
        return reassemble(raws)
    }

    fun sendFrames(uuid: String, payload: ByteArray, magic: FrameMagic) {
        // 假秤=下行方向：真机实测下行 CRC=MODBUS（CrcRule.Downstream）
        FrameCodec.packFrames(payload, magic, CrcRule.Downstream)
            .forEach { peripheral.notify(uuid, it) }
    }

    fun sendStatus(uuid: String, code: Int) {
        peripheral.notify(uuid, statusReply(code))
    }

    /** AES(WorkKey) encrypt + 0xCD downstream frames (0x2D tare / 0x97 BIA replies). */
    fun sendEncrypted(uuid: String, plain: ByteArray) {
        sendFrames(uuid, ScaleCrypto.encryptAesCtr(workKey, plain), FrameMagic.EncryptedDownstream)
    }

    /** Expect one raw QUERY_ARM write to [uuid]. */
    suspend fun awaitQuery(uuid: String) {
        val write = peripheral.awaitWrite()
        assertEquals(uuid, write.uuid)
        assertContentEquals(ProtocolConstants.QUERY_ARM, write.data)
    }

    /** Expect the fixed-time 0x52 frame (golden vector) and ack it. */
    suspend fun awaitTimeSyncAndAck() {
        val write = peripheral.awaitWrite()
        assertEquals(GattUuids.TIME_SYNC, write.uuid)
        assertContentEquals("db0b00ea0709160e1e050266ff".hexToBytes(), write.data)
        sendStatus(GattUuids.TIME_SYNC, 0)
    }

    /** Consume one 0x31 profile/commit message (6 frames) and return the decrypted packet. */
    suspend fun awaitProfilePacket(): UserProfilePacket {
        val wire = collectFrames(GattUuids.USER_PROFILE, expectedFrames = 6)
        return UserProfilePacket.decode(ScaleCrypto.decryptAesCtr(workKey, wire))
    }

    /** Consume the 0x31 profile message and reply with [code]. */
    suspend fun profileExchange(code: Int = 0): UserProfilePacket {
        val packet = awaitProfilePacket()
        sendStatus(GattUuids.USER_PROFILE, code)
        return packet
    }

    /** Reply to a unit query (`BD 04 00 <code>`) on [uuid] (downstream MODBUS CRC). */
    fun sendUnitReply(uuid: String, code: Int) {
        sendFrames(uuid, byteArrayOf(code.toByte()), FrameMagic.PlainDownstream)
    }
}

// ---- frame / payload builders ----

internal fun statusReply(code: Int): ByteArray =
    FrameCodec.packFrames(
        byteArrayOf(code.toByte()),
        FrameMagic.PlainDownstream,
        CrcRule.Downstream
    ).single()

internal fun reassemble(raws: List<ByteArray>): ByteArray {
    // 主机→假秤方向：上行 XMODEM CRC
    val reassembler = FrameReassembler(CrcRule.Upstream)
    var out: ByteArray? = null
    for (raw in raws) {
        when (val result = reassembler.feed(raw)) {
            is FeedResult.Complete -> out = result.frame.payload
            is FeedResult.Rejected -> error("frame rejected: ${result.reason}")
            is FeedResult.Incomplete -> Unit
        }
    }
    return out ?: error("incomplete message")
}

/**
 * 0x97 BIA 38B plaintext builder. Defaults reproduce the golden vector
 * `521cd700ea0709160e1e0502...` (72.50kg / 21.5% / 2026-09-22 14:30:05 dow=2).
 */
internal fun biaPayload(
    weightDg: Int = 7250,
    fatDg: Int = 215,
    year: Int = 2026,
    month: Int = 9,
    day: Int = 22,
    hour: Int = 14,
    minute: Int = 30,
    second: Int = 5,
    dow: Int = 2,
    feet: List<Int> = listOf(256, 257, 258, 259, 260, 261),
    heartRate: Int = 65,
    hands: List<Int> = listOf(512, 513, 514, 515, 516, 517),
): ByteArray {
    val out = ByteArray(38)
    out.putU16Le(0, weightDg)
    out.putU16Le(2, fatDg)
    out.putU16Le(4, year)
    out[6] = month.toByte()
    out[7] = day.toByte()
    out[8] = hour.toByte()
    out[9] = minute.toByte()
    out[10] = second.toByte()
    out[11] = dow.toByte()
    feet.forEachIndexed { i, v -> out.putU16Le(12 + 2 * i, v) }
    out.putU16Le(24, heartRate)
    hands.forEachIndexed { i, v -> out.putU16Le(26 + 2 * i, v) }
    return out
}

/** 0x2D tare reply plaintext (`005a1800` = status 0 / 62.34kg golden). */
internal fun tarePayload(status: Int = 0, weightDg: Int = 6234): ByteArray {
    val out = ByteArray(4)
    out[0] = status.toByte()
    out.putU16Le(1, weightDg)
    out[3] = 0
    return out
}
