@file:OptIn(com.juul.kable.ExperimentalKableApi::class)

package xyz.crazyh.kscale3.ble

import com.juul.kable.DiscoveredCharacteristic
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.writeWithoutResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Kable 实装：连接管理、写-有/无响应、按 UUID 订阅 notify（会话期间常驻）。
 * 特征按 UUID 在服务发现结果中解析（TECH.md §4：勿用 handle）。
 */
class KableBlePeripheral(private val peripheral: Peripheral) : BlePeripheral {

    override val identifier: String get() = peripheral.identifier.toString()

    override val name: String? get() = peripheral.name

    override val isConnected: StateFlow<Boolean> = peripheral.state
        .map { it is State.Connected }
        .stateIn(peripheral.scope, SharingStarted.Eagerly, false)

    override suspend fun connect() {
        peripheral.connect()
    }

    override suspend fun disconnect() {
        peripheral.disconnect()
    }

    override suspend fun write(uuid: String, data: ByteArray, withResponse: Boolean) {
        val characteristic = characteristic(uuid)
        // 协议里的"无响应写"是意图；GATT 特征未必支持 Write Without Response。
        // iOS/CoreBluetooth 会静默忽略这类写（日志："does not specify the Write Without
        // Response property - ignoring response-less write"），不抛异常，导致命令没发出、
        // 上层只能等到超时。这里按特征实际属性选择写类型。
        val supportsWithoutResponse = characteristic.properties.writeWithoutResponse
        peripheral.write(
            characteristic,
            data,
            if (withResponse || !supportsWithoutResponse) {
                WriteType.WithResponse
            } else {
                WriteType.WithoutResponse
            },
        )
    }

    override fun observe(uuid: String, onSubscribed: (() -> Unit)?): Flow<ByteArray> = flow {
        emitAll(
            peripheral.observe(characteristic(uuid)) {
                // Kable: 在 CCCD 订阅（setNotification/writeDescriptor）完成后回调
                onSubscribed?.invoke()
            }
        )
    }

    /** 在服务发现结果里按特征 UUID 解析（连接后可用；多服务同 UUID 时取首个）。 */
    private fun characteristic(uuid: String): DiscoveredCharacteristic {
        val target = uuid.lowercase()
        return peripheral.services.value.orEmpty()
            .asSequence()
            .flatMap { service -> service.characteristics.asSequence() }
            .firstOrNull { it.characteristicUuid.toString().lowercase() == target }
            ?: error("Characteristic $uuid not discovered on ${peripheral.identifier}")
    }
}
