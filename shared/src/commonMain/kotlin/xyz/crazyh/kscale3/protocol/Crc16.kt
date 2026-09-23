package xyz.crazyh.kscale3.protocol

/**
 * CRC-16/CCITT 查表法（poly 0x1021、init 0、256 项表：TECH.md §3.1 = scale3.py L82）。
 *
 * ⚠️ 覆盖范围（TECH.md 写错，以实测线上字节为准）：
 * 对「CRC 字段预置 `00 00` 的完整帧」计算，即 `CRC(magic‖len‖seq‖payload‖0000)`，
 * 结果**小端**写回最后两字节。收端校验用同一规则重算比对。
 * 反例：空载荷 0xDB 帧按 TECH.md 字面实现会算出 `db030036c0`，真实线上是 `db0300c140`。
 */
object Crc16 {
    /** 计算 [data] 的 CRC-16/CCITT（16 位无符号值）。 */
    fun compute(data: ByteArray): Int = TODO("Phase 1: protocol implementation")
}
