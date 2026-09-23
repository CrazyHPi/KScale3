package xyz.crazyh.kscale3.data.db

/**
 * 数据库创建入口。[KScale3Database] 由 SQLDelight 从
 * `shared/src/commonMain/sqldelight/xyz/crazyh/kscale3/data/db/` 下的 .sq
 * （user_profile / measurement / paired_device）生成。
 */
fun createDatabase(driverFactory: DriverFactory): KScale3Database {
    val driver = driverFactory.createDriver()
    return KScale3Database(driver)
}
