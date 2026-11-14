package xyz.crazyh.kscale3.session

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
import kotlin.time.Duration

/**
 * 会话帧 I/O：按特征 UUID 一队列（一次 notify 一完整帧，无粘包），坏包 → [SessionError.CrcError]。
 * safeWrite 语义同 scale3.py L176：失败且报 permit/not allowed → 翻转 with-response 重试一次。
 * 订阅在 [start] 后常驻 [scope]（一次操作期间，结束时由调用方取消）；BLE 连接本身跨操作保持。
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

    /** 每个 framed 特征的订阅就绪信号（Kable `onSubscription` 回调）。 */
    private val subscribed = Channel<String>(Channel.BUFFERED)

    /** Launch session-lifetime collectors; caller cancels the returned jobs on teardown. */
    fun start(): List<Job> {
        val jobs = mutableListOf<Job>()
        for (uuid in FRAMED_UUIDS) {
            val queue = Channel<Inbound>(Channel.BUFFERED)
            queues[uuid] = queue
            jobs += scope.launch {
                val reassembler = FrameReassembler()
                try {
                    peripheral.observe(uuid) { subscribed.trySend(uuid) }.collect { raw ->
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
                    // 可选特征（如单位查询）订阅失败不致命：对应步骤超时即可
                    if (uuid in ESSENTIAL_UUIDS) {
                        // 订阅流失败（多为断连）→ 失败整个会话
                        throw SessionException(SessionError.Disconnected, cause = e)
                    }
                    onLog?.invoke("[RX $uuid] subscribe failed (optional, ignored): ${e.message}")
                }
            }
        }
        for (uuid in LOG_ONLY_UUIDS) {
            jobs += scope.launch {
                try {
                    peripheral.observe(uuid)
                        .collect { raw -> onLog?.invoke("[RX $uuid] ${raw.toHex()}") }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 日志通道失败不致命
                }
            }
        }
        return jobs
    }

    /**
     * 等待 [FRAMED_UUIDS] 的 CCCD 订阅全部就绪；超时返回 false。
     * 复用连接时订阅是异步建立的：必须等它就绪再发首个写入，否则秤的即时应答
     * （如单位设置的 Indication `BD 04 00 00`）会在订阅生效前丢失。
     * 可选特征订阅失败不应无限阻塞——超时后调用方继续，对应步骤按超时处理。
     */
    suspend fun awaitSubscriptions(timeout: Duration): Boolean {
        val ready = mutableSetOf<String>()
        val allReady = withTimeoutOrNull(timeout) {
            while (ready.size < FRAMED_UUIDS.size) {
                ready += subscribed.receive()
            }
            true
        } ?: false
        // 真机诊断：iOS 上若通知未开启，表现为只有 TX 没有 RX——先看这行。
        val missing = FRAMED_UUIDS - ready
        onLog?.invoke(
            if (missing.isEmpty()) {
                "[SUB] ${FRAMED_UUIDS.size}/${FRAMED_UUIDS.size} characteristics subscribed"
            } else {
                "[SUB] ${ready.size}/${FRAMED_UUIDS.size} subscribed; missing=${missing.joinToString()}"
            },
        )
        return allReady
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
            GattUuids.WEIGHT_UNIT_QUERY,
            GattUuids.FACTORY_RESET,
            GattUuids.CONFIG,
            GattUuids.MODEL,
            GattUuids.OFFLINE_HISTORY,
            GattUuids.BIA,
        )

        val LOG_ONLY_UUIDS = listOf(
            GattUuids.SENTINEL,
            GattUuids.CAPABILITIES_RSP,
        )

        /** 订阅失败即判定断连的特征；其余（如单位查询/恢复出厂设置）失败只记日志。 */
        val ESSENTIAL_UUIDS =
            FRAMED_UUIDS.toSet() - setOf(GattUuids.WEIGHT_UNIT_QUERY, GattUuids.FACTORY_RESET)
    }
}
