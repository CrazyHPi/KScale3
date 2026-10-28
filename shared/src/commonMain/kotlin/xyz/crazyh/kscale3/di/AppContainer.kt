package xyz.crazyh.kscale3.di

import com.juul.kable.Scanner
import xyz.crazyh.kscale3.ble.BlePeripheral
import xyz.crazyh.kscale3.ble.BleScanner
import xyz.crazyh.kscale3.ble.KableBlePeripheral
import xyz.crazyh.kscale3.ble.KableBleScanner
import xyz.crazyh.kscale3.ble.createKablePeripheral
import xyz.crazyh.kscale3.data.db.DriverFactory
import xyz.crazyh.kscale3.data.db.KScale3Database
import xyz.crazyh.kscale3.data.db.createDatabase
import xyz.crazyh.kscale3.data.repository.DeviceRepository
import xyz.crazyh.kscale3.data.repository.MeasurementRepository
import xyz.crazyh.kscale3.data.repository.SqlDeviceRepository
import xyz.crazyh.kscale3.data.repository.SqlMeasurementRepository
import xyz.crazyh.kscale3.data.repository.SqlUserRepository
import xyz.crazyh.kscale3.data.repository.UserRepository
import xyz.crazyh.kscale3.session.ScaleSession
import xyz.crazyh.kscale3.session.ScaleSessionImpl
import xyz.crazyh.kscale3.session.SessionOptions

/**
 * 手写构造注入容器（PLAN 架构决策：不上 Koin）。
 * 平台入口负责构造：Android `AppContainer(DriverFactory(context))`、iOS `AppContainer(DriverFactory())`。
 */
class AppContainer(private val driverFactory: DriverFactory) {

    private val database: KScale3Database by lazy { createDatabase(driverFactory) }

    val userRepository: UserRepository by lazy { SqlUserRepository(database) }

    val measurementRepository: MeasurementRepository by lazy { SqlMeasurementRepository(database) }

    val deviceRepository: DeviceRepository by lazy { SqlDeviceRepository(database) }

    /** 扫描（疑似秤置顶由 UI 层排序，不按名称过滤）。 */
    fun createBleScanner(): BleScanner = KableBleScanner(Scanner { })

    /** 按标识建外设（Android=物理 MAC，iOS=CoreBluetooth UUID）。 */
    fun createBlePeripheral(identifier: String): BlePeripheral =
        KableBlePeripheral(createKablePeripheral(identifier))

    /** 每次称重/配对新建会话。 */
    fun createScaleSession(
        peripheral: BlePeripheral,
        options: SessionOptions = SessionOptions()
    ): ScaleSession =
        ScaleSessionImpl(peripheral, options)
}
