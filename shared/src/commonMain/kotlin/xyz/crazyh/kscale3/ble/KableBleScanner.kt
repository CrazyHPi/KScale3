package xyz.crazyh.kscale3.ble

import com.juul.kable.Advertisement
import com.juul.kable.Scanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Kable 扫描实装：映射 Advertisement → [BleAdvertisement]（名称过滤由调用方约定）。 */
class KableBleScanner(private val scanner: Scanner<out Advertisement>) : BleScanner {

    override fun scan(): Flow<BleAdvertisement> = scanner.advertisements.map { adv ->
        BleAdvertisement(
            name = adv.name ?: adv.peripheralName,
            identifier = adv.identifier,
            rssi = adv.rssi,
        )
    }
}
