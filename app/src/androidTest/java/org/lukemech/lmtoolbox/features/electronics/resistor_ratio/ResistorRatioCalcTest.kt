package org.lukemech.lmtoolbox.features.electronics.resistor_ratio

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lukemech.lmtoolbox.features.electronics.common.ESeries

/** The resistor ratio's maths (ResistorRatioCalc). */
class ResistorRatioCalcTest {
    @Test
    fun exactPairsFirst() {
        val pairs = bestPairs(2.5, RatioKind.R2ToR1, ESeries.E24)
        assertEquals(1200.0, pairs[0].r1, 1e-6)
        assertEquals(3000.0, pairs[0].r2, 1e-6)
        assertEquals(3000.0, pairs[1].r1, 1e-6)
        assertEquals(7500.0, pairs[1].r2, 1e-6)
        assertTrue(abs(pairs[0].error) < 1e-12)
        assertEquals(5, pairs.size)
        // Closest first
        assertTrue(pairs.zipWithNext().all { (a, b) -> abs(a.error) <= abs(b.error) + 1e-9 })
    }

    @Test
    fun dividerRatio() {
        // 1/3: R2 half of R1
        val best = bestPairs(1.0 / 3, RatioKind.Divider, ESeries.E24).first()
        assertEquals(1.0 / 3, ratioOf(best.r1, best.r2, RatioKind.Divider), 1e-12)
        assertEquals(best.r1 / 2, best.r2, 1e-6)
        // A divider can't give 1 or more
        assertTrue(bestPairs(1.5, RatioKind.Divider, ESeries.E24).isEmpty())
    }

    @Test
    fun ratiosConvert() {
        assertEquals(2.0 / 3, convertRatio(2.0, RatioKind.R2ToR1, RatioKind.Divider)!!, 1e-12)
        assertEquals(2.0, convertRatio(2.0 / 3, RatioKind.Divider, RatioKind.R2ToR1)!!, 1e-12)
        assertNull(convertRatio(1.2, RatioKind.Divider, RatioKind.R2ToR1))
    }

    @Test
    fun anyRatioFindsANearPair() {
        // E96's steps are under 2.5 %: the best of its pairs for any ratio is within 1 %
        listOf(0.0123, 0.5, 1.0, 3.7, 47.0, 1234.0).forEach { target ->
            val best = bestPairs(target, RatioKind.R2ToR1, ESeries.E96).first()
            assertTrue("$target: ${best.error}", abs(best.error) < 0.01)
        }
    }
}
