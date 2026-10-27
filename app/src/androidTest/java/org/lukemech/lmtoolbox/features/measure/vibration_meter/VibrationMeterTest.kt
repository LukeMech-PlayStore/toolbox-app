package org.lukemech.lmtoolbox.features.measure.vibration_meter

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.lukemech.lmtoolbox.e2e.ToolTest
import org.lukemech.lmtoolbox.features.measure.common.sampleRate

/** Vibration meter, gone through (see ToolTest), and its maths right: RMS, peak, the FFT and the frequency. */
class VibrationMeterTest : ToolTest(VibrationMeterFeature) {
    @Test
    fun rmsAndPeakOfASine() {
        val n = 1000
        val x = DoubleArray(n) { 2 * sin(2 * PI * 10 * it / n) }
        val zeros = DoubleArray(n)
        assertEquals(2 / sqrt(2.0), rms(x, zeros, zeros), 1e-3)
        assertEquals(2.0, peak(x, zeros, zeros), 1e-3)
        // Spread over the axes, the same size
        assertEquals(5.0, peak(doubleArrayOf(3.0), doubleArrayOf(4.0), doubleArrayOf(0.0)), 1e-9)
        assertEquals(1.0, toG(STANDARD_GRAVITY), 1e-12)
    }

    @Test
    fun fftOfAnImpulseAndATone() {
        val re = doubleArrayOf(1.0, 0.0, 0.0, 0.0)
        val im = DoubleArray(4)
        fft(re, im)
        assertArrayEquals(doubleArrayOf(1.0, 1.0, 1.0, 1.0), re, 1e-12)
        // Bin 3 of 16 holds a cosine of 3 cycles, half of it (and its mirror bin 13)
        val tone = DoubleArray(16) { cos(2 * PI * 3 * it / 16) }
        val toneIm = DoubleArray(16)
        fft(tone, toneIm)
        assertEquals(8.0, tone[3], 1e-9)
        assertEquals(8.0, tone[13], 1e-9)
        assertEquals(0.0, tone[4], 1e-9)
    }

    @Test
    fun dominantFrequencyOfAVibration() {
        val rate = 200.0
        val n = 512
        // 23.4 Hz on one axis, between bins (0.39 Hz apart), with a little 60 Hz on another
        val x = DoubleArray(n) { sin(2 * PI * 23.4 * it / rate) }
        val y = DoubleArray(n) { 0.2 * sin(2 * PI * 60 * it / rate) }
        val z = DoubleArray(n)
        assertEquals(23.4, dominantFrequency(listOf(x, y, z), rate)!!, 0.2)
        assertNull(dominantFrequency(listOf(DoubleArray(4)), rate))
        assertEquals(512, powerOfTwoBelow(700))
    }

    @Test
    fun windowKeepsTheNewest() {
        val window = SampleWindow(4)
        for (i in 1..6) window.add(i.toDouble(), 0.0, 0.0, i * 5_000_000L)
        assertEquals(4, window.size)
        assertArrayEquals(doubleArrayOf(3.0, 4.0, 5.0, 6.0), window.axis(0), 0.0)
        assertArrayEquals(doubleArrayOf(5.0, 6.0), window.axis(0, 2), 0.0)
        // 5 ms apart: 200 a second
        assertEquals(200.0, sampleRate(window.timestamps())!!, 1e-9)
    }

    @Test
    fun gravityTakenOut() {
        val filter = GravityFilter()
        var last = DoubleArray(3)
        repeat(100) { last = filter.filter(floatArrayOf(0f, 0f, 9.81f)) }
        assertEquals(0.0, last[2], 1e-6)
    }
}
