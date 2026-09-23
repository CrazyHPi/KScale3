package xyz.crazyh.kscale3.protocol

/**
 * 秤的真实物理 MAC 地址（RootKey 派生输入）。
 *
 * 接受 `"AA:BB:CC:DD:EE:FF"` 或 12 位 HEX（大小写不敏感）。
 * ⚠️ 必须是秤身标签上的真实物理 MAC：iOS CoreBluetooth 只暴露随机 UUID，
 * 喂错会导致握手看似成功、BIA 解出乱码（体重/日期不合理）。
 */
typealias MacAddress = String

/** 协议静态常量与固定线上报文（TECH.md §2.1 / §3）。 */
object ProtocolConstants {
    /** 认证静态常量（TECH.md §2.1）。 */
    val CAK: ByteArray = hexToBytes("90B96ECA297EF78717E66E491084D3F8")
    val WB1033: ByteArray = hexToBytes("CA4946D061C9FE534F6044F930EBB69B")
    val WB2033: ByteArray = hexToBytes("FBCE6E2B4BAF80ED969BA26B4A4B9325")

    /** 通用查询/武装命令（空载荷单帧），也是 CRC 端到端黄金向量：`db0300c140`。 */
    val QUERY_ARM: ByteArray = hexToBytes("db0300c140")

    /** 能力上报串（10 字节原样写入 0xD7，无响应写）。 */
    val CAPABILITY_BYTES: ByteArray = hexToBytes("5a000500013701001ca9")

    /** 单帧载荷上限（字节），TECH.md §3。 */
    const val MAX_PAYLOAD_PER_FRAME: Int = 15
}

private fun hexToBytes(hex: String): ByteArray {
    val clean = hex.replace(" ", "")
    require(clean.length % 2 == 0) { "hex string must have even length" }
    return ByteArray(clean.length / 2) { i ->
        clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}
