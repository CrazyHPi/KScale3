package xyz.crazyh.kscale3.ble

import com.juul.kable.Peripheral

/**
 * 按平台连接标识构造 Kable Peripheral。
 *
 * Kable 的 `Peripheral(identifier)` 只在各平台源集声明（common 不可见）：
 * Android 接受物理 MAC，Apple 接受 CoreBluetooth UUID。String → Identifier
 * 的平台差异（Android 校验 MAC / Apple `Uuid.parse`）收敛在这里。
 */
internal expect fun createKablePeripheral(identifier: String): Peripheral
