package xyz.crazyh.kscale3.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import xyz.crazyh.kscale3.domain.UserProfile
import xyz.crazyh.kscale3.domain.UserProfileDraft

/** 用户档案仓库（实现见后续阶段；对应 `user_profile` 表）。 */
interface UserRepository {
    fun getAll(): Flow<List<UserProfile>>

    fun byId(id: Long): Flow<UserProfile?>

    suspend fun insert(draft: UserProfileDraft): Long

    suspend fun update(user: UserProfile)

    /** Mode 1 成功后落 HUID（注册时间 = 当时时刻）。 */
    suspend fun markRegistered(id: Long, huid: String, registeredAt: Instant)

    /** 删除档案及其全部测量记录（同事务）。秤上已注册的 HUID 无法经协议删除。 */
    suspend fun delete(id: Long)
}
