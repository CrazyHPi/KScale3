package xyz.crazyh.kscale3.data.repository

import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.crazyh.kscale3.data.db.KScale3Database
import xyz.crazyh.kscale3.data.db.Measurement as MeasurementRow
import xyz.crazyh.kscale3.data.db.instantFromEpochMs
import xyz.crazyh.kscale3.data.db.toEpochMs
import xyz.crazyh.kscale3.data.db.toImpedanceCsv
import xyz.crazyh.kscale3.data.db.toImpedanceList
import xyz.crazyh.kscale3.domain.Measurement

/** [MeasurementRepository] 的 SQLDelight 实装（`measurement` 表）。 */
class SqlMeasurementRepository(
    private val db: KScale3Database,
) : MeasurementRepository {

    private val queries get() = db.measurementQueries

    override fun forUser(userId: Long): Flow<List<Measurement>> =
        queries.forUser(userId).asFlow().map { query -> query.executeAsList().map { it.toDomain() } }

    override fun getAll(): Flow<List<Measurement>> =
        queries.selectAll().asFlow().map { query -> query.executeAsList().map { it.toDomain() } }

    override suspend fun insert(measurement: Measurement): Long = db.transactionWithResult {
        queries.insert(
            measurement.userId,
            measurement.measuredAt.toEpochMs(),
            measurement.weightKg,
            measurement.fatPct,
            measurement.heartRate.toLong(),
            measurement.feetImp.toImpedanceCsv(),
            measurement.handsImp.toImpedanceCsv(),
            measurement.deviceTs,
            measurement.fatMassKg,
            measurement.tbwKg,
            measurement.boneKg,
            measurement.muscleKg,
            measurement.bmi,
            measurement.bmr,
        )
        val id = db.utilQueries.lastInsertRowId().executeAsOne()
        // 0x31 type=0 的 "上次测量值" 同事务回写 user_profile（也令用户 Flow 失效重发）
        db.user_profileQueries.updateLastWeight(measurement.weightKg, measurement.userId)
        id
    }

    override suspend fun delete(id: Long) {
        db.transaction {
            queries.delete(id)
        }
    }
}

private fun MeasurementRow.toDomain(): Measurement = Measurement(
    id = id,
    userId = user_id,
    measuredAt = instantFromEpochMs(measured_at),
    weightKg = weight_kg,
    fatPct = fat_pct,
    heartRate = heart_rate.toInt(),
    feetImp = feet_imp.toImpedanceList(),
    handsImp = hands_imp.toImpedanceList(),
    deviceTs = device_ts,
    fatMassKg = fat_mass_kg,
    tbwKg = tbw_kg,
    boneKg = bone_kg,
    muscleKg = muscle_kg,
    bmi = bmi,
    bmr = bmr,
)
