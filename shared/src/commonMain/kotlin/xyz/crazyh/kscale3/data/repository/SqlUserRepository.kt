package xyz.crazyh.kscale3.data.repository

import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import xyz.crazyh.kscale3.data.db.KScale3Database
import xyz.crazyh.kscale3.data.db.User_profile
import xyz.crazyh.kscale3.data.db.instantFromEpochMs
import xyz.crazyh.kscale3.data.db.sexFromDb
import xyz.crazyh.kscale3.data.db.toDb
import xyz.crazyh.kscale3.data.db.toEpochMs
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.domain.UserProfileDraft

/** [UserRepository] 的 SQLDelight 实装（`user_profile` 表）。 */
class SqlUserRepository(
    private val db: KScale3Database,
    private val now: () -> Instant = { kotlin.time.Clock.System.now() },
) : UserRepository {

    private val queries get() = db.user_profileQueries

    override fun getAll(): Flow<List<UserProfile>> =
        queries.selectAll().asFlow().map { query -> query.executeAsList().map { it.toDomain() } }

    override fun byId(id: Long): Flow<UserProfile?> =
        queries.byId(id).asFlow().map { query -> query.executeAsOneOrNull()?.toDomain() }

    override suspend fun insert(draft: UserProfileDraft): Long = db.transactionWithResult {
        queries.insert(
            draft.name,
            draft.sex.toDb(),
            draft.age.toLong(),
            draft.heightCm.toLong(),
            draft.goalWeightKg,
            null, // huid — 注册后才有
            null, // registered_at
            null, // last_weight_kg — 首次测量后回填
            now().toEpochMs(),
        )
        db.utilQueries.lastInsertRowId().executeAsOne()
    }

    override suspend fun update(user: UserProfile) {
        db.transaction {
            queries.updateProfile(
                user.name,
                user.sex.toDb(),
                user.age.toLong(),
                user.heightCm.toLong(),
                user.goalWeightKg,
                user.id,
            )
        }
    }

    override suspend fun markRegistered(id: Long, huid: String, registeredAt: Instant) {
        db.transaction {
            queries.markRegistered(huid, registeredAt.toEpochMs(), id)
        }
    }

    override suspend fun delete(id: Long) {
        db.transaction {
            // 同事务先清该用户的测量记录再删档案（measurement 表无 ON DELETE CASCADE，
            // 否则会留下孤儿记录或触发外键约束）
            db.measurementQueries.deleteForUser(id)
            queries.delete(id)
        }
    }
}

private fun User_profile.toDomain(): UserProfile = UserProfile(
    id = id,
    name = name,
    sex = sexFromDb(sex),
    age = age.toInt(),
    heightCm = height_cm.toInt(),
    goalWeightKg = goal_weight,
    huid = huid,
    registeredAt = registered_at?.let { instantFromEpochMs(it) },
    createdAt = instantFromEpochMs(created_at),
    lastWeightKg = last_weight_kg,
)
