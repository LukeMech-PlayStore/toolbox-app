package org.lukemech.lmtoolbox.features.reference.si_prefixes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Superscript
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** The SI prefixes, quetta to quecto (with 2022's ronna, quetta, ronto and quecto). */
object SiPrefixesFeature : ReferenceFeature() {

    override val route = "si_prefixes"
    override val titleStringResId: Int = R.string.fp_si_prefixes
    override val keywordsResId: Int = R.string.fk_si_prefixes
    override val descriptionResId: Int = R.string.fd_si_prefixes
    override val icon @Composable get() = Icons.Default.Superscript

    private class Prefix(val name: Int, val symbol: String, val exponent: Int)

    private val PREFIXES = listOf(
        Prefix(R.string.rf_si_quetta, "Q", 30),
        Prefix(R.string.rf_si_ronna, "R", 27),
        Prefix(R.string.rf_si_yotta, "Y", 24),
        Prefix(R.string.rf_si_zetta, "Z", 21),
        Prefix(R.string.rf_si_exa, "E", 18),
        Prefix(R.string.rf_si_peta, "P", 15),
        Prefix(R.string.rf_si_tera, "T", 12),
        Prefix(R.string.rf_si_giga, "G", 9),
        Prefix(R.string.rf_si_mega, "M", 6),
        Prefix(R.string.rf_si_kilo, "k", 3),
        Prefix(R.string.rf_si_hecto, "h", 2),
        Prefix(R.string.rf_si_deca, "da", 1),
        Prefix(R.string.rf_si_deci, "d", -1),
        Prefix(R.string.rf_si_centi, "c", -2),
        Prefix(R.string.rf_si_milli, "m", -3),
        Prefix(R.string.rf_si_micro, "μ", -6),
        Prefix(R.string.rf_si_nano, "n", -9),
        Prefix(R.string.rf_si_pico, "p", -12),
        Prefix(R.string.rf_si_femto, "f", -15),
        Prefix(R.string.rf_si_atto, "a", -18),
        Prefix(R.string.rf_si_zepto, "z", -21),
        Prefix(R.string.rf_si_yocto, "y", -24),
        Prefix(R.string.rf_si_ronto, "r", -27),
        Prefix(R.string.rf_si_quecto, "q", -30),
    )

    private const val SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

    private fun power(exponent: Int) =
        "10" + exponent.toString().map { if (it == '-') '⁻' else SUPERSCRIPTS[it - '0'] }.joinToString("")

    // 1 000 000 or 0.000 001, in threes: for the short ones alone
    private fun decimal(exponent: Int): String = when {
        exponent > 9 || exponent < -9 -> "—"
        exponent >= 0 -> ("1" + "0".repeat(exponent)).reversed().chunked(3).joinToString(" ").reversed()
        else -> "0." + ("0".repeat(-exponent - 1) + "1").chunked(3).joinToString(" ")
    }

    override val sheets by lazy {
        listOf(
            TableSheet(
                raw("SI"),
                columns = listOf(res(R.string.rf_prefix), res(R.string.rf_symbol), res(R.string.rf_factor), res(R.string.rf_decimal)),
                rows = PREFIXES.map { listOf(res(it.name), raw(it.symbol), raw(power(it.exponent)), raw(decimal(it.exponent))) }
            )
        )
    }
}
