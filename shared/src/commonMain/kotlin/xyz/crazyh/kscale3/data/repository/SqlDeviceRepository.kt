package xyz.crazyh.kscale3.data.repository

import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.crazyh.kscale3.data.db.KScale3Database
import xyz.crazyh.kscale3.data.db.Paired_device
import xyz.crazyh.kscale3.data.db.instantFromEpochMs
import xyz.crazyh.kscale3.data.db.toEpochMs
import xyz.crazyh.kscale3.domain.PairedDevice
import xyz.crazyh.kscale3.domain.WeightUnit

/** [DeviceRepository] 的 SQLDelight 实装（`paired_device` 表，单台秤）。 */
class SqlDeviceRepository(
    private val db: KScale3Database,
) : DeviceRepository {

    private val queries get() = db.paired_deviceQueries

    override fun observe(): Flow<PairedDevice?> =
        queries.get().asFlow().map { query -> query.executeAsOneOrNull()?.toDomain() }

    override suspend fun save(device: PairedDevice) {
        db.transaction {
            // 同一台秤重新配对（MAC 不变，iOS 可能换 bleId）时保留上次获得的显示单位
            val previous = queries.get().executeAsOneOrNull()
            val weightUnit = device.weightUnit
                ?: previous?.takeIf { it.mac == device.mac }
                    ?.weight_unit?.toInt()?.let(WeightUnit::fromCode)
            queries.deleteAll()
            queries.insert(
                device.mac,
                device.bleId,
                device.name,
                device.model,
                device.sn,
                weightUnit?.code?.toLong(),
                device.lastSeen.toEpochMs(),
            )
        }
    }

    override suspend fun updateWeightUnit(mac: String, unit: WeightUnit) {
        queries.updateWeightUnit(unit.code.toLong(), mac)
    }

    override suspend fun clear() {
        queries.deleteAll()
    }
}

private fun Paired_device.toDomain(): PairedDevice = PairedDevice(
    id = id,
    mac = mac,
    bleId = ble_id,
    name = name,
    model = model,
    sn = sn,
    weightUnit = weight_unit?.toInt()?.let(WeightUnit::fromCode),
    lastSeen = instantFromEpochMs(last_seen),
)
