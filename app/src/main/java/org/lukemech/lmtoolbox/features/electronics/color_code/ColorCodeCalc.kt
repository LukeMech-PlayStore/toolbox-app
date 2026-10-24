package org.lukemech.lmtoolbox.features.electronics.color_code

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * [value]'s first [count] significant digits and the power of ten they're multiplied by, as a color
 * code has them; null when the multiplier has no color (outside 0.01–10⁹).
 */
internal fun significantDigits(value: Double, count: Int): Pair<List<Int>, Int>? {
    if (value <= 0 || !value.isFinite()) return null
    var exponent = floor(log10(value)).toInt() - (count - 1)
    var significant = (value / 10.0.pow(exponent)).roundToLong()
    // Rounded up to one digit more (e.g. 9.996 to two digits)
    if (significant >= 10.0.pow(count).toLong()) {
        significant /= 10
        exponent++
    }
    if (exponent !in -2..9) return null
    return significant.toString().padStart(count, '0').map { it.digitToInt() } to exponent
}
