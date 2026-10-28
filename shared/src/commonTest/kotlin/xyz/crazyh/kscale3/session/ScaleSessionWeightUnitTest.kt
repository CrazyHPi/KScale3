package xyz.crazyh.kscale3.session

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import xyz.crazyh.kscale3.ble.GattUuids
import xyz.crazyh.kscale3.domain.WeightUnit
import xyz.crazyh.kscale3.protocol.ProtocolConstants
import xyz.crazyh.kscale3.protocol.hexToBytes
import xyz.crazyh.kscale3.protocol.packets.TimeSyncPacket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** 单位读/写会话（TECH.md §7.3）：握手 + 一次 0x5A 写 / 7e6dbc73 查询。 */
class ScaleSessionWeightUnitTest {

    private fun options() =
        SessionOptions(timeProvider = { TimeSyncPacket(2026, 9, 22, 14, 30, 5, 2) })

    @Test
    fun setWeightUnit_writesGoldenFrameAndWaitsStatus0() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { session.setWeightUnit(WeightUnit.Lb, TEST_MAC) }

        scale.handshake(autoQueryUnit = false)

        // 0x5A 无响应写，帧 = DB 04 00 02 70 80（黄金向量）
        val write = scale.peripheral.awaitWrite()
        assertEquals(GattUuids.SERIAL, write.uuid)
        assertContentEquals("db0400027080".hexToBytes(), write.data)
        assertEquals(false, write.withResponse)

        scale.sendUnitReply(GattUuids.SERIAL, 0) // BD 04 00 00（设置确认）
        job.await()

        assertEquals(WeightUnit.Lb, session.hardwareInfo.value.weightUnit)
        // status 0 后不再回读（秤异步应用单位，立即查询会拿到旧值）
        assertEquals(
            0,
            scale.peripheral.attempts.count { it.uuid == GattUuids.WEIGHT_UNIT_QUERY },
            "设置单位不应立即回读",
        )
        assertEquals(SessionState.Idle, session.state.value) // 单位操作不残留称重屏状态
        assertEquals(true, scale.peripheral.isConnected.value) // 持久连接：操作结束不断连
    }

    @Test
    fun setWeightUnit_nonZeroStatus_fails() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.setWeightUnit(WeightUnit.Jin, TEST_MAC) } }

        scale.handshake(autoQueryUnit = false)
        scale.peripheral.awaitWrite()
        scale.sendUnitReply(GattUuids.SERIAL, 1) // 非 0 = 设置失败

        val error = assertIs<SessionException>(job.await().exceptionOrNull())
        assertEquals(SessionError.UnexpectedReply("weightUnitSet", 1), error.error)
        assertEquals(null, session.hardwareInfo.value.weightUnit)
    }

    @Test
    fun setWeightUnit_noReply_timesOut() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.setWeightUnit(WeightUnit.Kg, TEST_MAC) } }

        scale.handshake(autoQueryUnit = false)
        scale.peripheral.awaitWrite()
        // 秤不回确认 → 2s 虚拟超时

        val error = assertIs<SessionException>(job.await().exceptionOrNull())
        assertEquals(SessionError.Timeout("weightUnitSet"), error.error)
    }

    @Test
    fun readWeightUnit_queriesAndParses() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { session.readWeightUnit(TEST_MAC) }

        scale.handshake(autoQueryUnit = false)

        val write = scale.peripheral.awaitWrite()
        assertEquals(GattUuids.WEIGHT_UNIT_QUERY, write.uuid)
        assertContentEquals(ProtocolConstants.QUERY_ARM, write.data)
        assertEquals(false, write.withResponse)

        scale.sendUnitReply(GattUuids.WEIGHT_UNIT_QUERY, 3) // BD 04 00 03（斤）
        assertEquals(WeightUnit.Jin, job.await())
        assertEquals(WeightUnit.Jin, session.hardwareInfo.value.weightUnit)
    }

    @Test
    fun readWeightUnit_unknownCode_fails() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.readWeightUnit(TEST_MAC) } }

        scale.handshake(autoQueryUnit = false)
        scale.peripheral.awaitWrite()
        scale.sendUnitReply(GattUuids.WEIGHT_UNIT_QUERY, 7)

        val error = assertIs<SessionException>(job.await().exceptionOrNull())
        assertEquals(SessionError.UnexpectedReply("weightUnit", 7), error.error)
    }

    @Test
    fun readWeightUnit_invalidMac_fails() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val error = assertFailsWith<SessionException> {
            session.readWeightUnit("00000000-0000-0000-0000-00000000f00d")
        }
        assertEquals(SessionError.MacInvalid, error.error)
    }
}
