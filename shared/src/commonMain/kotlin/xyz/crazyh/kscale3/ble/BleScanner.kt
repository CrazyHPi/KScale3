package xyz.crazyh.kscale3.ble

import kotlinx.coroutines.flow.Flow

/** 扫描到的广播条目。 */
data class BleAdvertisement(
    val name: String?,
    /** Android 为 MAC；iOS 为 CoreBluetooth UUID（⚠️ RootKey 需手填秤身标签 MAC）。 */
    val identifier: String,
    val rssi: Int,
)

/** BLE 扫描薄封装。名称过滤 `HaigeBLE|Scale 3|HUAWEI Scale` 由调用方约定。 */
interface BleScanner {
    fun scan(): Flow<BleAdvertisement>
}
