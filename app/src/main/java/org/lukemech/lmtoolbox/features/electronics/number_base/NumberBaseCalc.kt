package org.lukemech.lmtoolbox.features.electronics.number_base

import java.math.BigDecimal
import java.math.BigInteger

internal enum class Base(val radix: Int, val digits: String) {
    Bin(2, "01"),
    Oct(8, "01234567"),
    Dec(10, "0123456789"),
    Hex(16, "0123456789ABCDEF"),
}

/** What [text] may hold in [base]: its digits (hex's in capitals), a decimal's minus sign too. */
internal fun digitsOnly(text: String, base: Base): String =
    text.uppercase().filterIndexed { i, c -> c in base.digits || (base == Base.Dec && c == '-' && i == 0) }

private fun bits(width: Int): BigInteger = BigInteger.ONE.shiftLeft(width)

/**
 * [text] in [base] as a [width]-bit pattern (0 to 2^width − 1): bin, oct and hex as the bits themselves, a
 * decimal as the number they stand for, [signed] in two's complement. Null when it isn't one or doesn't fit.
 */
internal fun parsePattern(text: String, base: Base, width: Int, signed: Boolean): BigInteger? {
    if (text.isEmpty() || text == "-") return null
    val value = runCatching { BigInteger(text, base.radix) }.getOrNull() ?: return null
    if (base != Base.Dec) return value.takeIf { it < bits(width) }
    val min = if (signed) bits(width - 1).negate() else BigInteger.ZERO
    val max = (if (signed) bits(width - 1) else bits(width)) - BigInteger.ONE
    if (value < min || value > max) return null
    return if (value.signum() < 0) value + bits(width) else value
}

/** [pattern] ([width] bits) in [base]: a decimal as the number it stands for, [signed] in two's complement. */
internal fun formatPattern(pattern: BigInteger, base: Base, width: Int, signed: Boolean): String {
    val value = if (base == Base.Dec && signed && pattern.testBit(width - 1)) pattern - bits(width) else pattern
    return value.toString(base.radix).uppercase()
}

/** [pattern]'s [width] bits, in fours ("0000 1010"). */
internal fun groupedBits(pattern: BigInteger, width: Int): String =
    pattern.toString(2).padStart(width, '0').chunked(4).joinToString(" ")

/** An IEEE 754 binary format: float32 or float64, as its field widths. */
internal enum class FloatFormat(val width: Int, val exponentBits: Int) {
    Float32(32, 8),
    Float64(64, 11);

    val fractionBits get() = width - 1 - exponentBits
    val bias get() = (1 shl (exponentBits - 1)) - 1
}

/** What a floating-point pattern holds. */
internal enum class FloatClass { Zero, Subnormal, Normal, Infinity, NaN }

/** [value] rounded to [format] (to nearest, as the hardware does), as its bits. */
internal fun floatPattern(value: Double, format: FloatFormat): BigInteger = when (format) {
    FloatFormat.Float32 -> BigInteger.valueOf(value.toFloat().toRawBits().toLong() and 0xFFFFFFFFL)
    FloatFormat.Float64 -> BigInteger(java.lang.Long.toUnsignedString(value.toRawBits()))
}

/** The value [pattern] holds in [format], as a double (a float32's exactly). */
internal fun floatValue(pattern: BigInteger, format: FloatFormat): Double = when (format) {
    FloatFormat.Float32 -> Float.fromBits(pattern.toInt()).toDouble()
    FloatFormat.Float64 -> Double.fromBits(pattern.toLong())
}

/** A pattern's fields: the sign bit, the biased exponent and the fraction, and what they make it. */
internal data class FloatFields(val sign: Int, val exponent: Int, val fraction: BigInteger, val kind: FloatClass)

internal fun floatFields(pattern: BigInteger, format: FloatFormat): FloatFields {
    val fraction = pattern.and(bits(format.fractionBits) - BigInteger.ONE)
    val exponent = pattern.shiftRight(format.fractionBits).toInt() and ((1 shl format.exponentBits) - 1)
    val sign = if (pattern.testBit(format.width - 1)) 1 else 0
    val maxExponent = (1 shl format.exponentBits) - 1
    val kind = when {
        exponent == 0 && fraction.signum() == 0 -> FloatClass.Zero
        exponent == 0 -> FloatClass.Subnormal
        exponent == maxExponent && fraction.signum() == 0 -> FloatClass.Infinity
        exponent == maxExponent -> FloatClass.NaN
        else -> FloatClass.Normal
    }
    return FloatFields(sign, exponent, fraction, kind)
}

/** [pattern]'s bits split into sign, exponent and fraction ("0 10000000 1001…"). */
internal fun floatBits(pattern: BigInteger, format: FloatFormat): String {
    val all = pattern.toString(2).padStart(format.width, '0')
    return "${all[0]} ${all.substring(1, 1 + format.exponentBits)} ${all.substring(1 + format.exponentBits)}"
}

/** What a finite [stored] value differs by from the decimal typed ([typed]): stored − typed, exactly. */
internal fun roundingError(typed: String, stored: Double): BigDecimal? =
    runCatching { BigDecimal(stored).subtract(BigDecimal(typed)) }.getOrNull()
