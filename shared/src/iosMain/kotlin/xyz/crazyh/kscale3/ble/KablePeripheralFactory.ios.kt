@file:OptIn(com.juul.kable.ObsoleteKableApi::class)

package xyz.crazyh.kscale3.ble

import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.toIdentifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

internal actual suspend fun createKablePeripheral(identifier: String): Peripheral {
    val uuid = identifier.toIdentifier()
    return try {
        Peripheral(uuid) { applyNotificationWorkaround() }
    } catch (e: NoSuchElementException) {
        // CoreBluetooth 的 retrievePeripheralsWithIdentifiers 只返回"系统已知"的外设：
        // App 重启、蓝牙开关重置或系统缓存淘汰后可能为空，直接抛 NoSuchElementException。
        // 此时先扫描到该 UUID，再用 Advertisement 构造 Peripheral（Kable 文档推荐做法）。
        // 秤唤醒时广播间隔很短，5s 足够；未唤醒时也没必要让 UI 干等太久。
        val advertisement = withTimeoutOrNull(5.seconds) {
            Scanner { }.advertisements.first { it.identifier == uuid }
        } ?: throw NoSuchElementException(
            "Peripheral with UUID $uuid not found (scan timed out); wake the scale and retry",
        )
        Peripheral(advertisement) { applyNotificationWorkaround() }
    }
}

/**
 * iOS/CoreBluetooth 可能为同一个特征返回不同的 CBCharacteristic 实例，
 * 而 Kable 默认按引用相等匹配通知 → 写入成功但所有 RX 被丢弃
 * （真机表现：帧日志只有 TX、等 randA 超时）。改为按 UUID 匹配。
 * 见 https://github.com/JuulLabs/kable/issues/886
 */
private fun com.juul.kable.PeripheralBuilder.applyNotificationWorkaround() {
    forceCharacteristicEqualityByUuid = true
}