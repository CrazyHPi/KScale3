package xyz.crazyh.kscale3.ble

import com.juul.kable.Peripheral
import kotlinx.coroutines.flow.Flow

/** Kable 实现桩（Phase 2 补全：连接管理、write 语义、按 UUID 订阅 notify）。 */
class KableBlePeripheral(private val peripheral: Peripheral) : BlePeripheral {

    override val identifier: String get() = TODO("Phase 2: BLE + session")

    override val name: String? get() = TODO("Phase 2: BLE + session")

    override suspend fun connect(): Unit = TODO("Phase 2: BLE + session")

    override suspend fun disconnect(): Unit = TODO("Phase 2: BLE + session")

    override suspend fun write(uuid: String, data: ByteArray, withResponse: Boolean): Unit =
        TODO("Phase 2: BLE + session")

    override fun observe(uuid: String): Flow<ByteArray> = TODO("Phase 2: BLE + session")
}
