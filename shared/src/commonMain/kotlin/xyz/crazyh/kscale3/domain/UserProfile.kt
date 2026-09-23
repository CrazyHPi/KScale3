package xyz.crazyh.kscale3.domain

import kotlinx.datetime.Instant

/**
 * 本地用户档案（对应 `user_profile` 表）。
 * 每档案独立 HUID（注册到秤后写入），测量归属 = 称前选用户 + 秤 0x31 Code 确认。
 */
data class UserProfile(
    val id: Long,
    val name: String,
    val sex: Sex,
    val age: Int,
    val heightCm: Int,
    val goalWeightKg: Double?,
    /** 注册到秤后才有（`"300330000"`+8 位随机数字） */
    val huid: String?,
    /** null = 未注册 */
    val registeredAt: Instant?,
    val createdAt: Instant,
)
