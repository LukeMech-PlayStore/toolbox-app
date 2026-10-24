package org.lukemech.lmtoolbox.features.electronics.smd_code

import kotlin.math.pow
import org.lukemech.lmtoolbox.features.electronics.common.ESeries

/** EIA-96 multiplier letters. */
internal val EIA96_MULTIPLIERS = mapOf(
    'Z' to 0.001, 'Y' to 0.01, 'R' to 0.01, 'X' to 0.1, 'S' to 0.1, 'A' to 1.0,
    'B' to 10.0, 'H' to 10.0, 'C' to 100.0, 'D' to 1e3, 'E' to 1e4, 'F' to 1e5,
)

/**
 * An SMD resistor's [code] in Ω: 3 digits (2 significant and the power of ten), 4 digits (3 and the
 * power), R as the decimal point ("4R7", "R10"), or EIA-96 (E96 index and a letter, "01C"). Null if
 * it's none of those.
 */
internal fun parseSmdCode(code: String): Double? {
    val c = code.trim().uppercase()
    if (c.length == 3 && c[0].isDigit() && c[1].isDigit() && c[2] in EIA96_MULTIPLIERS) {
        val index = c.substring(0, 2).toInt()
        if (index !in 1..96) return null
        return ESeries.E96.values[index - 1] * 100 * EIA96_MULTIPLIERS.getValue(c[2])
    }
    if (c.count { it == 'R' } == 1 && c.length in 2..4 && c.replace("R", "").all(Char::isDigit)) {
        return c.replace('R', '.').toDoubleOrNull()
    }
    if (c.length !in 3..4 || !c.all(Char::isDigit)) return null
    return c.dropLast(1).toInt() * 10.0.pow(c.last().digitToInt())
}
