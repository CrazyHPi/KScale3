package xyz.crazyh.kscale3.domain

import kotlinx.datetime.Instant

/** 已配对的秤（`paired_device` 表，单台秤）。 */
data class PairedDevice(
    val id: Long,
    /** 真实物理 MAC（RootKey 输入） */
    val mac: String,
    val name: String,
    /** 硬件型号（0x7A，如 M00F7） */
    val model: String?,
    /** 序列号（0x5A） */
    val sn: String?,
    val lastSeen: Instant,
)
