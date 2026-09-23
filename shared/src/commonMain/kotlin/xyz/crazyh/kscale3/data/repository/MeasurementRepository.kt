package xyz.crazyh.kscale3.data.repository

import kotlinx.coroutines.flow.Flow
import xyz.crazyh.kscale3.domain.Measurement

/** 测量记录仓库（对应 `measurement` 表）。 */
interface MeasurementRepository {
    fun forUser(userId: Long): Flow<List<Measurement>>

    suspend fun insert(measurement: Measurement): Long

    suspend fun delete(id: Long)
}
