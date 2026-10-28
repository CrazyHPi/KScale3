@file:OptIn(com.juul.kable.ExperimentalKableApi::class)

package xyz.crazyh.kscale3.ble

import com.juul.kable.Characteristic
import com.juul.kable.Identifier
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.WriteType
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

    override val identifier: Identifier get() = peripheral.identifier

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
        peripheral.write(
            characteristic(uuid),
            data,
            if (withResponse) WriteType.WithResponse else WriteType.WithoutResponse,
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
    private fun characteristic(uuid: String): Characteristic {
        val target = uuid.lowercase()
        return peripheral.services.value.orEmpty()
            .asSequence()
            .flatMap { service -> service.characteristics.asSequence() }
            .firstOrNull { it.characteristicUuid.toString().lowercase() == target }
            ?: error("Characteristic $uuid not discovered on ${peripheral.identifier}")
    }
}
