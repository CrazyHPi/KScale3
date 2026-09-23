package xyz.crazyh.kscale3.protocol

/** HEX 字符串 → 字节（忽略空格/冒号/连字符分隔；奇数长度拒绝）。 */
internal fun String.hexToBytes(): ByteArray {
    val clean = replace(" ", "").replace(":", "").replace("-", "")
    require(clean.length % 2 == 0) { "hex string must have even length: $this" }
    return ByteArray(clean.length / 2) { i ->
        val hi = clean[i * 2].digitTo16StrictOrNull()
        val lo = clean[i * 2 + 1].digitTo16StrictOrNull()
        require(hi != null && lo != null) { "invalid hex digit in: $this" }
        ((hi shl 4) or lo).toByte()
    }
}

/** 字节 → 小写 HEX 字符串（无分隔）。 */
internal fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    return buildString(size * 2) {
        for (b in this@toHex) {
            val v = b.toInt() and 0xFF
            append(digits[v ushr 4])
            append(digits[v and 0x0F])
        }
    }
}

private fun Char.digitTo16StrictOrNull(): Int? = when (this) {
    in '0'..'9' -> this - '0'
    in 'a'..'f' -> this - 'a' + 10
    in 'A'..'F' -> this - 'A' + 10
    else -> null
}

/** 读 u16 小端。 */
internal fun ByteArray.getU16Le(offset: Int): Int {
    require(offset in indices && offset + 1 <= lastIndex) { "u16 out of range at $offset" }
    return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
}

/** 写 u16 小端。 */
internal fun ByteArray.putU16Le(offset: Int, value: Int) {
    require(offset in indices && offset + 1 <= lastIndex) { "u16 out of range at $offset" }
    require(value in 0..0xFFFF) { "u16 value out of range: $value" }
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
}

/**
 * 写定长 ASCII 字段（不足补 `\x00`）。
 * ⚠️ 超长**抛异常拒绝**而非截断（截断会静默产生两个字段值相同的标识）。
 */
internal fun ByteArray.putAsciiPadded(offset: Int, value: String, fieldLen: Int) {
    require(offset + fieldLen <= size) { "field [$offset, ${offset + fieldLen}) out of buffer" }
    val bytes = value.encodeToByteArray()
    require(bytes.size <= fieldLen) { "value of ${bytes.size}B exceeds field length $fieldLen" }
    for (i in 0 until fieldLen) this[offset + i] = 0
    bytes.copyInto(this, offset)
}

/** 读定长 ASCII 字段（截到首个 `\x00`）。 */
internal fun ByteArray.getAsciiTrimmed(offset: Int, fieldLen: Int): String {
    require(offset + fieldLen <= size) { "field [$offset, ${offset + fieldLen}) out of buffer" }
    var end = offset
    while (end < offset + fieldLen && this[end] != 0.toByte()) end++
    return decodeToString(offset, end)
}
