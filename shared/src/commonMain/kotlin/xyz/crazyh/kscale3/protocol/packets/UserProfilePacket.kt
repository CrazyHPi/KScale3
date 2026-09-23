package xyz.crazyh.kscale3.protocol.packets

import xyz.crazyh.kscale3.protocol.getAsciiTrimmed
import xyz.crazyh.kscale3.protocol.getU16Le
import xyz.crazyh.kscale3.protocol.putAsciiPadded
import xyz.crazyh.kscale3.protocol.putU16Le

/**
 * 0x31 用户档案（69B 明文 → AES(WorkKey) → 0xDC 帧，TECH.md §7.1）。
 * 向量（huid=`30033000012345678`、男、30 岁、175cm、62.34kg、type=0）：
 * `3330303333303030303132333435363738` + 45×`00` + `011eaf005a1800`（175=`af00`、6234=`5a18` 小端）；
 * type=2 提交变体尾部 `011eaf00000002`（weight=0）。
 *
 * ⚠️ UID 32B **恒全零**（线上 type=0/2 均如此）；encode 按字段写出（调用方传 `uid=""` 即得全零），
 * 不硬置零以便 decode/encode 往返。
 * ⚠️ [weightDg] 直接收整型 centi-kg（体重×100），协议层**无浮点舍入**；
 * 参考实现 `round(weight*100)` 为银行家舍入（half-even），kg→centi-kg 换算在调用方做时注意。
 */
data class UserProfilePacket(
    /** 30B ASCII 补 `\x00`；超长拒绝（不截断） */
    val huid: String,
    /** 32B ASCII 补 `\x00`；线上恒空串 → 全零 */
    val uid: String,
    /** 1=男 0=女 */
    val sex: Int,
    val age: Int,
    /** 身高 cm，u16le */
    val heightCm: Int,
    /** 体重×100（kg），u16le；配对用 0x2D 皮重、日常用上次测量值 */
    val weightDg: Int,
    /** 0=档案/测量；2=测后提交（且 weight=0） */
    val type: Int,
) {
    fun encode(): ByteArray {
        require(sex in 0..1) { "sex must be 0(female)/1(male): $sex" }
        require(age in 0..0xFF) { "age out of u8: $age" }
        require(heightCm in 0..0xFFFF) { "heightCm out of u16: $heightCm" }
        require(weightDg in 0..0xFFFF) { "weightDg out of u16: $weightDg" }
        require(type == 0 || type == 2) { "type must be 0 or 2: $type" }
        if (type == 2) require(weightDg == 0) { "commit (type=2) must carry weight=0: $weightDg" }
        val out = ByteArray(SIZE)
        out.putAsciiPadded(0, huid, 30)
        out.putAsciiPadded(30, uid, 32)
        out[62] = sex.toByte()
        out[63] = age.toByte()
        out.putU16Le(64, heightCm)
        out.putU16Le(66, weightDg)
        out[68] = type.toByte()
        return out
    }

    companion object {
        const val SIZE: Int = 69

        /** 忠实解码线上字节（不做值域校验），仅严格校验长度。 */
        fun decode(data: ByteArray): UserProfilePacket {
            require(data.size == SIZE) { "profile packet must be ${SIZE}B, was ${data.size}B" }
            return UserProfilePacket(
                huid = data.getAsciiTrimmed(0, 30),
                uid = data.getAsciiTrimmed(30, 32),
                sex = data[62].toInt() and 0xFF,
                age = data[63].toInt() and 0xFF,
                heightCm = data.getU16Le(64),
                weightDg = data.getU16Le(66),
                type = data[68].toInt() and 0xFF,
            )
        }
    }
}
