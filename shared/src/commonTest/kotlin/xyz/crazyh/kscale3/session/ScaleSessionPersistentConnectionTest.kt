package xyz.crazyh.kscale3.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import xyz.crazyh.kscale3.ble.GattUuids
import xyz.crazyh.kscale3.domain.Sex
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.domain.WeightUnit
import xyz.crazyh.kscale3.protocol.FrameMagic
import xyz.crazyh.kscale3.protocol.packets.TimeSyncPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * 持久连接（2026-10-29）：首次操作 connect + 握手，操作结束后保持连接与 WorkKey；
 * 后续操作直接复用；秤主动断开后下一次操作重连 + 重新握手。
 */
class ScaleSessionPersistentConnectionTest {

    private val user = UserProfile(
        id = 1,
        name = "tester",
        sex = Sex.Male,
        age = 30,
        heightCm = 175,
        goalWeightKg = null,
        huid = "30033000012345678",
        registeredAt = Instant.fromEpochMilliseconds(0),
        createdAt = Instant.fromEpochMilliseconds(0),
        lastWeightKg = 62.34,
    )

    private fun options() = SessionOptions(
        timeProvider = { TimeSyncPacket(2026, 9, 22, 14, 30, 5, 2) },
    )

    @Test
    fun measure_keepsConnectionAndReusesWorkKeyOnNextOperation() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())

        val first = async { session.measure(user, TEST_MAC) }
        scale.handshake()
        scale.completeRoutineMeasurement()
        first.await()

        assertTrue(scale.peripheral.isConnected.value, "操作结束后应保持连接")
        assertEquals(1, scale.peripheral.connectCount)
        assertEquals(1, scale.peripheral.attempts.count { it.uuid == GattUuids.AUTH_REQ })

        // 第二次操作：不握手，直接走 0x52 → 0x31 → … → 0x97 → 0x31 提交
        val second = async { session.measure(user, TEST_MAC) }
        scale.completeRoutineMeasurement()
        second.await()

        assertEquals(1, scale.peripheral.connectCount, "复用连接不应重连")
        assertEquals(
            1,
            scale.peripheral.attempts.count { it.uuid == GattUuids.AUTH_REQ },
            "复用连接不应重新握手",
        )
    }

    @Test
    fun newConnection_queriesUnitOnce_reconnectQueriesAgain() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())

        // 首次连接（新握手）→ 自动查询一次显示单位
        val first = async { session.measure(user, TEST_MAC) }
        scale.handshake(replyUnit = 1) // 自动查询回公斤
        scale.completeRoutineMeasurement()
        first.await()

        assertEquals(1, scale.peripheral.attempts.count { it.uuid == GattUuids.WEIGHT_UNIT_QUERY })
        assertEquals(WeightUnit.Kg, session.hardwareInfo.value.weightUnit)

        // 复用连接：不重复查询
        val second = async { session.measure(user, TEST_MAC) }
        scale.completeRoutineMeasurement()
        second.await()
        assertEquals(1, scale.peripheral.attempts.count { it.uuid == GattUuids.WEIGHT_UNIT_QUERY })

        // 秤主动断开 → 下一次操作重连 + 重新握手 → 再查询一次
        scale.peripheral.simulateDisconnect()
        val third = async { session.measure(user, TEST_MAC) }
        scale.handshake(replyUnit = 3) // 自动查询回斤
        scale.completeRoutineMeasurement()
        third.await()

        assertEquals(2, scale.peripheral.attempts.count { it.uuid == GattUuids.WEIGHT_UNIT_QUERY })
        assertEquals(WeightUnit.Jin, session.hardwareInfo.value.weightUnit)
    }

    @Test
    fun scaleDisconnect_thenNextOperationReconnectsAndHandshakes() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())

        val first = async { session.measure(user, TEST_MAC) }
        scale.handshake()
        scale.completeRoutineMeasurement()
        first.await()

        // 秤主动断开（操作已结束，无 watcher 在跑）：下一次操作应重连 + 重新握手
        scale.peripheral.simulateDisconnect()

        val second = async { session.measure(user, TEST_MAC) }
        scale.handshake()
        scale.completeRoutineMeasurement()
        second.await()

        assertEquals(2, scale.peripheral.connectCount)
        assertEquals(2, scale.peripheral.attempts.count { it.uuid == GattUuids.AUTH_REQ })
    }

    @Test
    fun cancelOperation_keepsConnectionForNextOperation() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())

        val first = async { runCatching { session.measure(user, TEST_MAC) } }
        scale.handshake()
        scale.awaitTimeSyncAndAck()
        scale.profileExchange(code = 0)
        scale.awaitQuery(GattUuids.MODEL)
        scale.sendFrames(GattUuids.MODEL, "M00F7".encodeToByteArray(), FrameMagic.PlainDownstream)
        scale.awaitQuery(GattUuids.OFFLINE_HISTORY)
        scale.sendFrames(GattUuids.OFFLINE_HISTORY, byteArrayOf(0x01), FrameMagic.PlainDownstream)
        scale.awaitProfilePacket()
        scale.sendStatus(GattUuids.USER_PROFILE, 0)
        scale.awaitQuery(GattUuids.BIA)

        // 会话正在等 BIA：取消当前操作（不是关闭会话）
        session.cancelOperation()

        assertIs<CancellationException>(first.await().exceptionOrNull())
        assertTrue(scale.peripheral.isConnected.value, "取消操作不应断连")

        // 下一次操作复用连接（不再握手）
        val second = async { session.measure(user, TEST_MAC) }
        scale.completeRoutineMeasurement()
        second.await()

        assertEquals(1, scale.peripheral.connectCount)
        assertEquals(1, scale.peripheral.attempts.count { it.uuid == GattUuids.AUTH_REQ })
    }

    @Test
    fun close_disconnectsAndRejectsFurtherOperations() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())

        val job = async { session.measure(user, TEST_MAC) }
        scale.handshake()
        scale.completeRoutineMeasurement()
        job.await()
        assertTrue(scale.peripheral.isConnected.value)

        session.close()
        assertEquals(false, scale.peripheral.isConnected.value, "close 应主动断连")

        val error = assertFailsWith<SessionException> {
            session.measure(user, TEST_MAC)
        }
        assertEquals(SessionError.Disconnected, error.error)
    }

    @Test
    fun awaitSubscriptions_blocksUntilCccdCallbacks() = runTest {
        // 回归：复用连接时首个写入必须等订阅（CCCD）就绪，否则单位设置的 Indication 会丢
        val peripheral = FakePeripheral()
        val gate = CompletableDeferred<Unit>()
        peripheral.subscriptionGate = gate
        val io = FrameIo(peripheral, this, null)
        val jobs = io.start()
        try {
            val ready = async { io.awaitSubscriptions(1.seconds) }
            advanceTimeBy(500)
            assertFalse(ready.isCompleted, "订阅未就绪时不应返回")
            gate.complete(Unit)
            assertTrue(ready.await(), "订阅全部就绪后应返回 true")
        } finally {
            jobs.forEach { it.cancel() }
        }
    }

    @Test
    fun disconnectAfterBiaCaptured_keepsMeasurementResult() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())

        val job = async { session.measure(user, TEST_MAC) }
        scale.handshake()
        scale.awaitTimeSyncAndAck()
        scale.profileExchange(code = 0)
        scale.awaitQuery(GattUuids.MODEL)
        scale.sendFrames(GattUuids.MODEL, "M00F7".encodeToByteArray(), FrameMagic.PlainDownstream)
        scale.awaitQuery(GattUuids.OFFLINE_HISTORY)
        scale.sendFrames(GattUuids.OFFLINE_HISTORY, byteArrayOf(0x01), FrameMagic.PlainDownstream)
        scale.awaitProfilePacket()
        scale.sendStatus(GattUuids.USER_PROFILE, 0)
        scale.awaitQuery(GattUuids.BIA)
        scale.sendEncrypted(GattUuids.BIA, biaPayload())

        // 提交已写出 => BIA 结果已捕获；秤在收尾静置期间断连，结果不应丢失
        scale.awaitProfilePacket()
        scale.peripheral.simulateDisconnect()

        val result = job.await()
        assertEquals(72.50, result.weightKg)
        assertEquals(21.5, result.fatPct)
    }
}

/** 常规 Mode 2 的秤端脚本（不含握手）：0x52 → 0x31 → 查询 → 刷新 → 0x97 → 0x31 提交。 */
private suspend fun TestScale.completeRoutineMeasurement() {
    awaitTimeSyncAndAck()

    profileExchange(code = 0)

    awaitQuery(GattUuids.MODEL)
    sendFrames(GattUuids.MODEL, "M00F7".encodeToByteArray(), FrameMagic.PlainDownstream)
    awaitQuery(GattUuids.OFFLINE_HISTORY)
    sendFrames(GattUuids.OFFLINE_HISTORY, byteArrayOf(0x01), FrameMagic.PlainDownstream)

    awaitProfilePacket() // 0x31 刷新
    sendStatus(GattUuids.USER_PROFILE, 0)

    awaitQuery(GattUuids.BIA)
    sendEncrypted(GattUuids.BIA, biaPayload())
    awaitProfilePacket() // 0x31 提交 type=2
}
