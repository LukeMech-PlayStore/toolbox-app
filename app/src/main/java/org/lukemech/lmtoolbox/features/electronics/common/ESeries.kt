package org.lukemech.lmtoolbox.features.electronics.common

import kotlin.math.abs
import kotlin.math.pow

/**
 * The E-series of preferred component values (IEC 60063): E6, E12, E24, E48, E96, E192. The
 * values are the mantissas (1.0–9.1) that, with a power of ten, give the standard values.
 */
object ESeries {
    data class Series(val name: String, val tolerance: String, val values: List<Double>)

    private fun generate(n: Int): List<Double> {
        val out = ArrayList<Double>(n)
        for (i in 0 until n) {
            val v = roundSig3(10.0.pow(i.toDouble() / n))
            if (out.lastOrNull() != v) out.add(v)
        }
        return out
    }

    // Three significant figures, the values all being in [1, 10)
    private fun roundSig3(value: Double) = (value * 100).roundTo() / 100

    // E6...E24 as the standard has them, not as 10^(i/n) rounds (2.7, 3.3, 4.7... rather than 2.6, 3.2, 4.6)
    private val E24_VALUES = listOf(
        1.0, 1.1, 1.2, 1.3, 1.5, 1.6, 1.8, 2.0, 2.2, 2.4, 2.7, 3.0,
        3.3, 3.6, 3.9, 4.3, 4.7, 5.1, 5.6, 6.2, 6.8, 7.5, 8.2, 9.1,
    )

    val E6 = Series("E6", "20%", E24_VALUES.filterIndexed { i, _ -> i % 4 == 0 })
    val E12 = Series("E12", "10%", E24_VALUES.filterIndexed { i, _ -> i % 2 == 0 })
    val E24 = Series("E24", "5%", E24_VALUES)
    val E48 = Series("E48", "2%", generate(48))
    val E96 = Series("E96", "1%", generate(96))
    // 9.20 rather than 9.19, the one value the standard rounds otherwise
    val E192 = Series("E192", "0.5%", generate(192).map { if (it == 9.19) 9.20 else it })

    val all = listOf(E6, E12, E24, E48, E96, E192)

    /** The nearest standard value of [series] to [value] (mantissa space, so it works across decades). */
    fun nearest(series: Series, value: Double): Double {
        if (value <= 0) return 0.0
        var mantissa = value
        var scale = 1.0
        while (mantissa >= 10.0) { mantissa /= 10.0; scale *= 10.0 }
        while (mantissa < 1.0) { mantissa *= 10.0; scale /= 10.0 }
        val best = series.values.minByOrNull { abs(it - mantissa) } ?: return value
        return best * scale
    }

    /** The standard values of [series] in [lo]–[hi]. */
    fun valuesIn(series: Series, lo: Double, hi: Double): List<Double> =
        (-12..9).flatMap { exp -> series.values.map { it * 10.0.pow(exp) } }.filter { it in lo..hi }.distinct().sorted()
}
