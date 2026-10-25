package xyz.crazyh.kscale3.data.repository

import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.crazyh.kscale3.data.db.KScale3Database
import xyz.crazyh.kscale3.data.db.Paired_device
import xyz.crazyh.kscale3.data.db.instantFromEpochMs
import xyz.crazyh.kscale3.data.db.toEpochMs
import xyz.crazyh.kscale3.domain.PairedDevice

/** [DeviceRepository] 的 SQLDelight 实装（`paired_device` 表，单台秤）。 */
class SqlDeviceRepository(
    private val db: KScale3Database,
) : DeviceRepository {

    private val queries get() = db.paired_deviceQueries

    override fun observe(): Flow<PairedDevice?> =
        queries.get().asFlow().map { query -> query.executeAsOneOrNull()?.toDomain() }

    override suspend fun save(device: PairedDevice) {
        db.transaction {
            queries.deleteAll()
            queries.insert(
                device.mac,
                device.bleId,
                device.name,
                device.model,
                device.sn,
                device.lastSeen.toEpochMs(),
            )
        }
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
    lastSeen = instantFromEpochMs(last_seen),
)
