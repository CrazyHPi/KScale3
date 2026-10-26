package xyz.crazyh.kscale3.ble

import kotlinx.coroutines.flow.Flow

data class BleAdvertisement(
    val name: String?,
    /** Android 为 MAC；iOS 为 CoreBluetooth UUID（⚠️ RootKey 需手填秤身标签 MAC）。 */
    val identifier: String,
    val rssi: Int,
)

/** BLE 扫描薄封装；疑似秤的排序由调用方决定。 */
interface BleScanner {
    fun scan(): Flow<BleAdvertisement>
}
