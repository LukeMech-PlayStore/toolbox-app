package org.lukemech.lmtoolbox.ui.pages.features.electronics.number_base

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.number_base.Base
import org.lukemech.lmtoolbox.features.electronics.number_base.FloatClass
import org.lukemech.lmtoolbox.features.electronics.number_base.FloatFormat
import org.lukemech.lmtoolbox.features.electronics.number_base.digitsOnly
import org.lukemech.lmtoolbox.features.electronics.number_base.floatBits
import org.lukemech.lmtoolbox.features.electronics.number_base.floatFields
import org.lukemech.lmtoolbox.features.electronics.number_base.floatPattern
import org.lukemech.lmtoolbox.features.electronics.number_base.floatValue
import org.lukemech.lmtoolbox.features.electronics.number_base.formatPattern
import org.lukemech.lmtoolbox.features.electronics.number_base.groupedBits
import org.lukemech.lmtoolbox.features.electronics.number_base.parsePattern
import org.lukemech.lmtoolbox.features.electronics.number_base.roundingError
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.features.electronics.number_base.NumberBaseFeature
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CodeField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula

@Composable
fun NumberBasePage() {
    var floating by rememberSaveable { mutableStateOf(false) }

    CalculatorPage(
        stringResource(R.string.fp_number_base),
        formulas = listOf(
            Formula("""N = \text{Σ} d_{i} · b^{i}""", stringResource(R.string.ec_positional)),
            Formula("""−x = 2^{n} − x""", stringResource(R.string.ec_twos_complement)),
            Formula("""x = (−1)^{s} · 1.f · 2^{e − bias}""", "IEEE 754"),
            Formula("""bias = 127 \text{ (float32)},  1023 \text{ (float64)}"""),
        ),
        tables = listOf(NumberBaseFeature.table)
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_mode),
            icon = Icons.Default.Tune,
            selected = floating,
            options = listOf(false to stringResource(R.string.ec_integer), true to stringResource(R.string.ec_floating_point)),
            onSelect = { floating = it }
        )
        RevealSection(!floating) { IntegerSection() }
        RevealSection(floating) { FloatSection() }
    }
}

/** The same bits in binary, octal, decimal and hex: the field typed in last is what the others follow. */
@Composable
private fun IntegerSection() {
    var width by rememberSaveable { mutableIntStateOf(8) }
    var source by rememberSaveable { mutableStateOf(Base.Dec) }
    var text by rememberSaveable { mutableStateOf("42") }

    val pattern = parsePattern(text, source, width)

    OptionSetting(
        title = stringResource(R.string.ec_word_size),
        icon = Icons.Default.ViewWeek,
        selected = width,
        options = listOf(8, 16, 32, 64).map { it to stringResource(R.string.ec_n_bits, it) },
        onSelect = { width = it }
    )
    // The decimal field takes a negative number too (as two's complement); the results read the bits both ways
    listOf(
        Base.Bin to R.string.ec_binary,
        Base.Oct to R.string.ec_octal,
        Base.Dec to R.string.ec_decimal,
        Base.Hex to R.string.ec_hexadecimal,
    ).forEach { (base, label) ->
        val shown = if (base == source) text else pattern?.let { formatPattern(it, base, width, signed = false) }.orEmpty()
        CodeField(stringResource(label), shown, if (base == Base.Hex) KeyboardType.Ascii else KeyboardType.Number) {
            source = base
            text = digitsOnly(it, base)
        }
    }
    Results {
        if (pattern != null) {
            MainResult(stringResource(R.string.ec_bit_pattern), groupedBits(pattern, width))
            ResultRow(stringResource(R.string.ec_unsigned_value), formatPattern(pattern, Base.Dec, width, signed = false))
            ResultRow(stringResource(R.string.ec_signed_value), formatPattern(pattern, Base.Dec, width, signed = true))
        }
    }
    Note(pluralStringResource(R.plurals.ec_does_not_fit, width, width), visible = pattern == null && text.isNotEmpty() && text != "-")
}

/** A floating-point number and its IEEE 754 bits, either typed. */
@Composable
private fun FloatSection() {
    var format by rememberSaveable { mutableStateOf(FloatFormat.Float32) }
    var fromBits by rememberSaveable { mutableStateOf(false) }
    var valueText by rememberSaveable { mutableStateOf("0.1") }
    var hexText by rememberSaveable { mutableStateOf("") }

    val digits = format.width / 4
    val pattern = if (fromBits) {
        hexText.takeIf { it.isNotEmpty() && it.length <= digits }?.let { BigInteger(it, 16) }
    } else {
        valueText.toDoubleOrNull()?.let { floatPattern(it, format) }
    }
    val stored = pattern?.let { floatValue(it, format) }

    OptionSetting(
        title = stringResource(R.string.ec_precision),
        icon = Icons.Default.DataObject,
        selected = format,
        options = listOf(FloatFormat.Float32 to "float32 (single)", FloatFormat.Float64 to "float64 (double)"),
        onSelect = {
            // From the bits, the number they held goes on (the new format's bits are another length)
            if (fromBits) {
                valueText = stored?.let { value -> shortest(value, format) }.orEmpty()
                fromBits = false
            }
            format = it
        }
    )
    CodeField(
        stringResource(R.string.ec_decimal),
        if (fromBits) stored?.let { shortest(it, format) }.orEmpty() else valueText,
        KeyboardType.Decimal
    ) {
        fromBits = false
        valueText = it.filter { c -> c in "0123456789.-+E" }
    }
    CodeField(
        stringResource(R.string.ec_hex_bits),
        if (fromBits) hexText else pattern?.toString(16)?.uppercase()?.padStart(digits, '0').orEmpty()
    ) {
        fromBits = true
        hexText = digitsOnly(it, Base.Hex).take(digits)
    }
    Results {
        if (pattern != null && stored != null) {
            val fields = floatFields(pattern, format)
            MainResult(stringResource(R.string.ec_bit_pattern), floatBits(pattern, format))
            ResultRow(
                stringResource(R.string.ec_float_class),
                stringResource(
                    when (fields.kind) {
                        FloatClass.Zero -> R.string.ec_float_zero
                        FloatClass.Subnormal -> R.string.ec_float_subnormal
                        FloatClass.Normal -> R.string.ec_float_normal
                        FloatClass.Infinity -> R.string.ec_float_infinity
                        FloatClass.NaN -> R.string.ec_float_nan
                    }
                )
            )
            ResultRow(stringResource(R.string.ec_sign), if (fields.sign == 0) "0 (+)" else "1 (−)")
            if (fields.kind == FloatClass.Normal || fields.kind == FloatClass.Subnormal) {
                // A subnormal's exponent is the smallest normal one's, without the leading 1
                val unbiased = if (fields.kind == FloatClass.Normal) fields.exponent - format.bias else 1 - format.bias
                ResultRow(stringResource(R.string.ec_exponent), "${fields.exponent} − ${format.bias} = $unbiased")
            } else {
                ResultRow(stringResource(R.string.ec_exponent), fields.exponent.toString())
            }
            ResultRow(stringResource(R.string.ec_fraction), "0x" + fields.fraction.toString(16).uppercase())
            if (stored.isFinite()) {
                ResultRow(stringResource(R.string.ec_stored_value), BigDecimal(stored).round(MathContext(30)).stripTrailingZeros().toString())
                if (!fromBits) roundingError(valueText, stored)?.let {
                    ResultRow(stringResource(R.string.ec_rounding_error), if (it.signum() == 0) "0" else it.round(MathContext(6)).toString())
                }
            }
        }
    }
    Note(stringResource(R.string.ec_invalid_number), visible = pattern == null && (if (fromBits) hexText else valueText).isNotEmpty())
}

/** The shortest decimal that reads back as [value] in [format], with no exponent; nothing for ∞ and NaN. */
private fun shortest(value: Double, format: FloatFormat): String {
    if (!value.isFinite()) return ""
    val text = if (format == FloatFormat.Float32) value.toFloat().toString() else value.toString()
    return BigDecimal(text).stripTrailingZeros().toPlainString()
}
