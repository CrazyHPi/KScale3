package xyz.crazyh.kscale3.session

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import xyz.crazyh.kscale3.ble.GattUuids
import xyz.crazyh.kscale3.domain.Sex
import xyz.crazyh.kscale3.domain.UserProfileDraft
import xyz.crazyh.kscale3.protocol.FrameMagic
import xyz.crazyh.kscale3.protocol.hexToBytes
import xyz.crazyh.kscale3.protocol.packets.TimeSyncPacket

/** Mode 1 (flash pairing): full path, tare failures, tail softness, unbind switch, safeWrite flip. */
class ScaleSessionPairingTest {

    private val fixedTime = TimeSyncPacket(2026, 9, 22, 14, 30, 5, 2)

    private val draft = UserProfileDraft(name = "newbie", sex = Sex.Male, age = 30, heightCm = 175, goalWeightKg = null)

    private fun options(
        sendUnbindStep: Boolean = false,
        verifyTokenA: Boolean = true,
    ) = SessionOptions(
        sendUnbindStep = sendUnbindStep,
        verifyTokenA = verifyTokenA,
        timeProvider = { fixedTime },
    )

    @Test
    fun pair_fullHappyPath() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { session.pair(draft, TEST_MAC) }

        scale.handshake()

        // 0x45 arm: exact golden frame db04000120d9
        val arm = scale.peripheral.awaitWrite()
        assertEquals(GattUuids.BINDING, arm.uuid)
        assertContentEquals("db04000120d9".hexToBytes(), arm.data)

        // 0x2D HUID (4 frames) -> decrypted HUID matches the returned one (checked after)
        val huidWire = scale.collectFrames(GattUuids.HUID_TARE, expectedFrames = 4)
        val huidPlain = xyz.crazyh.kscale3.protocol.ScaleCrypto.decryptAesCtr(scale.workKey, huidWire)
        assertEquals(30, huidPlain.size)

        // tare reply 62.34kg (golden 005a1800)
        scale.sendEncrypted(GattUuids.HUID_TARE, tarePayload(status = 0, weightDg = 6234))

        scale.awaitTimeSyncAndAck()

        // Mode 1 pre-profile queries 0x5A/0x7A/0x62
        scale.awaitQuery(GattUuids.SERIAL)
        scale.sendFrames(GattUuids.SERIAL, "SN42".encodeToByteArray(), FrameMagic.PlainDownstream)
        scale.awaitQuery(GattUuids.MODEL)
        scale.sendFrames(GattUuids.MODEL, "M00F7".encodeToByteArray(), FrameMagic.PlainDownstream)
        scale.awaitQuery(GattUuids.CONFIG)
        scale.sendStatus(GattUuids.CONFIG, 1)

        // 0x31 profile: weightDg must be the tare 6234
        val profile = scale.profileExchange(code = 0)
        assertEquals(6234, profile.weightDg)
        assertEquals(0, profile.type)

        // post-profile queries 0x7A/0x8F + refresh
        scale.awaitQuery(GattUuids.MODEL)
        scale.awaitQuery(GattUuids.OFFLINE_HISTORY)
        scale.awaitProfilePacket()
        scale.sendStatus(GattUuids.USER_PROFILE, 0)

        // BIA + commit
        scale.awaitQuery(GattUuids.BIA)
        scale.sendEncrypted(GattUuids.BIA, biaPayload())
        val commit = scale.awaitProfilePacket()
        assertEquals(2, commit.type)

        val result = job.await()
        assertIs<PairingResult.Success>(result)
        assertTrue(Regex("300330000\\d{8}").matches(result.huid))
        // decrypted HUID from the 0x2D write equals the generated one
        assertEquals(result.huid, huidPlain.decodeToString().trim(Char(0)))
        assertEquals(72.50, result.result?.weightKg)
        assertIs<SessionState.Paired>(session.state.value)

        // hardwareInfo captured from queries
        assertEquals("SN42", session.hardwareInfo.value.serial)
        assertEquals("M00F7", session.hardwareInfo.value.model)
        assertEquals(1, session.hardwareInfo.value.configCode)

        // unbind default off: 0x45 written exactly once (the arm)
        val bindWrites = scale.peripheral.attempts.filter { it.uuid == GattUuids.BINDING }
        assertEquals(1, bindWrites.size)
    }

    @Test
    fun pair_unbindSwitchEnabled_writesUnbindAfterProfile() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options(sendUnbindStep = true))
        val job = async { session.pair(draft, TEST_MAC) }

        scale.handshake()
        scale.peripheral.awaitWrite() // 0x45 arm
        scale.collectFrames(GattUuids.HUID_TARE, 4)
        scale.sendEncrypted(GattUuids.HUID_TARE, tarePayload())
        scale.awaitTimeSyncAndAck()
        scale.awaitQuery(GattUuids.SERIAL)
        scale.awaitQuery(GattUuids.MODEL)
        scale.awaitQuery(GattUuids.CONFIG)
        scale.profileExchange(code = 0)

        // unbind write lands right after profile
        val unbind = scale.peripheral.awaitWrite()
        assertEquals(GattUuids.BINDING, unbind.uuid)
        assertContentEquals("db0300c140".hexToBytes(), unbind.data)
        scale.sendStatus(GattUuids.BINDING, 0)

        scale.awaitQuery(GattUuids.MODEL)
        scale.awaitQuery(GattUuids.OFFLINE_HISTORY)
        scale.awaitProfilePacket()
        scale.sendStatus(GattUuids.USER_PROFILE, 0)
        scale.awaitQuery(GattUuids.BIA)
        scale.sendEncrypted(GattUuids.BIA, biaPayload())
        scale.awaitProfilePacket()

        val result = job.await()
        assertIs<PairingResult.Success>(result)
        assertEquals(2, scale.peripheral.attempts.count { it.uuid == GattUuids.BINDING })
    }

    @Test
    fun pair_tareTimeout_fails() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { session.pair(draft, TEST_MAC) }

        scale.handshake()
        scale.peripheral.awaitWrite() // 0x45 arm
        scale.collectFrames(GattUuids.HUID_TARE, 4)
        // user never steps on -> 25s virtual timeout

        val result = job.await()
        assertIs<PairingResult.Failure>(result)
        assertEquals(SessionError.Timeout("tare"), (result as PairingResult.Failure).error)
    }

    @Test
    fun pair_tareStatusNonZero_fails() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { session.pair(draft, TEST_MAC) }

        scale.handshake()
        scale.peripheral.awaitWrite() // 0x45 arm
        scale.collectFrames(GattUuids.HUID_TARE, 4)
        scale.sendEncrypted(GattUuids.HUID_TARE, tarePayload(status = 7))

        val result = job.await()
        assertIs<PairingResult.Failure>(result)
        assertEquals(SessionError.UnexpectedReply("tare", 7), (result as PairingResult.Failure).error)
    }

    @Test
    fun pair_biaTimeout_stillSuccessWithNullMeasurement() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { session.pair(draft, TEST_MAC) }

        scale.handshake()
        scale.peripheral.awaitWrite() // 0x45 arm
        scale.collectFrames(GattUuids.HUID_TARE, 4)
        scale.sendEncrypted(GattUuids.HUID_TARE, tarePayload())
        scale.awaitTimeSyncAndAck()
        scale.awaitQuery(GattUuids.SERIAL)
        scale.awaitQuery(GattUuids.MODEL)
        scale.awaitQuery(GattUuids.CONFIG)
        scale.profileExchange(code = 0) // registration proven
        scale.awaitQuery(GattUuids.MODEL)
        scale.awaitQuery(GattUuids.OFFLINE_HISTORY)
        scale.awaitProfilePacket()
        scale.sendStatus(GattUuids.USER_PROFILE, 0)
        scale.awaitQuery(GattUuids.BIA)
        // BIA never arrives -> tail fails softly, registration kept

        val result = job.await()
        assertIs<PairingResult.Success>(result)
        assertNull((result as PairingResult.Success).result)
        assertIs<SessionState.Paired>(session.state.value)
    }

    @Test
    fun pair_profileCode1_failsMissingTare() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { session.pair(draft, TEST_MAC) }

        scale.handshake()
        scale.peripheral.awaitWrite() // 0x45 arm
        scale.collectFrames(GattUuids.HUID_TARE, 4)
        scale.sendEncrypted(GattUuids.HUID_TARE, tarePayload())
        scale.awaitTimeSyncAndAck()
        scale.awaitQuery(GattUuids.SERIAL)
        scale.awaitQuery(GattUuids.MODEL)
        scale.awaitQuery(GattUuids.CONFIG)
        scale.profileExchange(code = 1)

        val result = job.await()
        assertIs<PairingResult.Failure>(result)
        assertEquals(SessionError.MissingTare, (result as PairingResult.Failure).error)
    }

    @Test
    fun safeWrite_flipsWithResponseOnPermitError() = runTest {
        val scale = TestScale()
        var failuresLeft = 1
        scale.peripheral.onWrite = { write ->
            if (failuresLeft > 0 && write.uuid == GattUuids.AUTH_REQ) {
                failuresLeft--
                Exception("GATT write not permitted") // triggers the flip retry (scale3.py L176)
            } else {
                null
            }
        }
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.measure(
            xyz.crazyh.kscale3.domain.UserProfile(
                id = 1, name = "t", sex = Sex.Male, age = 30, heightCm = 175,
                goalWeightKg = null, huid = "30033000012345678",
                registeredAt = null, createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
            ),
            TEST_MAC,
        ) } }

        // drive the whole handshake (first AUTH_REQ write fails, flip retry succeeds inside it)
        scale.handshake()

        val authAttempts = scale.peripheral.attempts.filter { it.uuid == GattUuids.AUTH_REQ }
        assertEquals(2, authAttempts.size)
        assertEquals(true, authAttempts[0].withResponse)
        assertEquals(false, authAttempts[1].withResponse)

        // finish the session to prove the write went through
        scale.awaitTimeSyncAndAck()
        scale.profileExchange(code = 0)
        scale.awaitQuery(GattUuids.MODEL)
        scale.awaitQuery(GattUuids.OFFLINE_HISTORY)
        scale.awaitProfilePacket()
        scale.sendStatus(GattUuids.USER_PROFILE, 0)
        scale.awaitQuery(GattUuids.BIA)
        scale.sendEncrypted(GattUuids.BIA, biaPayload())
        scale.awaitProfilePacket()

        assertEquals(72.50, job.await().getOrThrow().weightKg)
    }
}
