package xyz.crazyh.kscale3.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** PLAN table case: 70kg / 20% / 175cm -> FFM 56.00, TBW 40.88, bone, muscle, BMI, BMR. */
class BodyCompositionEstimatorTest {

    private val tolerance = 1e-9

    @Test
    fun estimate_maleReferenceCase() {
        val e = BodyCompositionEstimator.estimate(
            weightKg = 70.0,
            fatPct = 20.0,
            heightCm = 175,
            sex = Sex.Male,
            age = 30,
        )
        assertEquals(14.0, e.fatMassKg, tolerance) // 70 * 0.20
        assertEquals(40.88, e.tbwKg, tolerance) // 0.73 * 56
        assertEquals(2.9904, e.boneKg, tolerance) // 0.0534 * 56
        assertEquals(28.0, e.muscleKg, tolerance) // 0.50 * 56
        assertEquals(22.857142857142858, e.bmi, tolerance) // 70 / 1.75^2
        assertEquals(1648.75, e.bmr, tolerance) // 10*70 + 6.25*175 - 5*30 + 5
    }

    @Test
    fun estimate_femaleBmrFormula() {
        val e = BodyCompositionEstimator.estimate(
            weightKg = 70.0,
            fatPct = 20.0,
            heightCm = 175,
            sex = Sex.Female,
            age = 30,
        )
        assertEquals(1482.75, e.bmr, tolerance) // 10*70 + 6.25*175 - 5*30 - 161
    }

    @Test
    fun estimate_zeroFat_isAllFfm() {
        val e = BodyCompositionEstimator.estimate(
            weightKg = 60.0,
            fatPct = 0.0,
            heightCm = 160,
            sex = Sex.Female,
            age = 20,
        )
        assertEquals(0.0, e.fatMassKg, tolerance)
        assertEquals(43.8, e.tbwKg, tolerance) // 0.73 * 60
    }

    @Test
    fun estimate_rejectsInvalidInput() {
        assertFailsWith<IllegalArgumentException> {
            BodyCompositionEstimator.estimate(0.0, 20.0, 175, Sex.Male, 30)
        }
        assertFailsWith<IllegalArgumentException> {
            BodyCompositionEstimator.estimate(70.0, 120.0, 175, Sex.Male, 30)
        }
        assertFailsWith<IllegalArgumentException> {
            BodyCompositionEstimator.estimate(70.0, 20.0, 0, Sex.Male, 30)
        }
        assertFailsWith<IllegalArgumentException> {
            BodyCompositionEstimator.estimate(70.0, 20.0, 175, Sex.Male, 0)
        }
    }
}
