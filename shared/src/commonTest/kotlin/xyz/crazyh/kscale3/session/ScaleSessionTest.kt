package xyz.crazyh.kscale3.session

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import xyz.crazyh.kscale3.ble.GattUuids
import xyz.crazyh.kscale3.domain.Sex
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.protocol.packets.TimeSyncPacket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** Mode 2 (routine measure) happy path, Code 1/2 branches, timeouts, disconnect, CRC, MAC. */
class ScaleSessionTest {

    private val fixedTime = TimeSyncPacket(2026, 9, 22, 14, 30, 5, 2)

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

    private fun options(
        verifyTokenA: Boolean = true,
        acceptFirstBiaFrame: Boolean = true,
    ) = SessionOptions(
        verifyTokenA = verifyTokenA,
        acceptFirstBiaFrame = acceptFirstBiaFrame,
        timeProvider = { fixedTime },
    )

    @Test
    fun measure_fullHappyPath() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.measure(user, TEST_MAC) } }

        scale.handshake()
        scale.awaitTimeSyncAndAck()

        // 0x31 profile type=0: weightDg = lastWeightKg 62.34kg = 6234
        val profile = scale.profileExchange(code = 0)
        assertEquals("30033000012345678", profile.huid)
        assertEquals("", profile.uid)
        assertEquals(1, profile.sex)
        assertEquals(30, profile.age)
        assertEquals(175, profile.heightCm)
        assertEquals(6234, profile.weightDg)
        assertEquals(0, profile.type)

        // 0x7A/0x8F queries then refresh
        scale.awaitQuery(GattUuids.MODEL)
        scale.sendFrames(
            GattUuids.MODEL,
            "M00F7".encodeToByteArray(),
            xyz.crazyh.kscale3.protocol.FrameMagic.PlainDownstream
        )
        scale.awaitQuery(GattUuids.OFFLINE_HISTORY)
        scale.sendFrames(
            GattUuids.OFFLINE_HISTORY,
            byteArrayOf(0x01, 0x02),
            xyz.crazyh.kscale3.protocol.FrameMagic.PlainDownstream
        )
        val refresh = scale.awaitProfilePacket()
        assertEquals(0, refresh.type)
        scale.sendStatus(GattUuids.USER_PROFILE, 0)

        // 0x97 arm + BIA (golden vector plaintext)
        scale.awaitQuery(GattUuids.BIA)
        scale.sendEncrypted(GattUuids.BIA, biaPayload())

        // 0x31 commit type=2
        val commit = scale.awaitProfilePacket()
        assertEquals(2, commit.type)
        assertEquals(0, commit.weightDg)
        assertEquals("30033000012345678", commit.huid)

        val result = job.await().getOrThrow()
        assertEquals(72.50, result.weightKg)
        assertEquals(21.5, result.fatPct)
        assertEquals(65, result.heartRate)
        assertContentEquals(listOf(256, 257, 258, 259, 260, 261), result.feetImp)
        assertContentEquals(listOf(512, 513, 514, 515, 516, 517), result.handsImp)
        assertEquals("2026-09-22-14-30-05+2", result.deviceTs)
        assertEquals(23.673469387755102, result.estimates.bmi, 1e-9) // 72.5 / 1.75^2
        assertIs<SessionState.Done>(session.state.value)
        assertEquals("M00F7", session.hardwareInfo.value.model)

        // write order: handshake + time + profile + 2 queries + refresh + arm + commit
        val uuids = scale.peripheral.attempts.map { it.uuid }
        assertEquals(
            listOf(
                GattUuids.AUTH_REQ,
                GattUuids.AUTH_TOKENS,
                GattUuids.AUTH_TOKENS,
                GattUuids.AUTH_TOKENS,
                GattUuids.AUTH_TOKENS,
                GattUuids.WORK_KEY,
                GattUuids.WORK_KEY,
                GattUuids.WORK_KEY,
                GattUuids.CAPABILITIES,
                GattUuids.TIME_SYNC,
                GattUuids.USER_PROFILE,
                GattUuids.USER_PROFILE,
                GattUuids.USER_PROFILE,
                GattUuids.USER_PROFILE,
                GattUuids.USER_PROFILE,
                GattUuids.USER_PROFILE,
                GattUuids.MODEL,
                GattUuids.OFFLINE_HISTORY,
                GattUuids.USER_PROFILE,
                GattUuids.USER_PROFILE,
                GattUuids.USER_PROFILE,
                GattUuids.USER_PROFILE,
                GattUuids.USER_PROFILE,
                GattUuids.USER_PROFILE,
                GattUuids.BIA,
                GattUuids.USER_PROFILE,
                GattUuids.USER_PROFILE,
                GattUuids.USER_PROFILE,
                GattUuids.USER_PROFILE,
                GattUuids.USER_PROFILE,
                GattUuids.USER_PROFILE,
            ),
            uuids,
        )
    }

    @Test
    fun measure_profileCode1_missingTare() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.measure(user, TEST_MAC) } }

        scale.handshake()
        scale.awaitTimeSyncAndAck()
        scale.profileExchange(code = 1)

        val error = assertIs<SessionException>(job.await().exceptionOrNull())
        assertEquals(SessionError.MissingTare, error.error)
        assertIs<SessionState.Failed>(session.state.value)
    }

    @Test
    fun measure_profileCode2_userNotRecognised() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.measure(user, TEST_MAC) } }

        scale.handshake()
        scale.awaitTimeSyncAndAck()
        scale.profileExchange(code = 2)

        val error = assertIs<SessionException>(job.await().exceptionOrNull())
        assertEquals(SessionError.UserNotRecognised, error.error)
    }

    @Test
    fun measure_randATimeout() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.measure(user, TEST_MAC) } }

        val write = scale.peripheral.awaitWrite()
        assertEquals(GattUuids.AUTH_REQ, write.uuid)
        // no randA reply -> 3s virtual timeout

        val error = assertIs<SessionException>(job.await().exceptionOrNull())
        assertEquals(SessionError.Timeout("randA"), error.error)
    }

    @Test
    fun measure_tokenAMismatch() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options(verifyTokenA = true))
        val job = async { runCatching { session.measure(user, TEST_MAC) } }

        scale.handshakeTokenAOnly(tokenAOverride = ByteArray(32) { 0x55 })

        val error = assertIs<SessionException>(job.await().exceptionOrNull())
        assertEquals(SessionError.TokenMismatch, error.error)
    }

    @Test
    fun measure_tokenAVerifyOff_wrongTokenAStillPasses() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options(verifyTokenA = false))
        val job = async { runCatching { session.measure(user, TEST_MAC) } }

        // session never awaits tokenA; feed garbage and continue the dialogue
        scale.handshake(tokenAOverride = ByteArray(32) { 0x55 })
        scale.awaitTimeSyncAndAck()
        scale.profileExchange(code = 0)
        scale.awaitQuery(GattUuids.MODEL)
        scale.awaitQuery(GattUuids.OFFLINE_HISTORY)
        scale.awaitProfilePacket()
        scale.sendStatus(GattUuids.USER_PROFILE, 0)
        scale.awaitQuery(GattUuids.BIA)
        scale.sendEncrypted(GattUuids.BIA, biaPayload())
        scale.awaitProfilePacket()

        val result = job.await().getOrThrow()
        assertEquals(72.50, result.weightKg)
    }

    @Test
    fun measure_workKeyCodeNonZero() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.measure(user, TEST_MAC) } }

        scale.handshake(workKeyCode = 3)

        val error = assertIs<SessionException>(job.await().exceptionOrNull())
        assertEquals(SessionError.UnexpectedReply("workKey", 3), error.error)
    }

    @Test
    fun measure_biaTimeout() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.measure(user, TEST_MAC) } }

        scale.handshake()
        scale.awaitTimeSyncAndAck()
        scale.profileExchange(code = 0)
        scale.awaitQuery(GattUuids.MODEL)
        scale.awaitQuery(GattUuids.OFFLINE_HISTORY)
        scale.awaitProfilePacket()
        scale.sendStatus(GattUuids.USER_PROFILE, 0)
        scale.awaitQuery(GattUuids.BIA)
        // user never steps on -> 120s virtual timeout

        val error = assertIs<SessionException>(job.await().exceptionOrNull())
        assertEquals(SessionError.Timeout("bia"), error.error)
    }

    @Test
    fun measure_biaImplausibleFiltered_thenPlausibleAccepted() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.measure(user, TEST_MAC) } }

        scale.handshake()
        scale.awaitTimeSyncAndAck()
        scale.profileExchange(code = 0)
        scale.awaitQuery(GattUuids.MODEL)
        scale.awaitQuery(GattUuids.OFFLINE_HISTORY)
        scale.awaitProfilePacket()
        scale.sendStatus(GattUuids.USER_PROFILE, 0)
        scale.awaitQuery(GattUuids.BIA)
        // garbage frame (wrong-MAC symptom: absurd weight) then a good one
        scale.sendEncrypted(GattUuids.BIA, biaPayload(weightDg = 3))
        scale.sendEncrypted(GattUuids.BIA, biaPayload())
        scale.awaitProfilePacket()

        val result = job.await().getOrThrow()
        assertEquals(72.50, result.weightKg)
    }

    @Test
    fun measure_biaOnlyImplausible_macInvalid() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.measure(user, TEST_MAC) } }

        scale.handshake()
        scale.awaitTimeSyncAndAck()
        scale.profileExchange(code = 0)
        scale.awaitQuery(GattUuids.MODEL)
        scale.awaitQuery(GattUuids.OFFLINE_HISTORY)
        scale.awaitProfilePacket()
        scale.sendStatus(GattUuids.USER_PROFILE, 0)
        scale.awaitQuery(GattUuids.BIA)
        repeat(3) { scale.sendEncrypted(GattUuids.BIA, biaPayload(weightDg = 3)) }
        // only undecodable/implausible frames -> timeout maps to MacInvalid

        val error = assertIs<SessionException>(job.await().exceptionOrNull())
        assertEquals(SessionError.MacInvalid, error.error)
    }

    @Test
    fun measure_midSessionDisconnect() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.measure(user, TEST_MAC) } }

        scale.handshake()
        scale.peripheral.simulateDisconnect()

        val error = assertIs<SessionException>(job.await().exceptionOrNull())
        assertEquals(SessionError.Disconnected, error.error)
    }

    @Test
    fun measure_invalidMac_macInvalid() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val error = assertFailsWith<SessionException> {
            session.measure(user, "00000000-0000-0000-0000-00000000f00d") // iOS-style UUID
        }
        assertEquals(SessionError.MacInvalid, error.error)
    }

    @Test
    fun measure_crcErrorOnReply() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        val job = async { runCatching { session.measure(user, TEST_MAC) } }

        scale.peripheral.awaitWrite() // 0x21 QUERY_ARM
        // corrupt CRC (empty frame with zero CRC field)
        scale.peripheral.notify(GattUuids.AUTH_REQ, "db03000000".hexToBytesSafe())

        val error = assertIs<SessionException>(job.await().exceptionOrNull())
        assertEquals(SessionError.CrcError, error.error)
    }

    @Test
    fun measure_requiresRegisteredUser() = runTest {
        val scale = TestScale()
        val session = ScaleSessionImpl(scale.peripheral, options())
        var thrown: Throwable? = null
        try {
            session.measure(user.copy(huid = null), TEST_MAC)
        } catch (t: Throwable) {
            thrown = t
        }
        assertIs<IllegalArgumentException>(thrown)
    }
}

private fun String.hexToBytesSafe(): ByteArray {
    val clean = replace(" ", "")
    return ByteArray(clean.length / 2) { i ->
        clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}
