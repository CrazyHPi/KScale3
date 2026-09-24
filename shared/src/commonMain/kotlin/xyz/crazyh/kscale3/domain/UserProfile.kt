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
    /**
     * 上次测量体重 kg（0x31 type=0 的 Estimated Weight 字段，"日常用上次测量值"）。
     * 由数据层从最近测量回填；null/首次 → 0。配对流程改用 0x2D 皮重，不读此字段。
     */
    val lastWeightKg: Double? = null,
)
