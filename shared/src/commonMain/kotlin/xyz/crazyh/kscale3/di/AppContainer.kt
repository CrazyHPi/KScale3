package xyz.crazyh.kscale3.di

import xyz.crazyh.kscale3.ble.BleScanner
import xyz.crazyh.kscale3.data.db.DriverFactory
import xyz.crazyh.kscale3.data.repository.DeviceRepository
import xyz.crazyh.kscale3.data.repository.MeasurementRepository
import xyz.crazyh.kscale3.data.repository.UserRepository
import xyz.crazyh.kscale3.session.ScaleSession

/**
 * 手写构造注入容器（PLAN 架构决策：不上 Koin）。
 * 平台入口负责构造：Android `AppContainer(DriverFactory(context))`、iOS `AppContainer(DriverFactory())`。
 */
class AppContainer(private val driverFactory: DriverFactory) {

    val userRepository: UserRepository get() = TODO("Phase 3: data + domain")

    val measurementRepository: MeasurementRepository get() = TODO("Phase 3: data + domain")

    val deviceRepository: DeviceRepository get() = TODO("Phase 3: data + domain")

    /** 每次称重/配对新建会话。 */
    fun createScaleSession(): ScaleSession = TODO("Phase 2: BLE + session")

    fun createBleScanner(): BleScanner = TODO("Phase 2: BLE + session")
}
