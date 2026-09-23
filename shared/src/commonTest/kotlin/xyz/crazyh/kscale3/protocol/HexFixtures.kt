package xyz.crazyh.kscale3.protocol

/**
 * PLAN「测试与验证」黄金向量共享常量（scale3.py 真实代码生成；
 * NIST KAT 已在本机 openssl 3.2.1 上闭环核验）。
 */
internal object HexFixtures {
    /** 向量 2 输入：randA = 000102…0f */
    val RAND_A: ByteArray = "000102030405060708090a0b0c0d0e0f".hexToBytes()

    /** 向量 2 输入：randB = 101112…1f */
    val RAND_B: ByteArray = "101112131415161718191a1b1c1d1e1f".hexToBytes()

    /** 向量 4 载荷：`bytes(range(0x30, 0x56))`（38B） */
    val PAYLOAD_38: ByteArray = ByteArray(38) { (0x30 + it).toByte() }

    // ---- NIST SP 800-38A F.5.1 CTR-AES128.Encrypt（4 块 64B）----
    const val NIST_KEY: String = "2b7e151628aed2a6abf7158809cf4f3c"
    const val NIST_COUNTER: String = "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff"
    const val NIST_PLAINTEXT: String =
        "6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e51" +
            "30c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710"
    const val NIST_CIPHERTEXT: String =
        "874d6191b620e3261bef6864990db6ce9806f66b7970fdff8617187bb9fffdff" +
            "5ae4df3edbd5d35e5b4f09020db03eab1e031dda2fbe03d1792170a0f3009cee"
}
