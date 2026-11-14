@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package xyz.crazyh.kscale3.protocol.crypto

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCryptorCreateWithMode
import platform.CoreCrypto.CCCryptorFinal
import platform.CoreCrypto.CCCryptorRefVar
import platform.CoreCrypto.CCCryptorRelease
import platform.CoreCrypto.CCCryptorUpdate
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.ccNoPadding
import platform.CoreCrypto.kCCAlgorithmAES128
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCModeCTR
import platform.CoreCrypto.kCCSuccess
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.posix.size_tVar

actual fun sha256(data: ByteArray): ByteArray {
    val out = ByteArray(CC_SHA256_DIGEST_LENGTH)
    out.usePinned { o ->
        data.usePinned { d ->
            CC_SHA256(
                if (data.isEmpty()) null else d.addressOf(0),
                data.size.convert(),
                o.addressOf(0).reinterpret<UByteVar>(),
            )
        }
    }
    return out
}

actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val out = ByteArray(CC_SHA256_DIGEST_LENGTH)
    out.usePinned { o ->
        key.usePinned { k ->
            data.usePinned { d ->
                CCHmac(
                    kCCHmacAlgSHA256.convert(),
                    if (key.isEmpty()) null else k.addressOf(0), key.size.convert(),
                    if (data.isEmpty()) null else d.addressOf(0), data.size.convert(),
                    o.addressOf(0),
                )
            }
        }
    }
    return out
}

actual fun aesCtrEncrypt(key: ByteArray, iv: ByteArray, plain: ByteArray): ByteArray =
    aesCtr(key, iv, plain)

actual fun aesCtrDecrypt(key: ByteArray, iv: ByteArray, cipher: ByteArray): ByteArray =
    aesCtr(key, iv, cipher)

/**
 * AES-128-CTR：CTR 加解密同为密钥流 XOR（kCCEncrypt 即可），
 * 无填充时输出长度等于输入长度。
 */
private fun aesCtr(key: ByteArray, iv: ByteArray, input: ByteArray): ByteArray = memScoped {
    require(key.size == 16) { "AES-128 key must be 16 bytes" }
    require(iv.size == 16) { "CTR IV must be 16 bytes" }
    if (input.isEmpty()) return@memScoped ByteArray(0)

    val cryptorRef = alloc<CCCryptorRefVar>()
    val createStatus = key.usePinned { k ->
        iv.usePinned { i ->
            CCCryptorCreateWithMode(
                kCCEncrypt.convert(),
                kCCModeCTR.convert(),
                kCCAlgorithmAES128.convert(),
                ccNoPadding.convert(),
                i.addressOf(0),
                k.addressOf(0),
                16u.convert(),
                null,
                0u.convert(),
                0,
                0u.convert(),
                cryptorRef.ptr,
            )
        }
    }
    check(createStatus == kCCSuccess) { "CCCryptorCreateWithMode failed: $createStatus" }
    val cryptor = checkNotNull(cryptorRef.value) { "CCCryptorCreateWithMode returned null cryptor" }
    try {
        val out = ByteArray(input.size)
        val moved = alloc<size_tVar>()
        val updateStatus = input.usePinned { inp ->
            out.usePinned { o ->
                CCCryptorUpdate(
                    cryptor,
                    inp.addressOf(0), input.size.convert(),
                    o.addressOf(0), out.size.convert(),
                    moved.ptr,
                )
            }
        }
        check(updateStatus == kCCSuccess) { "CCCryptorUpdate failed: $updateStatus" }
        val finalStatus = CCCryptorFinal(cryptor, null, 0u.convert(), moved.ptr)
        check(finalStatus == kCCSuccess) { "CCCryptorFinal failed: $finalStatus" }
        out
    } finally {
        CCCryptorRelease(cryptor)
    }
}

actual fun secureRandomBytes(length: Int): ByteArray {
    val bytes = ByteArray(length)
    if (length == 0) return bytes
    bytes.usePinned { pinned ->
        val status = SecRandomCopyBytes(kSecRandomDefault, length.convert(), pinned.addressOf(0))
        check(status == 0) { "SecRandomCopyBytes failed: $status" }
    }
    return bytes
}
