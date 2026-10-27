package org.lukemech.lmtoolbox.features.electronics.number_base

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The number base converter's maths (NumberBaseCalc). */
class NumberBaseCalcTest {
    @Test
    fun basesOfTheSameBits() {
        val p = parsePattern("42", Base.Dec, 8)!!
        assertEquals("101010", formatPattern(p, Base.Bin, 8, false))
        assertEquals("52", formatPattern(p, Base.Oct, 8, false))
        assertEquals("2A", formatPattern(p, Base.Hex, 8, false))
        assertEquals("0010 1010", groupedBits(p, 8))
    }

    @Test
    fun twosComplement() {
        val minusOne = parsePattern("-1", Base.Dec, 8)!!
        assertEquals("FF", formatPattern(minusOne, Base.Hex, 8, true))
        assertEquals("-1", formatPattern(minusOne, Base.Dec, 8, true))
        assertEquals("255", formatPattern(minusOne, Base.Dec, 8, false))
        // A negative decimal and the unsigned one of the same bits are the same pattern
        assertEquals(parsePattern("255", Base.Dec, 8), minusOne)
        assertEquals("-128", formatPattern(parsePattern("80", Base.Hex, 8)!!, Base.Dec, 8, true))
        assertEquals("-9223372036854775808", formatPattern(parsePattern("8000000000000000", Base.Hex, 64)!!, Base.Dec, 64, true))
        assertEquals("FFFFFFFFFFFFFFFF", formatPattern(parsePattern("18446744073709551615", Base.Dec, 64)!!, Base.Hex, 64, false))
    }

    @Test
    fun whatDoesNotFit() {
        assertNull(parsePattern("256", Base.Dec, 8))
        assertNull(parsePattern("-129", Base.Dec, 8))
        assertNull(parsePattern("100", Base.Hex, 8))
        assertEquals("1F", digitsOnly("1fg-", Base.Hex))
        assertEquals("-12", digitsOnly("-1a2", Base.Dec))
    }

    @Test
    fun ieee754() {
        // 0.1 in float32: 0x3DCCCCCD
        val f = floatPattern(0.1, FloatFormat.Float32)
        assertEquals(BigInteger("3DCCCCCD", 16), f)
        assertEquals("0 01111011 10011001100110011001101", floatBits(f, FloatFormat.Float32))
        val fields = floatFields(f, FloatFormat.Float32)
        assertEquals(123, fields.exponent)
        assertEquals(FloatClass.Normal, fields.kind)
        assertEquals(0.1f.toDouble(), floatValue(f, FloatFormat.Float32), 0.0)
        // 1.0 in float64: 0x3FF0000000000000; −0, ∞, a subnormal, NaN
        assertEquals(BigInteger("3FF0000000000000", 16), floatPattern(1.0, FloatFormat.Float64))
        assertEquals(1, floatFields(floatPattern(-0.0, FloatFormat.Float64), FloatFormat.Float64).sign)
        assertEquals(FloatClass.Infinity, floatFields(BigInteger("7F800000", 16), FloatFormat.Float32).kind)
        assertEquals(FloatClass.Subnormal, floatFields(BigInteger("00000001", 16), FloatFormat.Float32).kind)
        assertEquals(FloatClass.NaN, floatFields(BigInteger("7FC00000", 16), FloatFormat.Float32).kind)
        assertEquals(FloatClass.Zero, floatFields(BigInteger.ZERO, FloatFormat.Float64).kind)
    }
}
