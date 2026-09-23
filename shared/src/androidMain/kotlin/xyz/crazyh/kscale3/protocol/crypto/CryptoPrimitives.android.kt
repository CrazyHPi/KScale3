package xyz.crazyh.kscale3.protocol.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private val secureRandom = SecureRandom()

actual fun sha256(data: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(data)

actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data)
}

actual fun aesCtrEncrypt(key: ByteArray, iv: ByteArray, plain: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return cipher.doFinal(plain)
}

actual fun aesCtrDecrypt(key: ByteArray, iv: ByteArray, cipher: ByteArray): ByteArray {
    val decryptor = Cipher.getInstance("AES/CTR/NoPadding")
    decryptor.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return decryptor.doFinal(cipher)
}

actual fun secureRandomBytes(length: Int): ByteArray =
    ByteArray(length).also(secureRandom::nextBytes)
