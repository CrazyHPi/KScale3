package xyz.crazyh.kscale3.protocol

/**
 * 秤端加密纯函数（TECH.md §2）。
 * 黄金向量见 doc/PLAN.md「测试与验证」，可与 scale3.py 交叉验证。
 * 原语来自 [xyz.crazyh.kscale3.protocol.crypto]（expect/actual）。
 */
object ScaleCrypto {

    /**
     * 由真实物理 MAC 派生 16B RootKey：
     * `mac_clean = (MAC 去分隔符 + "0000").upper()`（16B ASCII）；
     * `b4[i] = (WB1033[i] << 4) ^ WB2033[i]`；`bE = SHA256(b4)[:16]`；
     * `b5[i] = (bE[i] >> 6) ^ mac_clean[i]`；`RootKey = SHA256(b5)[:16]`。
     * 向量：`deriveRootKey("AA:BB:CC:DD:EE:FF") == deriveRootKey("aabbccddeeff") == d0d01fae597b65147ee89b03e26dd8ec`。
     */
    fun deriveRootKey(mac: MacAddress): ByteArray = TODO("Phase 1: protocol implementation")

    /**
     * `tokenB = HMAC-SHA256(HMAC-SHA256(CAK+"1123", randAB), randAB)`，32B；`randAB = randA‖randB`。
     * ⚠️ key/data 顺序不可反；tokenB 错 → 0x31 持续 Code 2。
     */
    fun generateTokenB(randA: ByteArray, randB: ByteArray): ByteArray =
        TODO("Phase 1: protocol implementation")

    /**
     * `tokenA = HMAC-SHA256(HMAC-SHA256(CAK+"9856", randAB), randAB)`，32B（秤 → 主机的认证凭据）。
     * 参考实现收了未校验 —— KMP 版应校验（PLAN 决策）。
     */
    fun generateTokenA(randA: ByteArray, randB: ByteArray): ByteArray =
        TODO("Phase 1: protocol implementation")

    /** AES-128-CTR 加密（WorkKey/RootKey 均 16B），线上格式 `[16B 随机 IV] + 密文`。 */
    fun encryptAesCtr(key: ByteArray, plain: ByteArray): ByteArray =
        TODO("Phase 1: protocol implementation")

    /** [encryptAesCtr] 的逆：剥离 16B IV 前缀后按 IV 解密。 */
    fun decryptAesCtr(key: ByteArray, wire: ByteArray): ByteArray =
        TODO("Phase 1: protocol implementation")
}
