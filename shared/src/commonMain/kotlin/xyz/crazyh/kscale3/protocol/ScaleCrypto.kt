package xyz.crazyh.kscale3.protocol

import xyz.crazyh.kscale3.protocol.crypto.aesCtrDecrypt
import xyz.crazyh.kscale3.protocol.crypto.aesCtrEncrypt
import xyz.crazyh.kscale3.protocol.crypto.hmacSha256
import xyz.crazyh.kscale3.protocol.crypto.secureRandomBytes
import xyz.crazyh.kscale3.protocol.crypto.sha256

/**
 * 秤端加密纯函数（TECH.md §2）。
 * 黄金向量见 doc/PLAN.md「测试与验证」（scale3.py 真实代码生成）。
 * 原语来自 [xyz.crazyh.kscale3.protocol.crypto]（expect/actual）。
 */
object ScaleCrypto {

    /**
     * 由真实物理 MAC 派生 16B RootKey：
     * `mac_clean = (MAC 去分隔符 + "0000").upper()`（16B ASCII）；
     * `b4[i] = (WB1033[i] 低4位 << 4) ^ WB2033[i]`；`bE = SHA256(b4)[:16]`；
     * `b5[i] = (bE[i] >>> 6) ^ mac_clean[i]`（⚠️ **无符号右移**，对有符号 Byte 直接 `shr` 会符号污染）；
     * `RootKey = SHA256(b5)[:16]`。
     *
     * 分隔符 `:`/`-` 均被移除（参考实现只除 `:`，带 `-` 的输入会静默派生出错误 key——此处显式归一并校验
     * 12 位 HEX，非法输入（如 iOS UUID）抛 [IllegalArgumentException]）。
     * 向量：`deriveRootKey("AA:BB:CC:DD:EE:FF") == deriveRootKey("aabbccddeeff") == d0d01fae597b65147ee89b03e26dd8ec`。
     */
    fun deriveRootKey(mac: MacAddress): ByteArray {
        val cleaned = mac.replace(":", "").replace("-", "").uppercase()
        require(cleaned.length == 12 && cleaned.all { it in "0123456789ABCDEF" }) {
            "MAC must be 12 hex digits (got: $mac)"
        }
        val macClean = (cleaned + "0000").encodeToByteArray()
        val w1 = ProtocolConstants.WB1033
        val w2 = ProtocolConstants.WB2033
        val b4 = ByteArray(16) { i ->
            ((((w1[i].toInt() and 0x0F) shl 4) xor (w2[i].toInt() and 0xFF)) and 0xFF).toByte()
        }
        val bE = sha256(b4).copyOfRange(0, 16)
        val b5 = ByteArray(16) { i ->
            (((bE[i].toInt() and 0xFF) ushr 6) xor (macClean[i].toInt() and 0xFF)).toByte()
        }
        return sha256(b5).copyOfRange(0, 16)
    }

    /**
     * `tokenB = HMAC-SHA256(HMAC-SHA256(CAK+"1123", randAB), randAB)`，32B（全长度不截断）。
     * ⚠️ key/data 顺序不可反；tokenB 错 → 0x31 持续 Code 2。
     */
    fun generateTokenB(randA: ByteArray, randB: ByteArray): ByteArray =
        doubleHmac("1123", randA, randB)

    /**
     * `tokenA = HMAC-SHA256(HMAC-SHA256(CAK+"9856", randAB), randAB)`，32B（秤 → 主机的认证凭据）。
     * 参考实现收了未校验——KMP 版应校验（PLAN 决策）。
     */
    fun generateTokenA(randA: ByteArray, randB: ByteArray): ByteArray =
        doubleHmac("9856", randA, randB)

    private fun doubleHmac(suffix: String, randA: ByteArray, randB: ByteArray): ByteArray {
        require(randA.size == 16 && randB.size == 16) { "randA/randB must be 16B each" }
        val randAB = randA + randB
        val innerKey = ProtocolConstants.CAK + suffix.encodeToByteArray()
        val inner = hmacSha256(innerKey, randAB)
        return hmacSha256(inner, randAB)
    }

    /** AES-128-CTR 加密（WorkKey/RootKey 均 16B），线上格式 `[16B 随机 IV] + 密文`（每次加密新 IV）。 */
    fun encryptAesCtr(key: ByteArray, plain: ByteArray): ByteArray {
        require(key.size == 16) { "key must be 16B" }
        val iv = secureRandomBytes(16)
        return iv + aesCtrEncrypt(key, iv, plain)
    }

    /**
     * [encryptAesCtr] 的逆：剥离 16B IV 前缀后按 IV 解密。
     * `wire` 恰为 16B（仅 IV）→ 返回空数组；`<16B` → 抛 [IllegalArgumentException]
     *（参考实现对 ≤16B 一律返回空串属容错，此处收紧）。
     */
    fun decryptAesCtr(key: ByteArray, wire: ByteArray): ByteArray {
        require(key.size == 16) { "key must be 16B" }
        require(wire.size >= 16) { "wire must be at least 16B (IV prefix)" }
        if (wire.size == 16) return ByteArray(0)
        return aesCtrDecrypt(key, wire.copyOfRange(0, 16), wire.copyOfRange(16, wire.size))
    }
}
