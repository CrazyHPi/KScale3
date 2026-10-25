package xyz.crazyh.kscale3.data.db

import kotlinx.datetime.Instant
import xyz.crazyh.kscale3.domain.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 行类型 ↔ 领域标量映射往返。 */
class DbMappingTest {

    @Test
    fun sex_roundTrip() {
        assertEquals(1L, Sex.Male.toDb())
        assertEquals(0L, Sex.Female.toDb())
        assertEquals(Sex.Male, sexFromDb(1L))
        assertEquals(Sex.Female, sexFromDb(0L))
    }

    @Test
    fun instant_roundTrip() {
        val instant = Instant.fromEpochMilliseconds(1_700_000_000_123L)
        assertEquals(1_700_000_000_123L, instant.toEpochMs())
        assertEquals(instant, instantFromEpochMs(instant.toEpochMs()))
    }

    @Test
    fun impedanceCsv_roundTrip() {
        val feet = listOf(256, 257, 258, 259, 260, 261)
        assertEquals("256,257,258,259,260,261", feet.toImpedanceCsv())
        assertEquals(feet, feet.toImpedanceCsv().toImpedanceList())

        val single = listOf(42)
        assertEquals(single, single.toImpedanceCsv().toImpedanceList())

        assertEquals(emptyList(), emptyList<Int>().toImpedanceCsv().toImpedanceList())
        assertEquals(emptyList(), "".toImpedanceList())
    }

    @Test
    fun impedanceList_invalidThrows() {
        assertFailsWith<NumberFormatException> { "1,two,3".toImpedanceList() }
    }
}
