package org.lukemech.lmtoolbox.features.measure.common

import kotlin.math.ceil
import kotlin.math.floor

/**
 * A meter's graph's scale, on its page and in its PDF alike: from [low] to [high], a line across at every [every].
 * See [of].
 */
class GraphScale private constructor(val low: Float, val high: Float, val every: Float) {

    /** The lines' values, from [low] up. */
    val lines: List<Float> = generateSequence(low) { it + every }.takeWhile { it <= high + every / 2 }.toList()

    /** Where [value] is, from 0 (at [low]) to 1 (at [high]). */
    fun fraction(value: Float) = (value - low) / (high - low)

    companion object {
        /** The most lines across. */
        private const val MAX_LINES = 6

        /**
         * Whole [step]s around [values], from [min] to [max] at least; a line every [step], or every few when it's
         * widened a lot (a strong vibration): never more than MAX_LINES.
         */
        fun of(values: List<Float>, min: Float, max: Float, step: Float): GraphScale {
            val low = minOf(min, floor((values.minOrNull() ?: min) / step) * step)
            val high = maxOf(max, ceil((values.maxOrNull() ?: max) / step) * step)
            var every = step
            while ((high - low) / every > MAX_LINES) every *= 2
            return GraphScale(low, high, every)
        }

        /** A line's value: whole when it is (40, not 40.0), else to the places it has (0.25, not 0.3). */
        fun tick(value: Float) =
            if (value == value.toInt().toFloat()) value.toInt().toString()
            else "%.2f".format(value).trimEnd('0')
    }
}
