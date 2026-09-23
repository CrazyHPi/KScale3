package xyz.crazyh.kscale3.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import xyz.crazyh.kscale3.protocol.packets.BiaMeasurement
import xyz.crazyh.kscale3.protocol.packets.TareResponse
import xyz.crazyh.kscale3.protocol.packets.TimeSyncPacket
import xyz.crazyh.kscale3.protocol.packets.UserProfilePacket

/** 黄金向量 5/6/7 + 报文负例（PLAN「测试与验证」）。 */
class PacketsTest {

    // ---- 0x31 档案（向量 5）----

    private fun goldenProfile(type: Int, weightDg: Int) = UserProfilePacket(
        huid = "30033000012345678",
        uid = "",
        sex = 1,
        age = 30,
        heightCm = 175,
        weightDg = weightDg,
        type = type,
    )

    @Test
    fun userProfile_encode_type0() {
        val expected = "3330303333303030303132333435363738" + "00".repeat(45) + "011eaf005a1800"
        assertEquals(expected, goldenProfile(type = 0, weightDg = 6234).encode().toHex())
    }

    @Test
    fun userProfile_encode_type2_commit() {
        val expected = "3330303333303030303132333435363738" + "00".repeat(45) + "011eaf00000002"
        assertEquals(expected, goldenProfile(type = 2, weightDg = 0).encode().toHex())
    }

    @Test
    fun userProfile_encode_emptyUid_isAllZero() {
        val bytes = goldenProfile(type = 0, weightDg = 6234).encode()
        assertEquals(69, bytes.size)
        // 13B HUID 补零 + 32B UID = 45×00（恒零约定：线上 uid 传空串）
        for (i in 17 until 62) assertEquals(0, bytes[i].toInt(), "byte[$i] should be 0")
    }

    @Test
    fun userProfile_decode_roundTrip() {
        val packet = goldenProfile(type = 0, weightDg = 6234)
        assertEquals(packet, UserProfilePacket.decode(packet.encode()))
        // 向量 5 字节直接解码
        val decoded = UserProfilePacket.decode(
            ("3330303333303030303132333435363738" + "00".repeat(45) + "011eaf005a1800").hexToBytes(),
        )
        assertEquals("30033000012345678", decoded.huid)
        assertEquals("", decoded.uid)
        assertEquals(1, decoded.sex)
        assertEquals(30, decoded.age)
        assertEquals(175, decoded.heightCm)
        assertEquals(6234, decoded.weightDg)
        assertEquals(0, decoded.type)
    }

    @Test
    fun userProfile_rejects() {
        // HUID 超 30B 拒绝（不截断）
        assertFailsWith<IllegalArgumentException> {
            goldenProfile(0, 1).copy(huid = "3".repeat(31)).encode()
        }
        assertFailsWith<IllegalArgumentException> {
            goldenProfile(0, 1).copy(uid = "u".repeat(33)).encode()
        }
        assertFailsWith<IllegalArgumentException> { goldenProfile(0, 1).copy(sex = 2).encode() }
        assertFailsWith<IllegalArgumentException> { goldenProfile(1, 1).encode() }
        assertFailsWith<IllegalArgumentException> { goldenProfile(0, 1).copy(age = 256).encode() }
        assertFailsWith<IllegalArgumentException> { goldenProfile(0, 1).copy(heightCm = 65536).encode() }
        // 提交帧体重必须为 0
        assertFailsWith<IllegalArgumentException> { goldenProfile(2, 1).encode() }
        assertFailsWith<IllegalArgumentException> { UserProfilePacket.decode(ByteArray(68)) }
    }

    // ---- 0x97 BIA（向量 6）----

    @Test
    fun bia_decode_golden() {
        val data = "521cd700ea0709160e1e05020001010102010301040105014100000201020202030204020502".hexToBytes()
        val bia = BiaMeasurement.decode(data)
        assertEquals(72.50, bia.weightKg)
        assertEquals(21.5, bia.fatPct)
        assertEquals(2026, bia.year)
        assertEquals(9, bia.month)
        assertEquals(22, bia.day)
        assertEquals(14, bia.hour)
        assertEquals(30, bia.minute)
        assertEquals(5, bia.second)
        assertEquals(2, bia.dayOfWeek)
        assertEquals(listOf(256, 257, 258, 259, 260, 261), bia.feet)
        assertEquals(65, bia.heartRate)
        assertEquals(listOf(512, 513, 514, 515, 516, 517), bia.hands)
    }

    @Test
    fun bia_rejects_shortPacket() {
        // 严格 38B（参考实现 ≥26B 容错属差异，见 PLAN 差异补记）
        assertFailsWith<IllegalArgumentException> {
            BiaMeasurement.decode(ByteArray(37))
        }
    }

    // ---- 0x2D 皮重（向量 7）----

    @Test
    fun tare_decode_golden() {
        val tare = TareResponse.decode("005a1800".hexToBytes())
        assertEquals(0, tare.status)
        assertEquals(62.34, tare.weightKg)
    }

    @Test
    fun tare_rejects_short() {
        assertFailsWith<IllegalArgumentException> { TareResponse.decode(ByteArray(2)) }
        // 3B 无 pad 亦可解析
        assertEquals(62.34, TareResponse.decode("005a18".hexToBytes()).weightKg)
    }

    // ---- 0x52 时间同步（向量 3c 载荷）----

    @Test
    fun timeSync_encode_golden() {
        val payload = TimeSyncPacket(
            year = 2026, month = 9, day = 22,
            hour = 14, minute = 30, second = 5,
            dayOfWeek = 2,
        ).encode()
        assertEquals("ea0709160e1e0502", payload.toHex())
    }

    @Test
    fun timeSync_rejects() {
        fun packet(
            year: Int = 2026, month: Int = 9, day: Int = 22,
            hour: Int = 14, minute: Int = 30, second: Int = 5, dayOfWeek: Int = 2,
        ) = TimeSyncPacket(year, month, day, hour, minute, second, dayOfWeek)

        assertFailsWith<IllegalArgumentException> { packet(month = 0).encode() }
        assertFailsWith<IllegalArgumentException> { packet(month = 13).encode() }
        assertFailsWith<IllegalArgumentException> { packet(day = 0).encode() }
        assertFailsWith<IllegalArgumentException> { packet(hour = 24).encode() }
        assertFailsWith<IllegalArgumentException> { packet(second = 60).encode() }
        assertFailsWith<IllegalArgumentException> { packet(dayOfWeek = 0).encode() }
        assertFailsWith<IllegalArgumentException> { packet(dayOfWeek = 8).encode() }
    }
}
