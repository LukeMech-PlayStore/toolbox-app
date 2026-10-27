package org.lukemech.lmtoolbox.features.measure.light_meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.e2e.ToolTest
import org.lukemech.lmtoolbox.features.measure.common.ReadingStats

/** Light meter, gone through (see ToolTest), and its maths right: the stats, foot-candles, the references. */
class LightMeterTest : ToolTest(LightMeterFeature) {
    @Test
    fun minMaxAverage() {
        val stats = ReadingStats()
        assertNull(stats.mean)
        listOf(100.0, 300.0, 200.0).forEach(stats::add)
        assertEquals(100.0, stats.min!!, 0.0)
        assertEquals(300.0, stats.max!!, 0.0)
        assertEquals(200.0, stats.mean!!, 1e-9)
        stats.reset()
        assertNull(stats.min)
        assertNull(stats.mean)
    }

    @Test
    fun footCandlesOfLux() {
        assertEquals(1.0, footCandles(LUX_PER_FOOT_CANDLE), 1e-9)
        assertEquals(46.45, footCandles(500.0), 0.01)
    }

    @Test
    fun readingsLikeTheirReferences() {
        fun like(lux: Double) = LIGHT_REFERENCES[referenceFor(lux)].name
        assertEquals(R.string.f_light_ref_moonlight, like(0.0))
        assertEquals(R.string.f_light_ref_moonlight, like(0.2))
        assertEquals(R.string.f_light_ref_office, like(400.0))
        // Nearer 50 than 320 by ratio (×2.4 against ×2.7)
        assertEquals(R.string.f_light_ref_living_room, like(120.0))
        assertEquals(R.string.f_light_ref_overcast, like(1500.0))
        assertEquals(R.string.f_light_ref_sunlight, like(150_000.0))
    }
}
