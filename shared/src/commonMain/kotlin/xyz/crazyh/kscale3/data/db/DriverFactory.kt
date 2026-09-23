package xyz.crazyh.kscale3.data.db

import app.cash.sqldelight.db.SqlDriver

/**
 * SQLDelight driver 工厂（expect/actual）：
 * Android `AndroidSqliteDriver` / iOS `NativeSqliteDriver`，库名 `kscale3.db`。
 * 构造参数按平台而异（Android 需 Context）。
 */
expect class DriverFactory {
    fun createDriver(): SqlDriver
}
