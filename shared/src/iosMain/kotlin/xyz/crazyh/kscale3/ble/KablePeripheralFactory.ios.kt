package xyz.crazyh.kscale3.ble

import com.juul.kable.Peripheral
import com.juul.kable.toIdentifier

internal actual fun createKablePeripheral(identifier: String): Peripheral =
    Peripheral(identifier.toIdentifier())
