package xyz.crazyh.kscale3.protocol.crypto

/**
 * 平台加解密原语（expect/actual：Android `javax.crypto` / iOS `CommonCrypto`，零第三方加密依赖）。
 * 只暴露 [xyz.crazyh.kscale3.protocol.ScaleCrypto] 需要的最小集合：
 * SHA-256 / HMAC-SHA256 / AES-128-CTR / 密码学安全随机数。
 */

/** SHA-256 摘要（32B）。 */
expect fun sha256(data: ByteArray): ByteArray

/** HMAC-SHA256（32B）；[key] 为 HMAC key、[data] 为消息（⚠️ key/data 顺序不可反）。 */
expect fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

/** AES-128-CTR 加密（返回纯密文，不含 IV 前缀；线上封装见 ScaleCrypto.encryptAesCtr）。 */
expect fun aesCtrEncrypt(key: ByteArray, iv: ByteArray, plain: ByteArray): ByteArray

/** AES-128-CTR 解密（纯密文输入）。 */
expect fun aesCtrDecrypt(key: ByteArray, iv: ByteArray, cipher: ByteArray): ByteArray

/** 密码学安全随机字节（randB / WorkKey / IV / HUID 后缀用）。 */
expect fun secureRandomBytes(length: Int): ByteArray
