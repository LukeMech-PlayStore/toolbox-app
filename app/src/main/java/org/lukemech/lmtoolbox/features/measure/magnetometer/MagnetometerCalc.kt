package org.lukemech.lmtoolbox.features.measure.magnetometer

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/** The field's strength (µT) from its [x], [y] and [z] components: the same however the phone is turned. */
fun magnitude(x: Double, y: Double, z: Double) = sqrt(x * x + y * y + z * z)

/**
 * The detector's zero: the average strength of the first [count] readings given ([add]) after it's
 * (re)started, held away from metal. One reading alone would carry its noise into every deviation.
 */
class Baseline(private val count: Int = 25) {
    private var sum = 0.0
    private var taken = 0

    /** The baseline (µT), null until [count] readings are in. */
    var value: Double? = null
        private set

    fun add(magnitude: Double) {
        if (value != null) return
        sum += magnitude
        if (++taken == count) value = sum / count
    }

    fun restart() {
        sum = 0.0
        taken = 0
        value = null
    }
}

/**
 * How far the field's strength [magnitude] is from the [baseline] (µT): what metal nearby does, iron and
 * steel bending the Earth's field (and a magnet adding its own). The strength, not the vector: turning the
 * phone turns the vector, not the strength.
 */
fun deviation(magnitude: Double, baseline: Double) = abs(magnitude - baseline)

/** Smaller deviations are the sensor's noise and the field's drift (µT): the detector stays quiet. */
const val DETECT_FROM = 2.0

/** From this deviation on (µT), the detector is at its highest (a magnet, steel right at the phone). */
const val DETECT_FULL = 100.0

/**
 * How strongly the detector answers [deviation]: 0 below [DETECT_FROM], up to 1 at [DETECT_FULL], by ratio (the
 * field falls off fast with distance: 4 µT from 2 is as much nearer as 80 from 40).
 */
fun strength(deviation: Double): Double =
    if (deviation < DETECT_FROM) 0.0 else (ln(deviation / DETECT_FROM) / ln(DETECT_FULL / DETECT_FROM)).coerceAtMost(1.0)

/** The tone's pitch for [deviation] (Hz), rising with it; null (silent) for none. */
fun toneFrequency(deviation: Double): Double? {
    val s = strength(deviation)
    return if (s <= 0) null else TONE_LOW + (TONE_HIGH - TONE_LOW) * s
}

/** How often to buzz for [deviation] (ms between buzzes), faster as it grows; null (still) for none. */
fun buzzInterval(deviation: Double): Long? {
    val s = strength(deviation)
    return if (s <= 0) null else (BUZZ_SLOW - (BUZZ_SLOW - BUZZ_FAST) * s).toLong()
}

private const val TONE_LOW = 300.0
private const val TONE_HIGH = 1500.0
private const val BUZZ_SLOW = 600.0
private const val BUZZ_FAST = 80.0
