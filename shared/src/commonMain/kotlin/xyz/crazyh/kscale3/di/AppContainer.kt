package xyz.crazyh.kscale3.di

import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import xyz.crazyh.kscale3.ble.BlePeripheral
import xyz.crazyh.kscale3.ble.BleScanner
import xyz.crazyh.kscale3.ble.KableBlePeripheral
import xyz.crazyh.kscale3.ble.KableBleScanner
import xyz.crazyh.kscale3.data.db.DriverFactory
import xyz.crazyh.kscale3.data.repository.DeviceRepository
import xyz.crazyh.kscale3.data.repository.MeasurementRepository
import xyz.crazyh.kscale3.data.repository.UserRepository
import xyz.crazyh.kscale3.session.ScaleSession
import xyz.crazyh.kscale3.session.ScaleSessionImpl

/**
 * 手写构造注入容器（PLAN 架构决策：不上 Koin）。
 * 平台入口负责构造：Android `AppContainer(DriverFactory(context))`、iOS `AppContainer(DriverFactory())`。
 */
class AppContainer(private val driverFactory: DriverFactory) {

    val userRepository: UserRepository get() = TODO("Phase 3: data + domain")

    val measurementRepository: MeasurementRepository get() = TODO("Phase 3: data + domain")

    val deviceRepository: DeviceRepository get() = TODO("Phase 3: data + domain")

    /** 扫描（名称过滤 `HaigeBLE|Scale 3|HUAWEI Scale` 由调用方约定）。 */
    fun createBleScanner(): BleScanner = KableBleScanner(Scanner { })

    /** 按标识建外设（Android=物理 MAC，iOS=CoreBluetooth UUID）。 */
    fun createBlePeripheral(identifier: String): BlePeripheral =
        KableBlePeripheral(Peripheral(identifier))

    /** 每次称重/配对新建会话。 */
    fun createScaleSession(peripheral: BlePeripheral): ScaleSession =
        ScaleSessionImpl(peripheral)
}
