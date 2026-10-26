package org.lukemech.lmtoolbox.features.measure.common

/**
 * A meter's lowest, highest and average reading since it was last [reset]. Each reading counts by its
 * weight: 1 for evenly spaced ones, how long it lasted for a sensor that reports only on a change (the
 * light sensor), so a long dark spell weighs more than a flicker.
 */
class ReadingStats {
    var min: Double? = null
        private set
    var max: Double? = null
        private set
    private var sum = 0.0
    private var weights = 0.0

    /** The weighted mean, null before any reading (or while they've all weighed nothing). */
    val mean: Double? get() = if (weights > 0) sum / weights else null

    fun add(value: Double, weight: Double = 1.0) {
        min = minOf(min ?: value, value)
        max = maxOf(max ?: value, value)
        if (weight > 0) {
            sum += value * weight
            weights += weight
        }
    }

    fun reset() {
        min = null
        max = null
        sum = 0.0
        weights = 0.0
    }
}
