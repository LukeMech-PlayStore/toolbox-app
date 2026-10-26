package org.lukemech.lmtoolbox.features.reference.capacitor_code

import kotlin.math.pow

/** A capacitor's marking read: its [farads], and its [tolerance] if it had a letter for it. */
internal data class CapacitorCode(val farads: Double, val tolerance: String?)

/**
 * The tolerance letters (IEC 60062): the first three in picofarads, for small capacitors; Z is the
 * lopsided one of ceramics that drift.
 */
internal val TOLERANCES = mapOf(
    'B' to "±0.1 pF", 'C' to "±0.25 pF", 'D' to "±0.5 pF", 'F' to "±1 %", 'G' to "±2 %",
    'J' to "±5 %", 'K' to "±10 %", 'M' to "±20 %", 'Z' to "+80 / −20 %",
)

private const val PICO = 1e-12

/** A letter standing for the decimal point and the unit: 4n7 is 4.7 nF, 1R5 is 1.5 pF. */
private val UNIT_LETTERS = mapOf('P' to 1e-12, 'R' to 1e-12, 'N' to 1e-9, 'U' to 1e-6, 'Μ' to 1e-6)

/**
 * Reads a capacitor's marking: three digits ("104": 10 · 10⁴ pF, an 8 or 9 last for 0.01 and 0.1), one or two
 * (picofarads: "47"), or a letter in place of the decimal point ("4n7", "p47", "2u2", "1R5"); any of them with
 * a tolerance letter after ("223J"). Null when it's none of those.
 */
internal fun parseCapacitorCode(text: String): CapacitorCode? {
    val code = text.trim().uppercase().replace("µ", "Μ").replace("μ", "Μ")
    if (code.isEmpty()) return null
    parseValue(code)?.let { return CapacitorCode(it, null) }
    val last = code.last()
    val tolerance = TOLERANCES[last] ?: return null
    return parseValue(code.dropLast(1))?.let { CapacitorCode(it, tolerance) }
}

private fun parseValue(code: String): Double? {
    if (code.isEmpty()) return null
    if (code.all { it.isDigit() }) {
        return when (code.length) {
            1, 2 -> code.toInt() * PICO
            3 -> {
                val significant = code.take(2).toInt()
                val multiplier = when (val m = code[2].digitToInt()) {
                    in 0..6 -> 10.0.pow(m)
                    8 -> 0.01
                    9 -> 0.1
                    else -> return null
                }
                significant * multiplier * PICO
            }
            else -> null
        }
    }
    val letters = code.filter { !it.isDigit() }
    if (letters.length != 1) return null
    val unit = UNIT_LETTERS[letters[0]] ?: return null
    val (whole, fraction) = code.split(letters[0])
    if (whole.isEmpty() && fraction.isEmpty()) return null
    return "${whole.ifEmpty { "0" }}.${fraction.ifEmpty { "0" }}".toDouble() * unit
}
