package xyz.crazyh.kscale3.data.repository

import kotlinx.coroutines.flow.Flow
import xyz.crazyh.kscale3.domain.PairedDevice

/** 配对设备仓库（对应 `paired_device` 表，单台秤）。 */
interface DeviceRepository {
    fun observe(): Flow<PairedDevice?>

    /** 保存/更新配对的秤（覆盖旧记录）。 */
    suspend fun save(device: PairedDevice)

    /** 忘记已配对的秤（仅清 `paired_device`；用户档案/HUID/测量记录不受影响）。 */
    suspend fun clear()
}
