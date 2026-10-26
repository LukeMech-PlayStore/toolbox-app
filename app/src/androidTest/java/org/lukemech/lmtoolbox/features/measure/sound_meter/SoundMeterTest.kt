package org.lukemech.lmtoolbox.features.measure.sound_meter

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.lukemech.lmtoolbox.e2e.ToolTest
import org.lukemech.lmtoolbox.features.measure.common.ReadingStats

/** Sound level meter, gone through (see ToolTest), and its maths right: dB, the A-weighting, Fast, Leq. */
class SoundMeterTest : ToolTest(SoundMeterFeature) {
    @Test
    fun decibelsOfFullScale() {
        assertEquals(0.0, dbfs(1.0), 1e-9)
        assertEquals(-6.02, dbfs(0.5), 0.01)
        assertEquals(FLOOR_DBFS, dbfs(0.0), 0.0)
    }

    @Test
    fun splAsAndroidSetsTheMicrophones() {
        // CDD: VOICE_RECOGNITION's 90 dB SPL at RMS 2500, UNPROCESSED's 94 dB SPL at RMS 520
        assertEquals(90.0, spl(dbfs(2500 / FULL_SCALE), fullScaleSpl(unprocessed = false), 0.0), 1e-9)
        assertEquals(94.0, spl(dbfs(520 / FULL_SCALE), fullScaleSpl(unprocessed = true), 0.0), 1e-9)
    }

    @Test
    fun calibrationMatchesTheReference() {
        val reading = 80.0
        val calibration = calibrationFor(reading, reference = 83.5)
        assertEquals(3.5, calibration, 1e-9)
        assertEquals(83.5, spl(reading - fullScaleSpl(false), fullScaleSpl(false), calibration), 1e-9)
    }

    @Test
    fun aWeightingAsTheStandard() {
        // The standard's own table: 0 dB at 1 kHz, -19.1 at 100 Hz, -2.5 at 10 kHz
        assertEquals(0.0, AWeighting.standardDb(1000.0), 0.01)
        assertEquals(-19.1, AWeighting.standardDb(100.0), 0.05)
        assertEquals(-2.5, AWeighting.standardDb(10_000.0), 0.05)
        for (rate in listOf(44_100.0, 48_000.0)) {
            val filter = AWeighting(rate)
            assertEquals(0.0, filter.gainDb(1000.0)!!, 0.01)
            for (hz in listOf(31.5, 63.0, 100.0, 250.0, 500.0, 2000.0, 4000.0)) {
                assertEquals("$hz Hz at $rate", AWeighting.standardDb(hz), filter.gainDb(hz)!!, 0.5)
            }
            assertEquals("8 kHz at $rate", AWeighting.standardDb(8000.0), filter.gainDb(8000.0)!!, 1.0)
            assertEquals("10 kHz at $rate", AWeighting.standardDb(10_000.0), filter.gainDb(10_000.0)!!, 1.5)
            assertNull(filter.gainDb(rate / 2))
        }
    }

    @Test
    fun aWeightedSineIsQuieter() {
        val rate = 44_100.0
        val filter = AWeighting(rate)
        val meter = LevelMeter(rate)
        // A full-scale 100 Hz sine: -3 dBFS (its RMS), 19.1 dB less A-weighted
        repeat(rate.toInt()) { meter.add(filter.process(sin(2 * PI * 100 * it / rate))) }
        assertEquals(-3.01 - 19.1, meter.level, 0.5)
    }

    @Test
    fun fastLevelAndLeq() {
        val rate = 44_100.0
        val meter = LevelMeter(rate)
        repeat(rate.toInt()) { meter.add(0.5 * sin(2 * PI * 1000 * it / rate)) }
        assertEquals(20 * log10(0.5 / sqrt(2.0)), meter.level, 0.2)
        assertEquals(0.125, meter.takeMeanSquare(), 0.001)
        assertEquals(0.0, meter.takeMeanSquare(), 0.0)
        // The Leq of a loud block and a quiet one: the energy's average, near the loud one's
        val blocks = ReadingStats()
        blocks.add(0.01)
        blocks.add(0.0001)
        assertEquals(10 * log10(0.00505), leqDbfs(blocks.mean!!), 1e-6)
    }
}
