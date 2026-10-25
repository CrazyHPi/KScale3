package xyz.crazyh.kscale3.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import xyz.crazyh.kscale3.data.db.KScale3Database
import xyz.crazyh.kscale3.domain.Measurement
import xyz.crazyh.kscale3.domain.PairedDevice
import xyz.crazyh.kscale3.domain.Sex
import xyz.crazyh.kscale3.domain.UserProfileDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 三仓库 CRUD（JdbcSqliteDriver 内存库，SQLDelight 生成代码 + 映射端到端）。 */
class SqlRepositoriesTest {

    private val t0 = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val draft = UserProfileDraft(
        name = "tester",
        sex = Sex.Male,
        age = 30,
        heightCm = 175,
        goalWeightKg = 65.5
    )

    private fun createDb(): KScale3Database {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KScale3Database.Schema.create(driver)
        return KScale3Database(driver)
    }

    // ---- user ----

    @Test
    fun user_insert_and_byId() = runTest {
        val db = createDb()
        val repo = SqlUserRepository(db, now = { t0 })
        val id = repo.insert(draft)

        assertTrue(id > 0)
        val user = repo.byId(id).first()!!
        assertEquals("tester", user.name)
        assertEquals(Sex.Male, user.sex)
        assertEquals(30, user.age)
        assertEquals(175, user.heightCm)
        assertEquals(65.5, user.goalWeightKg)
        assertNull(user.huid)
        assertNull(user.registeredAt)
        assertNull(user.lastWeightKg)
        assertEquals(t0, user.createdAt)
    }

    @Test
    fun user_getAll_update_delete() = runTest {
        val db = createDb()
        val repo = SqlUserRepository(db, now = { t0 })
        val id = repo.insert(draft)
        assertEquals(1, repo.getAll().first().size)

        val user = repo.byId(id).first()!!
        repo.update(user.copy(name = "renamed", sex = Sex.Female, age = 31, goalWeightKg = null))

        val updated = repo.byId(id).first()!!
        assertEquals("renamed", updated.name)
        assertEquals(Sex.Female, updated.sex)
        assertEquals(31, updated.age)
        assertNull(updated.goalWeightKg)

        repo.delete(id)
        assertNull(repo.byId(id).first())
        assertEquals(emptyList(), repo.getAll().first())
    }

    @Test
    fun user_markRegistered_setsHuid() = runTest {
        val db = createDb()
        val repo = SqlUserRepository(db, now = { t0 })
        val id = repo.insert(draft)

        val registeredAt = Instant.fromEpochMilliseconds(1_700_000_100_000)
        repo.markRegistered(id, "30033000012345678", registeredAt)

        val user = repo.byId(id).first()!!
        assertEquals("30033000012345678", user.huid)
        assertEquals(registeredAt, user.registeredAt)
    }

    // ---- measurement ----

    private fun measurement(userId: Long, weightKg: Double, measuredAt: Instant) = Measurement(
        id = 0,
        userId = userId,
        measuredAt = measuredAt,
        weightKg = weightKg,
        fatPct = 21.5,
        heartRate = 65,
        feetImp = listOf(256, 257, 258, 259, 260, 261),
        handsImp = listOf(512, 513, 514, 515, 516, 517),
        deviceTs = "2026-09-22-14-30-05+2",
        fatMassKg = 15.5875,
        tbwKg = 41.546125,
        boneKg = 3.0391325,
        muscleKg = 28.45625,
        bmi = 23.673469387755102,
        bmr = 1648.75,
    )

    @Test
    fun measurement_insert_forUser_orderedDesc_and_roundTrip() = runTest {
        val db = createDb()
        val users = SqlUserRepository(db, now = { t0 })
        val repo = SqlMeasurementRepository(db)
        val userId = users.insert(draft)

        val older = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val newer = Instant.fromEpochMilliseconds(1_700_000_500_000)
        repo.insert(measurement(userId, weightKg = 70.0, measuredAt = older))
        repo.insert(measurement(userId, weightKg = 72.5, measuredAt = newer))

        val list = repo.forUser(userId).first()
        assertEquals(2, list.size)
        // measured_at DESC
        assertEquals(72.5, list[0].weightKg)
        assertEquals(70.0, list[1].weightKg)

        // 阻抗/真值/估算快照完整往返
        val row = list[0]
        assertEquals(listOf(256, 257, 258, 259, 260, 261), row.feetImp)
        assertEquals(listOf(512, 513, 514, 515, 516, 517), row.handsImp)
        assertEquals(21.5, row.fatPct)
        assertEquals(65, row.heartRate)
        assertEquals("2026-09-22-14-30-05+2", row.deviceTs)
        assertEquals(1648.75, row.bmr)
    }

    @Test
    fun measurement_insert_updatesUserLastWeight() = runTest {
        val db = createDb()
        val users = SqlUserRepository(db, now = { t0 })
        val repo = SqlMeasurementRepository(db)
        val userId = users.insert(draft)
        assertNull(users.byId(userId).first()!!.lastWeightKg)

        repo.insert(measurement(userId, weightKg = 72.5, measuredAt = t0))
        assertEquals(72.5, users.byId(userId).first()!!.lastWeightKg)

        // 更新到最新一次测量
        repo.insert(
            measurement(
                userId,
                weightKg = 71.0,
                measuredAt = Instant.fromEpochMilliseconds(t0.toEpochMilliseconds() + 1)
            )
        )
        assertEquals(71.0, users.byId(userId).first()!!.lastWeightKg)
    }

    @Test
    fun measurement_delete() = runTest {
        val db = createDb()
        val users = SqlUserRepository(db, now = { t0 })
        val repo = SqlMeasurementRepository(db)
        val userId = users.insert(draft)
        val id = repo.insert(measurement(userId, weightKg = 70.0, measuredAt = t0))

        repo.delete(id)
        assertEquals(emptyList(), repo.forUser(userId).first())
    }

    // ---- device ----

    @Test
    fun device_save_replacesSingleRow() = runTest {
        val db = createDb()
        val repo = SqlDeviceRepository(db)
        assertNull(repo.observe().first())

        repo.save(
            PairedDevice(
                id = 0,
                mac = "AA:BB:CC:DD:EE:FF",
                bleId = "AA:BB:CC:DD:EE:FF",
                name = "HaigeBLE",
                model = "M00F7",
                sn = "SN42",
                lastSeen = t0,
            ),
        )
        val device = repo.observe().first()!!
        assertEquals("AA:BB:CC:DD:EE:FF", device.mac)
        assertEquals("AA:BB:CC:DD:EE:FF", device.bleId)
        assertEquals("M00F7", device.model)
        assertEquals("SN42", device.sn)
        assertEquals(t0, device.lastSeen)

        // 单台秤：save 覆盖旧记录（iOS 场景 bleId=CoreBluetooth UUID ≠ mac）
        repo.save(
            PairedDevice(
                id = 0,
                mac = "11:22:33:44:55:66",
                bleId = "00000000-0000-0000-0000-00000000f00d",
                name = "HaigeBLE-2",
                model = null,
                sn = null,
                lastSeen = Instant.fromEpochMilliseconds(t0.toEpochMilliseconds() + 1),
            ),
        )
        val replaced = repo.observe().first()!!
        assertEquals("11:22:33:44:55:66", replaced.mac)
        assertEquals("00000000-0000-0000-0000-00000000f00d", replaced.bleId)
        assertNull(replaced.model)
    }

    @Test
    fun device_clear_removesRow() = runTest {
        val db = createDb()
        val repo = SqlDeviceRepository(db)
        repo.save(
            PairedDevice(
                id = 0,
                mac = "AA:BB:CC:DD:EE:FF",
                bleId = "AA:BB:CC:DD:EE:FF",
                name = "HaigeBLE",
                model = null,
                sn = null,
                lastSeen = t0,
            ),
        )
        assertNotNull(repo.observe().first())

        repo.clear()
        assertNull(repo.observe().first())
    }
}
