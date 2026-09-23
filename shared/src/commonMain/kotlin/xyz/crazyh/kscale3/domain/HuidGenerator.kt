package xyz.crazyh.kscale3.domain

import xyz.crazyh.kscale3.protocol.crypto.secureRandomBytes

/**
 * HUID 生成（scale3.py `generate_random_huid`）：`"300330000"` + 8 位随机数字，共 17 位。
 * ⚠️ HUID 属身份信息，绝不入库到版本库/日志（SUMMARY §9）。
 */
object HuidGenerator {

    fun generate(): String {
        val suffix = buildString(8) {
            val bytes = secureRandomBytes(8)
            repeat(8) { i ->
                append(('0'.code + (bytes[i].toInt() and 0xFF) % 10).toChar())
            }
        }
        return "300330000$suffix"
    }
}
