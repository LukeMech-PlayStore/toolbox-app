package org.lukemech.lmtoolbox.features.electronics.common

import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField

internal fun Double.roundTo() = if (this >= 0) roundToLong().toDouble() else -(-this).roundToLong().toDouble()

/** SI prefixes the value fields understand and the results use. */
private val PREFIXES = listOf(
    "G" to 1e9, "M" to 1e6, "k" to 1e3, "" to 1.0,
    "m" to 1e-3, "μ" to 1e-6, "u" to 1e-6, "n" to 1e-9, "p" to 1e-12
)

/**
 * Parses a value the user typed, with an optional SI prefix and an optional unit, into a plain
 * number in base units: "1.1 kΩ" -> 1100.0, "100 μF" -> 1e-4, "20 m" -> 0.02, "5" -> 5.0.
 * Null when it's not a number.
 */
fun parseValue(text: String): Double? {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return null
    val m = Regex("^([+-]?\\d*\\.?\\d+(?:[eE][+-]?\\d+)?)\\s*([GMkmμunp])?").find(t) ?: return null
    val num = m.groupValues[1].toDoubleOrNull() ?: return null
    val factor = PREFIXES.firstOrNull { it.first == m.groupValues[2] }?.second ?: 1.0
    return num * factor
}

/** The SI prefix (and factor) that keeps [value] in a readable range (roughly 1–1000). */
private fun bestPrefix(value: Double): Pair<String, Double> {
    val v = abs(value)
    if (v == 0.0) return "" to 1.0
    for ((prefix, factor) in listOf("G" to 1e9, "M" to 1e6, "k" to 1e3, "" to 1.0, "m" to 1e-3, "μ" to 1e-6, "n" to 1e-9, "p" to 1e-12)) {
        if (v / factor >= 1.0) return prefix to factor
    }
    return "p" to 1e-12
}

/**
 * Formats [value] with the best SI prefix and [unit], e.g. 1100.0 + "Ω" -> "1.1 kΩ", 1e-4 + "F" ->
 * "100 μF". [sigFigs] significant figures.
 */
fun formatValue(value: Double, unit: String, sigFigs: Int = 4): String {
    if (value.isNaN() || value.isInfinite()) return "—"
    val (prefix, factor) = bestPrefix(value)
    val scaled = value / factor
    val rounded = if (scaled == 0.0) 0.0 else {
        val factor10 = 10.0.pow(sigFigs - 1)
        (scaled * factor10).roundTo() / factor10
    }
    var s = if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
    return "$s $prefix$unit"
}

/** [value] as a percentage with [digits] decimals. */
fun formatPercent(value: Double, digits: Int = 2): String =
    String.format(Locale.US, "%.${digits}f%%", value)

private const val SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

/**
 * [value] to [sigFigs] significant figures without a prefix, for what spans more than they do (energy in eV
 * and in kWh): plainly from 0.001 to a million, else as 1.234 × 10¹⁸.
 */
fun formatNumber(value: Double, sigFigs: Int = 5): String {
    if (!value.isFinite()) return "—"
    if (value == 0.0) return "0"
    val rounded = BigDecimal(value).round(MathContext(sigFigs))
    if (abs(value) >= 1e-3 && abs(value) < 1e6) return rounded.stripTrailingZeros().toPlainString()
    val exponent = rounded.precision() - rounded.scale() - 1
    val mantissa = rounded.movePointLeft(exponent).stripTrailingZeros().toPlainString()
    val power = exponent.toString().map { if (it == '-') '⁻' else SUPERSCRIPTS[it - '0'] }.joinToString("")
    return "$mantissa × 10$power"
}

/**
 * [value] as a [UnitField] of [unit] keeps it ("4.7 k"): with the prefix of the field's that reads best,
 * [sigFigs] significant figures.
 */
fun fieldText(value: Double, unit: String, sigFigs: Int = 4): String {
    if (!value.isFinite()) return ""
    val factors = UNIT_PREFIXES[unit].orEmpty().associateWith { p -> PREFIXES.first { it.first == p }.second }
    val prefix = factors.filter { abs(value) >= it.value }.maxByOrNull { it.value }?.key
        ?: factors.minByOrNull { it.value }?.key.takeIf { value != 0.0 } ?: ""
    val number = BigDecimal(value / (factors[prefix] ?: 1.0)).round(MathContext(sigFigs)).stripTrailingZeros().toPlainString()
    return if (prefix.isEmpty()) number else "$number $prefix"
}

/** The prefixes a field's unit is picked with, as they come up for it. */
internal val UNIT_PREFIXES = mapOf(
    "Ω" to listOf("m", "", "k", "M"),
    "F" to listOf("p", "n", "μ", "m", ""),
    "H" to listOf("n", "μ", "m", ""),
    "A" to listOf("μ", "m", ""),
    "V" to listOf("μ", "m", "", "k"),
    "W" to listOf("μ", "m", "", "k", "M"),
    "Hz" to listOf("", "k", "M", "G"),
    "s" to listOf("n", "μ", "m", ""),
    "m" to listOf("μ", "m", "", "k"),
)

/** [value] ("4.7 k") as its number and its prefix (one of [prefixes]). */
internal fun splitPrefix(value: String, prefixes: List<String>): Pair<String, String> {
    val t = value.trim()
    val last = t.lastOrNull()?.toString()?.replace("u", "μ") ?: return "" to ""
    return if (last in prefixes) t.dropLast(1).trim() to last else t to ""
}
