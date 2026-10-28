package xyz.crazyh.kscale3.ble

import com.juul.kable.Peripheral

internal actual fun createKablePeripheral(identifier: String): Peripheral =
    Peripheral(identifier)
