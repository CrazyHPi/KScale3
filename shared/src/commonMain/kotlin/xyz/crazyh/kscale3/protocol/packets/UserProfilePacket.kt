package xyz.crazyh.kscale3.protocol.packets

/**
 * 0x31 用户档案（69B 明文 → AES(WorkKey) → 0xDC 帧，TECH.md §7.1）。
 * 向量（huid=`30033000012345678`、男、30 岁、175cm、62.34kg、type=0）：
 * `3330303333303030303132333435363738` + 45×`00` + `011eaf005a1800`（175=`af00`、6234=`5a18` 小端）；
 * type=2 提交变体尾部 `011eaf00000002`（weight=0）。
 */
data class UserProfilePacket(
    /** 30B ASCII 补 `\x00` */
    val huid: String,
    /** 32B ASCII 补 `\x00`；type=0 时置零 */
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
    fun encode(): ByteArray = TODO("Phase 1: protocol implementation")

    companion object {
        const val SIZE: Int = 69

        fun decode(data: ByteArray): UserProfilePacket = TODO("Phase 1: protocol implementation")
    }
}
