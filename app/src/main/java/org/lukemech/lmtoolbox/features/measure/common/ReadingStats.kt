package org.lukemech.lmtoolbox.features.measure.common

/**
 * A meter's lowest, highest and average reading since it was last [reset]. Readings are to come evenly
 * spaced: a sensor that reports only on a change (the light sensor) is read on a tick instead, so a long
 * dark spell weighs more than a flicker.
 */
class ReadingStats {
    var min: Double? = null
        private set
    var max: Double? = null
        private set
    private var sum = 0.0
    private var count = 0

    /** The mean, null before any reading. */
    val mean: Double? get() = if (count > 0) sum / count else null

    fun add(value: Double) {
        min = minOf(min ?: value, value)
        max = maxOf(max ?: value, value)
        sum += value
        count++
    }

    fun reset() {
        min = null
        max = null
        sum = 0.0
        count = 0
    }
}
