package xyz.crazyh.kscale3.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import xyz.crazyh.kscale3.ble.BlePeripheral
import xyz.crazyh.kscale3.ble.GattUuids
import xyz.crazyh.kscale3.domain.BodyCompositionEstimator
import xyz.crazyh.kscale3.domain.HuidGenerator
import xyz.crazyh.kscale3.domain.MeasurementResult
import xyz.crazyh.kscale3.domain.Sex
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.domain.UserProfileDraft
import xyz.crazyh.kscale3.domain.WeightUnit
import xyz.crazyh.kscale3.protocol.Frame
import xyz.crazyh.kscale3.protocol.FrameMagic
import xyz.crazyh.kscale3.protocol.MacAddress
import xyz.crazyh.kscale3.protocol.ProtocolConstants
import xyz.crazyh.kscale3.protocol.ScaleCrypto
import xyz.crazyh.kscale3.protocol.crypto.secureRandomBytes
import xyz.crazyh.kscale3.protocol.packets.BiaMeasurement
import xyz.crazyh.kscale3.protocol.packets.TareResponse
import xyz.crazyh.kscale3.protocol.packets.UserProfilePacket
import xyz.crazyh.kscale3.protocol.packets.WeightUnitPacket
import xyz.crazyh.kscale3.protocol.toHex
import kotlin.math.roundToInt

/**
 * Scale session state machine (PLAN zh-CN docs; control scale3.py Scale3Driver.run).
 *
 * Structured concurrency: all work of pair/measure (notify collectors, disconnect
 * watcher, timeouts) runs in the caller coroutine context, so `runTest` virtual time
 * makes e.g. the 120s BIA timeout fire instantly. Subscriptions stay alive for the
 * whole session (see FrameIo).
 *
 * Deliberate deviations from the Python reference (PLAN diff notes 1/2/5/7/8):
 * tokenA verification, 0x31 Code 1/2 branches, 20ms inter-frame gap only on 0x25/0x29,
 * strict 0x2D status handling, unbind step default off.
 */
class ScaleSessionImpl(
    private val peripheral: BlePeripheral,
    private val options: SessionOptions = SessionOptions(),
) : ScaleSession {

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _hardwareInfo = MutableStateFlow(HardwareInfo())
    override val hardwareInfo: StateFlow<HardwareInfo> = _hardwareInfo.asStateFlow()

    private var activeJob: Job? = null

    override suspend fun pair(user: UserProfileDraft, mac: MacAddress): PairingResult {
        val huid = HuidGenerator.generate()
        return try {
            val output = runOperation(mac, Mode.Pairing(user, huid))
            _state.value = SessionState.Paired(huid)
            PairingResult.Success(huid, output.result)
        } catch (e: SessionException) {
            PairingResult.Failure(e.error)
        }
    }

    override suspend fun measure(user: UserProfile, mac: MacAddress): MeasurementResult {
        val huid =
            requireNotNull(user.huid) { "user must be registered on the scale (huid) before measure()" }
        val output = runOperation(mac, Mode.Routine(user, huid))
        val result = requireNotNull(output.result)
        _state.value = SessionState.Done(result)
        return result
    }

    override suspend fun readWeightUnit(mac: MacAddress): WeightUnit =
        runUnitOperation(mac) { io ->
            // 通用查询 `db0300c140`，同样是 Write Command（无响应写，TECH.md §7.3）
            io.writeRaw(
                GattUuids.WEIGHT_UNIT_QUERY,
                ProtocolConstants.QUERY_ARM,
                withResponse = false
            )
            val frame = io.await(GattUuids.WEIGHT_UNIT_QUERY, "weightUnit", options.timing.unit)
            val code = WeightUnitPacket.replyCode(frame.payload)
            val unit = code?.let(WeightUnit::fromCode)
                ?: throw SessionException(SessionError.UnexpectedReply("weightUnit", code))
            _hardwareInfo.value = _hardwareInfo.value.copy(weightUnit = unit)
            unit
        }

    override suspend fun setWeightUnit(unit: WeightUnit, mac: MacAddress) {
        runUnitOperation(mac) { io ->
            // 无响应写（TECH.md §7.3）；确认是同特征的 Indication `BD 04 00 00`
            io.writeRaw(
                GattUuids.SERIAL,
                WeightUnitPacket.setFrame(unit.code),
                withResponse = false
            )
            val frame = io.await(GattUuids.SERIAL, "weightUnitSet", options.timing.unit)
            val code = WeightUnitPacket.replyCode(frame.payload)
            if (code != 0) throw SessionException(
                SessionError.UnexpectedReply(
                    "weightUnitSet",
                    code
                )
            )
            _hardwareInfo.value = _hardwareInfo.value.copy(weightUnit = unit)
        }
    }

    override fun close() {
        activeJob?.cancel()
    }

    // ---- session skeleton ----

    private sealed class Mode {
        abstract val huid: String
        abstract val sex: Sex
        abstract val age: Int
        abstract val heightCm: Int

        /** 0x31 type=0 Estimated Weight: pairing uses 0x2D tare, routine uses last measurement. */
        abstract val fallbackWeightDg: Int

        class Pairing(val draft: UserProfileDraft, override val huid: String) : Mode() {
            override val sex: Sex get() = draft.sex
            override val age: Int get() = draft.age
            override val heightCm: Int get() = draft.heightCm
            override val fallbackWeightDg: Int get() = 0
        }

        class Routine(val user: UserProfile, override val huid: String) : Mode() {
            override val sex: Sex get() = user.sex
            override val age: Int get() = user.age
            override val heightCm: Int get() = user.heightCm
            override val fallbackWeightDg: Int
                get() = user.lastWeightKg?.let { (it * 100).roundToInt().coerceIn(0, 0xFFFF) } ?: 0
        }
    }

    private class SessionOutput(val result: MeasurementResult?)

    /**
     * 连接 → 握手 → [block] → 拆除（断连 watcher、帧日志、NonCancellable 断连）。
     * 失败原因不在此映射状态：配对流程的"Code 0 后断连仍算成功"需要调用方判断。
     */
    private suspend fun <T> withConnectedSession(
        mac: MacAddress,
        block: suspend (workKey: ByteArray, io: FrameIo) -> T,
    ): T {
        val rootKey = try {
            ScaleCrypto.deriveRootKey(mac)
        } catch (e: IllegalArgumentException) {
            throw SessionException(SessionError.MacInvalid, cause = e)
        }

        return coroutineScope {
            activeJob = coroutineContext[Job]
            _state.value = SessionState.Connecting
            try {
                peripheral.connect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw SessionException(SessionError.Disconnected, cause = e)
            }

            // mid-session disconnect watcher (true -> false counts as disconnect)
            val watcher = launch {
                peripheral.isConnected.dropWhile { !it }.first { !it }
                throw SessionException(SessionError.Disconnected)
            }
            val io = FrameIo(peripheral, this, options.onLog)
            val collectors = io.start()

            try {
                delay(options.timing.settle)
                block(handshake(rootKey, io), io)
            } finally {
                // stop background children BEFORE disconnecting: otherwise the watcher
                // sees the teardown disconnect and poisons a successful run with Disconnected
                watcher.cancel()
                collectors.forEach { it.cancel() }
                activeJob = null
                withContext(NonCancellable) {
                    runCatching { peripheral.disconnect() }
                }
            }
        }
    }

    private suspend fun runOperation(mac: MacAddress, mode: Mode): SessionOutput {
        // Reaching this point after measurementFlow() means 0x31 Code 0 confirmed the profile.
        // Mode 1 failures after that do not undo the flash registration (see pair() KDoc).
        var profileConfirmed = false

        return try {
            withConnectedSession(mac) { workKey, io ->
                val profileWeightDg = when (mode) {
                    is Mode.Pairing -> bindingPreamble(workKey, mode.huid, io)
                    is Mode.Routine -> mode.fallbackWeightDg
                }

                // hard for both modes up to 0x31 Code 0 (registration/ownership proven)
                measurementFlow(workKey, mode, profileWeightDg, io) { profileConfirmed = true }

                // BIA tail: Mode 1 failures do not undo the flash registration
                val result = try {
                    biaTail(workKey, mode, io)
                } catch (e: SessionException) {
                    if (mode is Mode.Pairing) {
                        options.onLog?.invoke("pairing BIA tail failed (${e.error}); registration kept")
                        null
                    } else {
                        throw e
                    }
                }
                SessionOutput(result)
            }
        } catch (e: SessionException) {
            // A child coroutine failure (the disconnect watcher) bypasses the inner try/catch:
            // without this outer catch the state stays at AwaitingMeasurement forever and the
            // UI dialog cannot be dismissed.
            if (mode is Mode.Pairing && profileConfirmed && e.error == SessionError.Disconnected) {
                options.onLog?.invoke("pairing: disconnected after Code 0; registration kept")
                return SessionOutput(null)
            }
            _state.value = SessionState.Failed(e.error)
            throw e
        }
    }

    /**
     * 单位控制：握手后执行一次读/写；结束后状态回到 [SessionState.Idle]（不进称重屏的错误条）。
     * 会话骨架与配对/称重共用（断连 watcher、帧日志、NonCancellable 断连）。
     */
    private suspend fun <T> runUnitOperation(
        mac: MacAddress,
        block: suspend (io: FrameIo) -> T,
    ): T = try {
        withConnectedSession(mac) { _, io -> block(io) }
    } finally {
        if (_state.value != SessionState.Idle) _state.value = SessionState.Idle
    }

    // ---- common handshake (0x21/0x25/0x29/0xD7) ----

    private suspend fun handshake(rootKey: ByteArray, io: FrameIo): ByteArray {
        _state.value = SessionState.Handshaking
        val timing = options.timing

        // 0x21: QUERY_ARM -> randA (>=16B)
        io.writeRaw(GattUuids.AUTH_REQ, ProtocolConstants.QUERY_ARM)
        val randAFrame = io.await(GattUuids.AUTH_REQ, "randA", timing.randA)
        val randA = randAFrame.payload.let {
            if (it.size < 16) throw SessionException(
                SessionError.UnexpectedReply("randA"),
                "randA ${it.size}B < 16B"
            )
            it.copyOfRange(0, 16)
        }

        // 0x25: randB(16B)+tokenB(32B), clear 0xDB in 4 frames (20ms gap)
        val randB = secureRandomBytes(16)
        val tokenB = ScaleCrypto.generateTokenB(randA, randB)
        io.writeFrames(
            GattUuids.AUTH_TOKENS,
            randB + tokenB,
            FrameMagic.PlainUpstream,
            timing.interFrameWrite
        )
        if (options.verifyTokenA) {
            val tokenAFrame = io.await(GattUuids.AUTH_TOKENS, "tokenA", timing.tokenA)
            val expected = ScaleCrypto.generateTokenA(randA, randB)
            val got = tokenAFrame.payload.let {
                if (it.size < 32) throw SessionException(
                    SessionError.UnexpectedReply("tokenA"),
                    "tokenA ${it.size}B < 32B"
                )
                it.copyOfRange(0, 32)
            }
            if (!got.contentEquals(expected)) throw SessionException(SessionError.TokenMismatch)
        }

        // 0x29: IV + AES-CTR(RootKey, WorkKey), 0xDC in 3 frames (20ms gap) -> Code 0
        // (Code 0 only means WorkKey installed, TECH.md section 2 note)
        val workKey = secureRandomBytes(16)
        io.writeFrames(
            GattUuids.WORK_KEY,
            ScaleCrypto.encryptAesCtr(rootKey, workKey),
            FrameMagic.EncryptedUpstream,
            timing.interFrameWrite,
        )
        val code = statusCode(io.await(GattUuids.WORK_KEY, "workKey", timing.workKey), "workKey")
        if (code != 0) throw SessionException(SessionError.UnexpectedReply("workKey", code))

        // 0xD7: raw 10B capability write (write-without-response; soft-fail like Python)
        try {
            io.writeRaw(
                GattUuids.CAPABILITIES,
                ProtocolConstants.CAPABILITY_BYTES,
                withResponse = false
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            options.onLog?.invoke("0xD7 capabilities write failed (ignored): ${e.message}")
        }
        return workKey
    }

    // ---- Mode 1 preamble (0x45 arm -> 0x2D HUID -> tare weigh-in) ----

    /** Returns tare weight in centi-kg: prerequisite for 0x31 Code 0 (TECH.md section 6 step 8). */
    private suspend fun bindingPreamble(workKey: ByteArray, huid: String, io: FrameIo): Int {
        val timing = options.timing

        // 0x45 arm (frame db04000120d9); no unbind step by default (diff note 8, see SessionOptions)
        io.writeFrames(GattUuids.BINDING, byteArrayOf(0x01), FrameMagic.PlainUpstream)
        io.awaitSoft(GattUuids.BINDING, "bindArm", timing.bindArm)

        // 0x2D: AES(WorkKey, HUID 30B) -> 0xDC frames written back-to-back
        val huid30 = ByteArray(30).also { buf ->
            val bytes = huid.encodeToByteArray()
            require(bytes.size <= 30) { "huid longer than 30B: $huid" }
            bytes.copyInto(buf)
        }
        io.writeFrames(
            GattUuids.HUID_TARE,
            ScaleCrypto.encryptAesCtr(workKey, huid30),
            FrameMagic.EncryptedUpstream
        )

        // Tare weigh-in reply: [status u8][tare u16le /100kg][pad].
        // Real-device finding (2026-10-25, 3 consecutive attempts): after the HUID write this unit
        // replies ONCE with a 15B message: status=1, weight ~57.5kg (varies 57.50/57.60), plus a
        // second u16 that varies ~5512..5570 (likely raw impedance). No status=0 message ever
        // arrives within the 25s window (nor after), so requiring status=0 always times out.
        // Interpretation: status here is a "weigh-in captured" flag, not 0x31's Code 1 meaning
        // ("no validation weigh-in"); accept status 0/1 as long as the weight is plausible.
        // (Earlier 2026-09-24 note assumed a separate status=0 tare would follow — not observed.)
        _state.value = SessionState.AwaitingTare
        val tareDg = withTimeoutOrNull(timing.tare) {
            var captured: Int? = null
            while (captured == null) {
                val frame = io.receive(GattUuids.HUID_TARE, "tare")
                val plain = decryptBody(workKey, frame, "tare")
                val tare = try {
                    TareResponse.decode(plain)
                } catch (e: IllegalArgumentException) {
                    throw SessionException(SessionError.UnexpectedReply("tare"), cause = e)
                }
                options.onLog?.invoke(
                    "[0x2D plain ${plain.size}B] ${plain.toHex()} -> status=${tare.status} " +
                            "weight=${tare.weightKg}kg",
                )
                when {
                    (tare.status == 0 || tare.status == 1) && tare.weightKg in 1.0..300.0 ->
                        captured = (tare.weightKg * 100).roundToInt().coerceIn(0, 0xFFFF)

                    tare.status == 0 || tare.status == 1 -> Unit // no weigh-in yet: keep waiting
                    else -> throw SessionException(
                        SessionError.UnexpectedReply(
                            "tare",
                            tare.status
                        )
                    )
                }
            }
            captured
        }
        return tareDg ?: throw SessionException(SessionError.Timeout("tare"))
    }

    // ---- shared measurement stage ----

    /** Time sync -> 0x31 Code check -> queries/refresh. Hard for both modes. */
    private suspend fun measurementFlow(
        workKey: ByteArray,
        mode: Mode,
        profileWeightDg: Int,
        io: FrameIo,
        onProfileConfirmed: () -> Unit = {},
    ) {
        val timing = options.timing

        // 0x52 time sync (private 8B layout, plaintext; ack soft)
        _state.value = SessionState.SyncingTime
        io.writeFrames(
            GattUuids.TIME_SYNC,
            options.timeProvider().encode(),
            FrameMagic.PlainUpstream
        )
        io.awaitSoft(GattUuids.TIME_SYNC, "timeSync", timing.timeSync)

        // Mode 1 only: 0x5A/0x7A/0x62 queries (Python STEP3.5, diff note 4)
        if (mode is Mode.Pairing) {
            querySerial(io)
            queryModel(io)
            queryConfig(io)
        }

        // 0x31 profile type=0 (weight = tare / last measurement) -> Code is the ownership check
        _state.value = SessionState.SendingProfile
        val profile = UserProfilePacket(
            huid = mode.huid,
            uid = "",
            sex = if (mode.sex == Sex.Male) 1 else 0,
            age = mode.age,
            heightCm = mode.heightCm,
            weightDg = profileWeightDg,
            type = 0,
        )
        io.writeFrames(
            GattUuids.USER_PROFILE,
            ScaleCrypto.encryptAesCtr(workKey, profile.encode()),
            FrameMagic.EncryptedUpstream,
        )
        val code =
            statusCode(io.await(GattUuids.USER_PROFILE, "profile", timing.profileFirst), "profile")
        options.onLog?.invoke("[0x31 type=0 weightDg=$profileWeightDg -> code=$code]")
        _state.value = SessionState.ProfileResponded(code)
        when (code) {
            0 -> onProfileConfirmed()
            1 -> throw SessionException(SessionError.MissingTare)
            2 -> throw SessionException(SessionError.UserNotRecognised)
            else -> throw SessionException(SessionError.UnexpectedReply("profile", code))
        }

        // "unbind" debug switch (default off; diff note 8)
        if (mode is Mode.Pairing && options.sendUnbindStep) {
            io.writeRaw(GattUuids.BINDING, ProtocolConstants.QUERY_ARM)
            io.awaitSoft(GattUuids.BINDING, "unbind", timing.bindArm)
        }

        // 0x7A/0x8F queries (both modes, Python STEP5)
        queryModel(io)
        queryHistory(io)

        // 0x31 refresh (same packet, new IV; ack soft, Python STEP6)
        io.writeFrames(
            GattUuids.USER_PROFILE,
            ScaleCrypto.encryptAesCtr(workKey, profile.encode()),
            FrameMagic.EncryptedUpstream,
        )
        io.awaitSoft(GattUuids.USER_PROFILE, "profileRefresh", timing.profileRefresh)
    }

    /** 0x97 arm + BIA stream -> 0x31 commit. Failures here are soft for Mode 1 (registration kept). */
    private suspend fun biaTail(
        workKey: ByteArray,
        mode: Mode,
        io: FrameIo,
    ): MeasurementResult {
        val timing = options.timing

        // 0x97 arm + BIA stream
        _state.value = SessionState.AwaitingMeasurement
        io.writeRaw(GattUuids.BIA, ProtocolConstants.QUERY_ARM)
        val bia = awaitBia(workKey, io)

        // 0x31 commit type=2 (weight=0; no ack wait, settle 0.5s like Python)
        _state.value = SessionState.Committing
        val commit = UserProfilePacket(
            huid = mode.huid,
            uid = "",
            sex = if (mode.sex == Sex.Male) 1 else 0,
            age = mode.age,
            heightCm = mode.heightCm,
            weightDg = 0,
            type = 2,
        )
        io.writeFrames(
            GattUuids.USER_PROFILE,
            ScaleCrypto.encryptAesCtr(workKey, commit.encode()),
            FrameMagic.EncryptedUpstream,
        )
        delay(timing.commitSettle)

        val estimates = BodyCompositionEstimator.estimate(
            weightKg = bia.weightKg,
            fatPct = bia.fatPct,
            heightCm = mode.heightCm,
            sex = mode.sex,
            age = mode.age,
        )
        return MeasurementResult(
            weightKg = bia.weightKg,
            fatPct = bia.fatPct,
            heartRate = bia.heartRate,
            feetImp = bia.feet,
            handsImp = bia.hands,
            deviceTs = deviceTsOf(bia),
            estimates = estimates,
        )
    }

    /**
     * 0x97: 38B BIA (0xCD decrypt / 0xBD plaintext, SUMMARY section 9); isPlausible filter
     * plus the "wait for last frame" switch (PLAN risk 7).
     */
    private suspend fun awaitBia(workKey: ByteArray, io: FrameIo): BiaMeasurement {
        var sawImplausible = false
        fun timeoutError() =
            if (sawImplausible) SessionException(SessionError.MacInvalid)
            else SessionException(SessionError.Timeout("bia"))

        try {
            val completed = withTimeoutOrNull(options.timing.bia) {
                val done = CompletableDeferred<BiaMeasurement>()
                val consumer = launch {
                    try {
                        var quiet: Job? = null
                        while (true) {
                            val frame = io.receive(GattUuids.BIA, "bia")
                            val plain = decryptBody(workKey, frame, "bia")
                            val bia = try {
                                BiaMeasurement.decode(plain)
                            } catch (e: IllegalArgumentException) {
                                sawImplausible = true
                                options.onLog?.invoke(
                                    "BIA payload ${plain.size}B undecodable (mac wrong?): ${plain.toHex()}",
                                )
                                continue
                            }
                            if (options.rejectImplausibleBia && !bia.isPlausible()) {
                                sawImplausible = true
                                options.onLog?.invoke(
                                    "BIA implausible frame filtered (mac wrong?): ${plain.toHex()}",
                                )
                                continue
                            }
                            options.onLog?.invoke(
                                "BIA decoded: weight=${bia.weightKg}kg fat=${bia.fatPct}% " +
                                        "hr=${bia.heartRate} feet=${bia.feet} hands=${bia.hands.size}",
                            )
                            if (options.acceptFirstBiaFrame) {
                                done.complete(bia)
                                break
                            }
                            // "wait last frame": take the last frame inside the quiet window
                            quiet?.cancel()
                            val captured = bia
                            quiet = launch {
                                delay(options.biaQuietWindow)
                                done.complete(captured)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        done.completeExceptionally(e)
                    }
                }
                try {
                    done.await()
                } finally {
                    consumer.cancel()
                }
            }
            return completed ?: throw timeoutError()
        } catch (e: TimeoutCancellationException) {
            throw timeoutError()
        }
    }

    // ---- query steps (soft timeout; results go to hardwareInfo) ----

    private suspend fun querySerial(io: FrameIo) {
        val frame = query(io, GattUuids.SERIAL, "0x5A")
        if (frame != null) {
            _hardwareInfo.value = _hardwareInfo.value.copy(serial = textOf(frame))
        }
    }

    private suspend fun queryModel(io: FrameIo) {
        val frame = query(io, GattUuids.MODEL, "0x7A")
        if (frame != null) {
            _hardwareInfo.value = _hardwareInfo.value.copy(model = textOf(frame))
        }
    }

    private suspend fun queryConfig(io: FrameIo) {
        val frame = query(io, GattUuids.CONFIG, "0x62")
        if (frame != null && frame.payload.isNotEmpty()) {
            _hardwareInfo.value =
                _hardwareInfo.value.copy(configCode = frame.payload[0].toInt() and 0xFF)
        }
    }

    private suspend fun queryHistory(io: FrameIo) {
        val frame = query(io, GattUuids.OFFLINE_HISTORY, "0x8F")
        if (frame != null) {
            _hardwareInfo.value = _hardwareInfo.value.copy(historyRaw = frame.payload.toHex())
        }
    }

    private suspend fun query(io: FrameIo, uuid: String, step: String): Frame? {
        io.writeRaw(uuid, ProtocolConstants.QUERY_ARM)
        return io.awaitSoft(uuid, step, options.timing.query)
    }

    // ---- helpers ----

    /** 0xCD -> AES(WorkKey) decrypt; 0xBD -> plaintext as-is (same rule for 0x97 and 0x2D). */
    private fun decryptBody(workKey: ByteArray, frame: Frame, step: String): ByteArray =
        when (frame.magic) {
            FrameMagic.EncryptedDownstream -> try {
                ScaleCrypto.decryptAesCtr(workKey, frame.payload)
            } catch (e: IllegalArgumentException) {
                throw SessionException(SessionError.UnexpectedReply(step), cause = e)
            }

            FrameMagic.PlainDownstream -> frame.payload
            else -> throw SessionException(
                SessionError.UnexpectedReply(step),
                "unexpected upstream magic ${frame.magic}"
            )
        }

    /** Short status reply `bd 04 00 XX ...` -> payload first byte is the status code. */
    private fun statusCode(frame: Frame, step: String): Int {
        if (frame.payload.isEmpty()) throw SessionException(SessionError.UnexpectedReply(step))
        return frame.payload[0].toInt() and 0xFF
    }

    private fun textOf(frame: Frame): String =
        frame.payload.decodeToString().trim(Char(0), ' ', '\n', '\r')

    /** Device timestamp text: `Y-m-d-h-m-s+dow` (Measurement.deviceTs). */
    private fun deviceTsOf(bia: BiaMeasurement): String {
        fun p2(v: Int) = v.toString().padStart(2, '0')
        // 26B 真机包不带星期（dayOfWeek=0）——按日期推算；38B 布局直接用包内值
        val dow = bia.dayOfWeek.takeIf { it in 1..7 }
            ?: runCatching { LocalDate(bia.year, bia.month, bia.day).dayOfWeek.isoDayNumber }
                .getOrDefault(0)
        return "${bia.year}-${p2(bia.month)}-${p2(bia.day)}-${p2(bia.hour)}-${p2(bia.minute)}-${
            p2(
                bia.second
            )
        }+$dow"
    }
}
