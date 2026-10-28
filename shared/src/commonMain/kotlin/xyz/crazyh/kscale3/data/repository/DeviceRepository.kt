package xyz.crazyh.kscale3.data.repository

import kotlinx.coroutines.flow.Flow
import xyz.crazyh.kscale3.domain.PairedDevice
import xyz.crazyh.kscale3.domain.WeightUnit

/** 配对设备仓库（对应 `paired_device` 表，单台秤）。 */
interface DeviceRepository {
    fun observe(): Flow<PairedDevice?>

    /** 保存/更新配对的秤（覆盖旧记录）。同一 MAC 重新保存时保留已持久化的显示单位。 */
    suspend fun save(device: PairedDevice)

    /** 记录秤最近一次上报的显示单位（按 MAC 限定；设备已换行时不写入）。 */
    suspend fun updateWeightUnit(mac: String, unit: WeightUnit)

    /** 忘记已配对的秤（仅清 `paired_device`；用户档案/HUID/测量记录不受影响）。 */
    suspend fun clear()
}
