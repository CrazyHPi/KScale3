package xyz.crazyh.kscale3.ble

import com.juul.kable.Peripheral

/**
 * 按平台连接标识构造 Kable Peripheral。
 *
 * Kable 的 `Peripheral(identifier)` 只在各平台源集声明（common 不可见）：
 * Android 接受物理 MAC，Apple 接受 CoreBluetooth UUID。String → Identifier
 * 的平台差异（Android 校验 MAC / Apple `Uuid.parse`）收敛在这里。
 *
 * Apple 端 `Peripheral(uuid)` 依赖 CoreBluetooth 的缓存：App 重启/蓝牙重置后可能
 * 查不到已保存的 UUID（抛 `NoSuchElementException`），因此实际实现是 suspend 的，
 * 必要时先扫描再构造（调用方必须处理该异常，不能让它冒到 UI 线程）。
 */
internal expect suspend fun createKablePeripheral(identifier: String): Peripheral