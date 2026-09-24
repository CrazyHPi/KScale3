package xyz.crazyh.kscale3.data.db

import kotlinx.datetime.Instant
import xyz.crazyh.kscale3.domain.Sex

/** 行类型 ↔ 领域模型的标量映射（内部小工具，测试见 DbMappingTest）。 */

/** 0x31 档案字段约定：1=男 0=女。 */
internal fun Sex.toDb(): Long = when (this) {
    Sex.Male -> 1L
    Sex.Female -> 0L
}

internal fun sexFromDb(value: Long): Sex = if (value == 1L) Sex.Male else Sex.Female

internal fun Instant.toEpochMs(): Long = toEpochMilliseconds()

internal fun instantFromEpochMs(value: Long): Instant = Instant.fromEpochMilliseconds(value)

/** 12 路原始阻抗以紧凑文本存储（CSV of u16，PLAN 数据层）。 */
internal fun List<Int>.toImpedanceCsv(): String = joinToString(",")

internal fun String.toImpedanceList(): List<Int> =
    if (isEmpty()) emptyList() else split(",").map { it.toInt() }
