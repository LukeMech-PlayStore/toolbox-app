package org.lukemech.lmtoolbox.features.electronics.rc_filter

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sqrt

/** The cutoff of R and L: fc = R / (2π·L). */
internal fun rlCutoff(r: Double, l: Double) = r / (2.0 * PI * l)

/** The resonance of L and C: f0 = 1 / (2π·√(L·C)). */
internal fun lcResonance(l: Double, c: Double) = 1.0 / (2.0 * PI * sqrt(l * c))

/** Series R, L and C's quality factor: Q = √(L / C) / R. */
internal fun seriesQ(r: Double, l: Double, c: Double) = sqrt(l / c) / r

/**
 * A first-order filter's gain (dB) at [x] = f / fc: 1 / √(1 + x²) for a low-pass, x / √(1 + x²) for a
 * high-pass.
 */
internal fun firstOrderGainDb(x: Double, highPass: Boolean): Double {
    val magnitude = 1.0 / sqrt(1.0 + x * x)
    return 20.0 * log10(if (highPass) x * magnitude else magnitude)
}

/**
 * A second-order filter's gain (dB) at [x] = f / f0 with quality factor [q]: across C (low-pass)
 * 1 / √((1 − x²)² + (x / Q)²), across L (high-pass) x² times that.
 */
internal fun secondOrderGainDb(x: Double, q: Double, highPass: Boolean): Double {
    val magnitude = 1.0 / sqrt((1 - x * x).let { it * it } + (x / q).let { it * it })
    return 20.0 * log10(if (highPass) x * x * magnitude else magnitude)
}

/**
 * Where a second-order filter's gain is −3 dB, as f / f0: |H|² = ½ is u² − (2 − 1/Q²)·u − 1 = 0 in
 * u = x², its positive root; the high-pass's is its reciprocal.
 */
internal fun secondOrderCutoffRatio(q: Double, highPass: Boolean): Double {
    val b = 2.0 - 1.0 / (q * q)
    val x = sqrt((b + sqrt(b * b + 4.0)) / 2.0)
    return if (highPass) 1.0 / x else x
}

/** A second-order filter's peak above its passband (dB), only with Q over 1/√2: Q / √(1 − 1/(4Q²)). */
internal fun secondOrderPeakDb(q: Double): Double? =
    if (q <= 1.0 / sqrt(2.0)) null else 20.0 * log10(q / sqrt(1.0 - 1.0 / (4.0 * q * q)))
