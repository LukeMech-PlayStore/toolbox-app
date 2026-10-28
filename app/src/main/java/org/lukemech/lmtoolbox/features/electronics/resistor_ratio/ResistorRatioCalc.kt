package org.lukemech.lmtoolbox.features.electronics.resistor_ratio

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong
import org.lukemech.lmtoolbox.features.electronics.common.ESeries

/** What the ratio is of: R2 to R1, or a divider's output to its input, R2 to R1 + R2. */
internal enum class RatioKind { R2ToR1, Divider }

internal fun ratioOf(r1: Double, r2: Double, kind: RatioKind) = when (kind) {
    RatioKind.R2ToR1 -> r2 / r1
    RatioKind.Divider -> r2 / (r1 + r2)
}

/** A ratio of [from] as one of [to] (2 as R2/R1 is 2/3 as a divider's), null where there's none. */
internal fun convertRatio(value: Double, from: RatioKind, to: RatioKind): Double? = when {
    from == to -> value
    to == RatioKind.Divider -> value / (1 + value)
    value < 1 -> value / (1 - value)
    else -> null
}

/** A pair of standard values and how far its ratio is from the target's (a fraction, + over, − under). */
internal data class RatioPair(val r1: Double, val r2: Double, val ratio: Double, val error: Double)

/** The values of [series] just under (or at) and just over [value]. */
private fun around(series: ESeries.Series, value: Double): List<Double> {
    val scale = 10.0.pow(floor(log10(value)))
    val mantissa = value / scale
    val values = series.values
    val i = values.indexOfLast { it <= mantissa * (1 + 1e-9) }.coerceAtLeast(0)
    val above = values.getOrNull(i + 1) ?: (values.first() * 10)
    return listOf(values[i] * scale, above * scale)
}

/**
 * The pairs of [series] values whose ratio ([kind]) is nearest [target], the [count] best: R1 in 1–10 kΩ
 * (both can be scaled by a power of ten), R2 a standard value either side of what R1 needs.
 */
internal fun bestPairs(target: Double, kind: RatioKind, series: ESeries.Series, count: Int = 5): List<RatioPair> {
    val r2ToR1 = convertRatio(target, kind, RatioKind.R2ToR1)?.takeIf { it > 0 && it.isFinite() } ?: return emptyList()
    return series.values.asSequence().map { it * 1000 }
        .flatMap { r1 -> around(series, r1 * r2ToR1).map { r2 -> r1 to r2 } }
        .map { (r1, r2) ->
            val ratio = ratioOf(r1, r2, kind)
            RatioPair(r1, r2, ratio, (ratio - target) / target)
        }
        // Equally near (to rounding), the smaller R1 first
        .sortedWith(compareBy({ (abs(it.error) * 1e9).roundToLong() }, { it.r1 }))
        .take(count)
        .toList()
}
