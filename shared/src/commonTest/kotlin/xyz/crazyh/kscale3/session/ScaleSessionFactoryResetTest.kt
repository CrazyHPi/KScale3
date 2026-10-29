package xyz.crazyh.kscale3.session

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import xyz.crazyh.kscale3.ble.GattUuids
import xyz.crazyh.kscale3.protocol.ProtocolConstants
import xyz.crazyh.kscale3.protocol.packets.TimeSyncPacket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 恢复出厂设置（TECH.md §7.4）：握手 + 向 4599778a 写通用空载荷 `db0300c140`。 */
class ScaleSessionFactoryResetTest {

    private fun options() =
        SessionOptions(timeProvider = { TimeSyncPacket(2026, 10, 30, 1, 30, 2, 5) })

    @Test
    fun factoryReset_writesQueryArmToResetCharacteristicAndWaitsStatus0() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { session.factoryReset(TEST_MAC) }

        scale.handshake(autoQueryUnit = false)

        // 复位帧与查询帧字节相同；语义由目标特征决定
        val write = scale.peripheral.awaitWrite()
        assertEquals(GattUuids.FACTORY_RESET, write.uuid)
        assertContentEquals(ProtocolConstants.FACTORY_RESET, write.data)
        assertContentEquals(ProtocolConstants.QUERY_ARM, write.data)
        assertEquals(false, write.withResponse)

        scale.sendStatus(GattUuids.FACTORY_RESET, 0) // BD 04 00 00
        job.await()

        assertEquals(SessionState.Idle, session.state.value) // 轻量操作不残留称重屏状态
        assertEquals(true, scale.peripheral.isConnected.value) // 会话不断连，由调用方 close()
    }

    @Test
    fun factoryReset_nonZeroStatus_fails() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.factoryReset(TEST_MAC) } }

        scale.handshake(autoQueryUnit = false)
        scale.peripheral.awaitWrite()
        scale.sendStatus(GattUuids.FACTORY_RESET, 1)

        val error = assertIs<SessionException>(job.await().exceptionOrNull())
        assertEquals(SessionError.UnexpectedReply("factoryReset", 1), error.error)
    }

    @Test
    fun factoryReset_noReply_timesOut() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.factoryReset(TEST_MAC) } }

        scale.handshake(autoQueryUnit = false)
        scale.peripheral.awaitWrite()
        // 秤不回确认 → 3s 虚拟超时

        val error = assertIs<SessionException>(job.await().exceptionOrNull())
        assertEquals(SessionError.Timeout("factoryReset"), error.error)
    }
}
