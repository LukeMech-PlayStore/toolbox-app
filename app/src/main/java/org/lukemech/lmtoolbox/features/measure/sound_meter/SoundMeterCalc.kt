package org.lukemech.lmtoolbox.features.measure.sound_meter

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** A 16-bit sample's full scale: a reading's RMS is a part of it. */
const val FULL_SCALE = 32768.0

/** Below this, silence: what an all-zero buffer (a muted microphone) shows instead of -∞. */
const val FLOOR_DBFS = -120.0

/** [rms] (a part of full scale, 0..1) in dB below full scale (dBFS). */
fun dbfs(rms: Double): Double = if (rms > 0) maxOf(20 * log10(rms), FLOOR_DBFS) else FLOOR_DBFS

/**
 * dB SPL that 0 dBFS stands for, by what Android asks of a source's microphone (its compatibility
 * definition, CDD): with VOICE_RECOGNITION a 90 dB SPL tone at 1 kHz should give an RMS of 2500 of 32768;
 * UNPROCESSED is quieter, 94 dB SPL giving 520. Only "should": the user's calibration does the rest.
 */
fun fullScaleSpl(unprocessed: Boolean): Double =
    if (unprocessed) 94 - 20 * log10(520 / FULL_SCALE) else 90 - 20 * log10(2500 / FULL_SCALE)

/**
 * A level in dBFS as dB SPL: the source's full scale ([fullScaleSpl]) and the user's [calibration] (dB, what
 * the microphone reads off by against a real meter) added.
 */
fun spl(dbfs: Double, fullScaleSpl: Double, calibration: Double): Double = dbfs + fullScaleSpl + calibration

/**
 * The calibration that makes a [reading] (dB SPL, without the calibration) show [reference] instead: what
 * a sound level meter beside the phone shows.
 */
fun calibrationFor(reading: Double, reference: Double): Double = reference - reading

/** The Leq of samples whose squares average [meanSquare] (a part of full scale squared), in dBFS. */
fun leqDbfs(meanSquare: Double): Double = dbfs(sqrt(meanSquare))

/**
 * One section of an IIR filter (a biquad), run sample by sample (transposed direct form II): the
 * coefficients normalized so a0 is 1.
 */
class Biquad(
    private var b0: Double, private var b1: Double, private var b2: Double,
    private val a1: Double, private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    /** Its gain multiplied by [factor] (the numerator scaled). */
    fun scale(factor: Double) {
        b0 *= factor
        b1 *= factor
        b2 *= factor
    }

    /** Its gain (as a ratio) at [omega] (radians per sample): |H(e^jω)|. */
    fun gain(omega: Double): Double {
        // H = (b0 + b1 z⁻¹ + b2 z⁻²) / (1 + a1 z⁻¹ + a2 z⁻²), z⁻¹ = e^-jω
        val c1 = cos(omega)
        val s1 = -sin(omega)
        val c2 = cos(2 * omega)
        val s2 = -sin(2 * omega)
        val num = hypot(b0 + b1 * c1 + b2 * c2, b1 * s1 + b2 * s2)
        val den = hypot(1 + a1 * c1 + a2 * c2, a1 * s1 + a2 * s2)
        return num / den
    }

    companion object {
        /**
         * The analog section (n2·s² + n1·s + n0) / (s² + d1·s + d0) at [sampleRate], by the bilinear transform
         * (s = 2·fs·(1 - z⁻¹) / (1 + z⁻¹)).
         */
        fun bilinear(n2: Double, n1: Double, n0: Double, d1: Double, d0: Double, sampleRate: Double): Biquad {
            val k = 2 * sampleRate
            val k2 = k * k
            val a0 = k2 + d1 * k + d0
            return Biquad(
                b0 = (n2 * k2 + n1 * k + n0) / a0,
                b1 = (-2 * n2 * k2 + 2 * n0) / a0,
                b2 = (n2 * k2 - n1 * k + n0) / a0,
                a1 = (-2 * k2 + 2 * d0) / a0,
                a2 = (k2 - d1 * k + d0) / a0,
            )
        }
    }
}

/**
 * The A-weighting of IEC 61672 (how loud the ear finds each frequency: low and very high ones count less),
 * as a filter the samples go through: its analog poles (20.6 Hz and 12.2 kHz twice, 107.7 Hz, 737.9 Hz, and
 * four zeros at 0 Hz) in three biquads, 0 dB at 1 kHz. Each pole is prewarped, so the bilinear transform
 * keeps it where it is (the 12.2 kHz ones would move down at 44.1 kHz); right within a dB or so up to
 * 10 kHz, a little low above, where the response ends at half the [sampleRate].
 */
class AWeighting(private val sampleRate: Double) {
    private val sections: List<Biquad>
    private val nyquist = sampleRate / 2

    init {
        // An analog pole's frequency (rad/s) moved so that the transform brings it back
        fun warp(hz: Double) = 2 * sampleRate * tan(PI * hz / sampleRate)
        val w1 = warp(F1)
        val w2 = warp(F2)
        val w3 = warp(F3)
        val w4 = warp(F4)
        sections = listOf(
            // s² / (s + ω1)²: the low cut
            Biquad.bilinear(1.0, 0.0, 0.0, 2 * w1, w1 * w1, sampleRate),
            // s² / ((s + ω2)(s + ω3)): the slope up to 1 kHz
            Biquad.bilinear(1.0, 0.0, 0.0, w2 + w3, w2 * w3, sampleRate),
            // 1 / (s + ω4)²: the high cut
            Biquad.bilinear(0.0, 0.0, 1.0, 2 * w4, w4 * w4, sampleRate),
        )
        // 0 dB at 1 kHz, as the standard has it
        sections[0].scale(1 / gainRatio(1000.0))
    }

    fun process(x: Double): Double {
        var y = x
        for (section in sections) y = section.process(y)
        return y
    }

    fun reset() = sections.forEach { it.reset() }

    /** The filter's gain at [hz] in dB (A1000 = 0); null above half the sample rate, which it can't pass. */
    fun gainDb(hz: Double): Double? = if (hz >= nyquist) null else 20 * log10(gainRatio(hz))

    private fun gainRatio(hz: Double): Double {
        val omega = 2 * PI * hz / sampleRate
        return sections.fold(1.0) { gain, section -> gain * section.gain(omega) }
    }

    companion object {
        const val F1 = 20.598997
        const val F2 = 107.65265
        const val F3 = 737.86223
        const val F4 = 12194.217

        /** The standard's A-weighting at [hz] in dB (its analog formula), what the filter is checked against. */
        fun standardDb(hz: Double): Double {
            val f2 = hz * hz
            val ra = F4 * F4 * f2 * f2 /
                ((f2 + F1 * F1) * sqrt((f2 + F2 * F2) * (f2 + F3 * F3)) * (f2 + F4 * F4))
            return 20 * log10(ra) + 2.0
        }
    }
}

/**
 * A sound level meter's readings from the samples given it ([add], each a part of full scale, -1..1, weighted
 * already if it's to be): the level as it is now, time-weighted Fast as meters do (an exponential average of
 * the squared samples over [timeConstant] s), and the mean square of each block of them, which the Leq is
 * the average of ([leqDbfs]: the steady level of the same energy).
 */
class LevelMeter(sampleRate: Double, timeConstant: Double = FAST) {
    private val alpha = 1 - exp(-1 / (timeConstant * sampleRate))
    private var meanSquare = 0.0
    private var blockEnergy = 0.0
    private var blockCount = 0

    /** The Fast level now, in dBFS. */
    val level: Double get() = dbfs(sqrt(meanSquare))

    fun add(sample: Double) {
        val square = sample * sample
        meanSquare += alpha * (square - meanSquare)
        blockEnergy += square
        blockCount++
    }

    /** The mean square of the samples since the last call (a part of full scale squared), 0 for none. */
    fun takeMeanSquare(): Double {
        val mean = if (blockCount > 0) blockEnergy / blockCount else 0.0
        blockEnergy = 0.0
        blockCount = 0
        return mean
    }

    companion object {
        /** Fast time weighting (IEC 61672), s. */
        const val FAST = 0.125
    }
}
