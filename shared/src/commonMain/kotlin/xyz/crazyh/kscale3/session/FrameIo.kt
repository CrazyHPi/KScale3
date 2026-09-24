package xyz.crazyh.kscale3.session

import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import xyz.crazyh.kscale3.ble.BlePeripheral
import xyz.crazyh.kscale3.ble.GattUuids
import xyz.crazyh.kscale3.protocol.FeedResult
import xyz.crazyh.kscale3.protocol.Frame
import xyz.crazyh.kscale3.protocol.FrameCodec
import xyz.crazyh.kscale3.protocol.FrameMagic
import xyz.crazyh.kscale3.protocol.FrameReassembler
import xyz.crazyh.kscale3.protocol.RejectReason
import xyz.crazyh.kscale3.protocol.toHex

/**
 * 会话帧 I/O：按特征 UUID 一队列（一次 notify 一完整帧，无粘包），坏包 → [SessionError.CrcError]。
 * safeWrite 语义同 scale3.py L176：失败且报 permit/not allowed → 翻转 with-response 重试一次。
 * 订阅在 [start] 后常驻 [scope]（会话期间），不再像参考实现逐步启停。
 */
internal class FrameIo(
    private val peripheral: BlePeripheral,
    private val scope: CoroutineScope,
    private val onLog: ((String) -> Unit)?,
) {
    private sealed interface Inbound {
        data class Message(val frame: Frame) : Inbound
        data class Bad(val reason: RejectReason) : Inbound
    }

    private val queues = mutableMapOf<String, Channel<Inbound>>()

    /** Launch session-lifetime collectors; caller cancels the returned jobs on teardown. */
    fun start(): List<Job> {
        val jobs = mutableListOf<Job>()
        for (uuid in FRAMED_UUIDS) {
            val queue = Channel<Inbound>(Channel.BUFFERED)
            queues[uuid] = queue
            jobs += scope.launch {
                val reassembler = FrameReassembler()
                try {
                    peripheral.observe(uuid).collect { raw ->
                        onLog?.invoke("[RX $uuid] ${raw.toHex()}")
                        when (val r = reassembler.feed(raw)) {
                            is FeedResult.Complete -> queue.trySend(Inbound.Message(r.frame))
                            is FeedResult.Rejected -> queue.trySend(Inbound.Bad(r.reason))
                            is FeedResult.Incomplete -> Unit
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 订阅流失败（多为断连）→ 失败整个会话
                    throw SessionException(SessionError.Disconnected, cause = e)
                }
            }
        }
        for (uuid in LOG_ONLY_UUIDS) {
            jobs += scope.launch {
                try {
                    peripheral.observe(uuid).collect { raw -> onLog?.invoke("[RX $uuid] ${raw.toHex()}") }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 日志通道失败不致命
                }
            }
        }
        return jobs
    }

    suspend fun receive(uuid: String, step: String): Frame {
        return when (val inbound = queues.getValue(uuid).receive()) {
            is Inbound.Message -> inbound.frame
            is Inbound.Bad -> throw SessionException(
                SessionError.CrcError,
                "frame rejected on $uuid (${inbound.reason}) during $step",
            )
        }
    }

    suspend fun await(uuid: String, step: String, timeout: Duration): Frame =
        withTimeoutOrNull(timeout) { receive(uuid, step) }
            ?: throw SessionException(SessionError.Timeout(step))

    suspend fun awaitSoft(uuid: String, step: String, timeout: Duration): Frame? =
        withTimeoutOrNull(timeout) { receive(uuid, step) }

    suspend fun writeRaw(uuid: String, data: ByteArray, withResponse: Boolean = true) {
        safeWrite(uuid, data, withResponse)
    }

    suspend fun writeFrames(
        uuid: String,
        payload: ByteArray,
        magic: FrameMagic,
        interFrame: Duration = Duration.ZERO,
    ) {
        val frames = FrameCodec.packFrames(payload, magic)
        for ((i, frame) in frames.withIndex()) {
            safeWrite(uuid, frame, withResponse = true)
            if (i < frames.lastIndex && interFrame > Duration.ZERO) delay(interFrame)
        }
    }

    private suspend fun safeWrite(uuid: String, data: ByteArray, withResponse: Boolean) {
        onLog?.invoke("[TX $uuid] ${data.toHex()}")
        try {
            peripheral.write(uuid, data, withResponse)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message?.lowercase().orEmpty()
            if ("permit" in msg || "not allowed" in msg) {
                onLog?.invoke("[TX $uuid] retry withResponse=${!withResponse}")
                try {
                    peripheral.write(uuid, data, !withResponse)
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    throw wrapWrite(e2)
                }
            } else {
                throw wrapWrite(e)
            }
        }
    }

    /** 写失败映射：连接类 → Disconnected；其余原样抛（编程错误不掩盖）。 */
    private fun wrapWrite(e: Exception): Exception {
        if (isDisconnectLike(e)) return SessionException(SessionError.Disconnected, cause = e)
        return e
    }

    private fun isDisconnectLike(e: Exception): Boolean {
        val msg = e.message?.lowercase().orEmpty()
        return "not connected" in msg ||
            "disconnect" in msg ||
            e::class.simpleName?.contains("NotConnected") == true
    }

    private companion object {
        val FRAMED_UUIDS = listOf(
            GattUuids.AUTH_REQ,
            GattUuids.AUTH_TOKENS,
            GattUuids.WORK_KEY,
            GattUuids.BINDING,
            GattUuids.HUID_TARE,
            GattUuids.USER_PROFILE,
            GattUuids.TIME_SYNC,
            GattUuids.SERIAL,
            GattUuids.CONFIG,
            GattUuids.MODEL,
            GattUuids.OFFLINE_HISTORY,
            GattUuids.BIA,
        )

        val LOG_ONLY_UUIDS = listOf(
            GattUuids.SENTINEL,
            GattUuids.CAPABILITIES_RSP,
        )
    }
}
