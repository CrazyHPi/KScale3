package xyz.crazyh.kscale3.ble

import com.juul.kable.Peripheral

internal actual suspend fun createKablePeripheral(identifier: String): Peripheral =
    Peripheral(identifier)