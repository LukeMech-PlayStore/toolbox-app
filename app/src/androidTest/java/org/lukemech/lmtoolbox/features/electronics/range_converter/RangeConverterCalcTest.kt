package org.lukemech.lmtoolbox.features.electronics.range_converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The range converter's maths (RangeConverterCalc). */
class RangeConverterCalcTest {
    @Test
    fun currentLoopToPercentAndBack() {
        assertEquals(50.0, mapRange(12.0, 4.0, 20.0, 0.0, 100.0)!!, 1e-12)
        assertEquals(0.0, mapRange(4.0, 4.0, 20.0, 0.0, 100.0)!!, 1e-12)
        assertEquals(8.0, mapRange(25.0, 0.0, 100.0, 4.0, 20.0)!!, 1e-12)
        // Outside the range, the line goes on: 3.6 mA is −2.5 %
        assertEquals(-2.5, mapRange(3.6, 4.0, 20.0, 0.0, 100.0)!!, 1e-12)
    }

    @Test
    fun reversedAndEmptyRanges() {
        // A falling output (10–0 V)
        assertEquals(7.5, mapRange(4.0, 0.0, 16.0, 10.0, 0.0)!!, 1e-12)
        assertNull(mapRange(1.0, 5.0, 5.0, 0.0, 100.0))
    }
}
