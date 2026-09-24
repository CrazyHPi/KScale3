package xyz.crazyh.kscale3.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** HUID 格式（`"300330000"`+8 位随机数字）与随机性冒烟。 */
class HuidGeneratorTest {

    @Test
    fun generate_format() {
        repeat(20) {
            val huid = HuidGenerator.generate()
            assertEquals(17, huid.length)
            assertTrue(huid.startsWith("300330000"), "prefix mismatch: $huid")
            assertTrue(huid.all { ch -> ch.isDigit() }, "non-digit in: $huid")
        }
    }

    @Test
    fun generate_randomSuffix() {
        val distinct = List(20) { HuidGenerator.generate() }.toSet()
        assertEquals(20, distinct.size)
    }
}
