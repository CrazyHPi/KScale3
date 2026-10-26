package xyz.crazyh.kscale3.ui.preview

import kotlinx.datetime.Instant
import xyz.crazyh.kscale3.domain.BodyCompositionEstimates
import xyz.crazyh.kscale3.domain.Measurement
import xyz.crazyh.kscale3.domain.MeasurementResult
import xyz.crazyh.kscale3.domain.PairedDevice
import xyz.crazyh.kscale3.domain.Sex
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.session.HardwareInfo

/** @Preview 用的示例数据（仅 UI 预览，不参与运行时逻辑）。 */

internal val previewDevice = PairedDevice(
    id = 1,
    mac = "C8:47:8C:12:34:56",
    bleId = "C8:47:8C:12:34:56",
    name = "HUAWEI Scale 3",
    model = "M00F7",
    sn = "SN1234567890",
    lastSeen = Instant.parse("2026-10-27T08:30:00Z"),
)

internal val previewUsers = listOf(
    UserProfile(
        id = 1,
        name = "张三",
        sex = Sex.Male,
        age = 30,
        heightCm = 175,
        goalWeightKg = 68.0,
        huid = "30033000012345678",
        registeredAt = Instant.parse("2026-09-01T10:00:00Z"),
        createdAt = Instant.parse("2026-09-01T09:00:00Z"),
        lastWeightKg = 70.2,
    ),
    UserProfile(
        id = 2,
        name = "李四",
        sex = Sex.Female,
        age = 28,
        heightCm = 162,
        goalWeightKg = null,
        huid = null,
        registeredAt = null,
        createdAt = Instant.parse("2026-09-10T09:00:00Z"),
        lastWeightKg = null,
    ),
)

internal val previewMeasurements = listOf(
    previewMeasurement(3, "2026-10-27T08:30:00Z", 70.2, 21.3, 72),
    previewMeasurement(2, "2026-10-20T08:10:00Z", 70.8, 21.8, 74),
    previewMeasurement(1, "2026-10-13T08:25:00Z", 71.4, 22.1, 71),
)

internal val previewResult = MeasurementResult(
    weightKg = 70.2,
    fatPct = 21.3,
    heartRate = 72,
    feetImp = listOf(520, 531, 542, 553, 564, 575),
    handsImp = listOf(610, 621, 632, 643, 654, 665),
    deviceTs = "2026-10-27-08-30-00",
    estimates = BodyCompositionEstimates(
        fatMassKg = 14.95,
        tbwKg = 38.61,
        boneKg = 2.95,
        muscleKg = 51.25,
        bmi = 22.9,
        bmr = 1520.0,
    ),
)

internal val previewHardwareInfo = HardwareInfo(
    serial = "SN1234567890",
    model = "M00F7",
    configCode = 1,
)

private fun previewMeasurement(
    id: Long,
    measuredAt: String,
    weightKg: Double,
    fatPct: Double,
    heartRate: Int,
): Measurement = Measurement(
    id = id,
    userId = 1,
    measuredAt = Instant.parse(measuredAt),
    weightKg = weightKg,
    fatPct = fatPct,
    heartRate = heartRate,
    feetImp = listOf(520, 531, 542, 553, 564, 575),
    handsImp = listOf(610, 621, 632, 643, 654, 665),
    deviceTs = measuredAt,
    fatMassKg = weightKg * fatPct / 100,
    tbwKg = weightKg * 0.55,
    boneKg = weightKg * 0.042,
    muscleKg = weightKg * 0.73,
    bmi = weightKg / (1.75 * 1.75),
    bmr = 1520.0,
)
