package xyz.crazyh.kscale3.ble

import com.juul.kable.Identifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * BLE 外设薄封装（Kable 之上的接口，便于 Fake 测试）。
 * 写入的 safeWrite 语义（失败且报 not permitted 时翻转 with-response 重试，scale3.py L176）
 * 由 session 层实现。
 */
interface BlePeripheral {
    /** 平台标识：Android 为物理 MAC，iOS 为 CoreBluetooth UUID（⚠️ 不可作 RootKey 输入）。 */
    val identifier: Identifier

    val name: String?

    /** 连接状态（会话层据此映射 SessionError.Disconnected）。 */
    val isConnected: StateFlow<Boolean>

    suspend fun connect()

    suspend fun disconnect()

    /** 按特征 [uuid] 写入；[withResponse] 指示写-有响应/写-无响应。 */
    suspend fun write(uuid: String, data: ByteArray, withResponse: Boolean)

    /**
     * 订阅特征 notify/indicate。
     * [onSubscribed] 在 CCCD 订阅真正生效后回调（Kable `onSubscription` 语义）：
     * 复用连接时订阅是异步建立的，会话层必须先等到它再发首个写入，否则即时应答（如单位设置的
     * Indication）会被漏掉。
     */
    fun observe(uuid: String, onSubscribed: (() -> Unit)? = null): Flow<ByteArray>
}
