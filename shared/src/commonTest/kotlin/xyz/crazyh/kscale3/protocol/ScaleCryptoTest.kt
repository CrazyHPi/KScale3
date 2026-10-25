package xyz.crazyh.kscale3.protocol

import xyz.crazyh.kscale3.protocol.crypto.aesCtrDecrypt
import xyz.crazyh.kscale3.protocol.crypto.aesCtrEncrypt
import xyz.crazyh.kscale3.protocol.crypto.secureRandomBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/** 黄金向量 1/2/8（PLAN「测试与验证」）。 */
class ScaleCryptoTest {

    @Test
    fun deriveRootKey_acceptsColonAndBareMac() {
        val expected = "d0d01fae597b65147ee89b03e26dd8ec"
        assertEquals(expected, ScaleCrypto.deriveRootKey("AA:BB:CC:DD:EE:FF").toHex())
        assertEquals(expected, ScaleCrypto.deriveRootKey("aabbccddeeff").toHex())
    }

    @Test
    fun deriveRootKey_normalizesDashSeparator() {
        assertEquals(
            "d0d01fae597b65147ee89b03e26dd8ec",
            ScaleCrypto.deriveRootKey("aa-bb-cc-dd-ee-ff").toHex(),
        )
    }

    @Test
    fun deriveRootKey_rejectsInvalidMac() {
        assertFailsWith<IllegalArgumentException> { ScaleCrypto.deriveRootKey("ZZ:ZZ:ZZ:ZZ:ZZ:ZZ") }
        assertFailsWith<IllegalArgumentException> { ScaleCrypto.deriveRootKey("aa:bb:cc:dd:ee") }
        // iOS CoreBluetooth UUID —— 必须拒绝而非静默派生错 key
        assertFailsWith<IllegalArgumentException> {
            ScaleCrypto.deriveRootKey("12345678-1234-1234-1234-123456789ABC")
        }
    }

    @Test
    fun generateTokenB_matchesGolden() {
        assertEquals(
            "e2c2c283c9545d4d67bce6e302ae0c0f274571455d92148746d037fe100def58",
            ScaleCrypto.generateTokenB(HexFixtures.RAND_A, HexFixtures.RAND_B).toHex(),
        )
    }

    @Test
    fun generateTokenA_matchesGolden() {
        assertEquals(
            "7d1c75ca91ef42b547ea1738b98b2ec2c853d53873fe617666e9a06839b7d052",
            ScaleCrypto.generateTokenA(HexFixtures.RAND_A, HexFixtures.RAND_B).toHex(),
        )
    }

    @Test
    fun generateToken_rejectsWrongRandLength() {
        assertFailsWith<IllegalArgumentException> {
            ScaleCrypto.generateTokenB(ByteArray(15), HexFixtures.RAND_B)
        }
        assertFailsWith<IllegalArgumentException> {
            ScaleCrypto.generateTokenA(HexFixtures.RAND_A, ByteArray(17))
        }
    }

    @Test
    fun aesCtr_nistSp80038aF51Kat() {
        val key = HexFixtures.NIST_KEY.hexToBytes()
        val iv = HexFixtures.NIST_COUNTER.hexToBytes()
        val plain = HexFixtures.NIST_PLAINTEXT.hexToBytes()
        val cipher = HexFixtures.NIST_CIPHERTEXT.hexToBytes()
        assertContentEquals(cipher, aesCtrEncrypt(key, iv, plain))
        assertContentEquals(plain, aesCtrDecrypt(key, iv, cipher))
    }

    @Test
    fun encrypt_decrypt_randomIvRoundTrip() {
        val key = secureRandomBytes(16)
        val plain = "华为体脂秤 Scale 3 Pro".encodeToByteArray()
        val wire = ScaleCrypto.encryptAesCtr(key, plain)
        assertEquals(plain.size + 16, wire.size) // [16B IV] + CT
        assertContentEquals(plain, ScaleCrypto.decryptAesCtr(key, wire))
        // 每次加密新随机 IV —— 同明文两次密文不同
        assertNotEquals(wire.toHex(), ScaleCrypto.encryptAesCtr(key, plain).toHex())
    }

    @Test
    fun decryptAesCtr_onlyIv_returnsEmpty() {
        val key = ByteArray(16) { it.toByte() }
        assertContentEquals(ByteArray(0), ScaleCrypto.decryptAesCtr(key, ByteArray(16)))
    }

    @Test
    fun decryptAesCtr_shorterThanIv_throws() {
        val key = ByteArray(16) { it.toByte() }
        assertFailsWith<IllegalArgumentException> { ScaleCrypto.decryptAesCtr(key, ByteArray(15)) }
        assertFailsWith<IllegalArgumentException> {
            ScaleCrypto.decryptAesCtr(
                ByteArray(15),
                ByteArray(16)
            )
        }
    }
}
