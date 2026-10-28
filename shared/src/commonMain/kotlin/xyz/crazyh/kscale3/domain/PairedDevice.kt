package xyz.crazyh.kscale3.domain

import kotlinx.datetime.Instant

/** 已配对的秤（`paired_device` 表，单台秤）。 */
data class PairedDevice(
    val id: Long,
    /** 真实物理 MAC（RootKey 输入，秤身标签） */
    val mac: String,
    /**
     * BLE 连接标识（Kable `Peripheral(identifier)` 输入）：Android = 物理 MAC；
     * iOS = CoreBluetooth UUID（⚠️ 与 [mac] 不同，扫描时记录）。
     */
    val bleId: String,
    val name: String,
    /** 硬件型号（0x7A，如 M00F7） */
    val model: String?,
    /** 序列号（0x5A） */
    val sn: String?,
    /** 秤最近一次上报的显示单位（null = 尚未获得；设置/查询/重连自动查询后回写） */
    val weightUnit: WeightUnit? = null,
    val lastSeen: Instant,
)
