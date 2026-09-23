package xyz.crazyh.kscale3.ble

import com.juul.kable.Advertisement
import com.juul.kable.Scanner
import kotlinx.coroutines.flow.Flow

/** Kable 扫描实现桩（Phase 2 补全：映射 Advertisement → [BleAdvertisement]）。 */
class KableBleScanner(private val scanner: Scanner<Advertisement>) : BleScanner {

    override fun scan(): Flow<BleAdvertisement> = TODO("Phase 2: BLE + session")
}
