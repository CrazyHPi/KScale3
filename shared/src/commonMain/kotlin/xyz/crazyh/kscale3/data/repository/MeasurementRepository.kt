package xyz.crazyh.kscale3.data.repository

import kotlinx.coroutines.flow.Flow
import xyz.crazyh.kscale3.domain.Measurement

/** 测量记录仓库（对应 `measurement` 表）。 */
interface MeasurementRepository {
    fun forUser(userId: Long): Flow<List<Measurement>>

    fun byId(id: Long): Flow<Measurement?>

    /** 全部用户（档案页趋势/记录与成员摘要）。 */
    fun getAll(): Flow<List<Measurement>>

    /** 入库（同事务回写 user_profile.last_weight_kg）。 */
    suspend fun insert(measurement: Measurement): Long

    suspend fun delete(id: Long)
}
