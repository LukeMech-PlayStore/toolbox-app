package org.lukemech.lmtoolbox.features.electronics.number_base

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.Txt
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.number_base.NumberBasePage

/** Number bases and bits: bin, oct, dec and hex, two's complement, IEEE 754 floats, and the ASCII table. */
object NumberBaseFeature : AppFeature {

    override val route = "number_base"
    override val titleStringResId: Int = R.string.fp_number_base
    override val keywordsResId: Int = R.string.fk_number_base
    override val descriptionResId: Int = R.string.fd_number_base
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Numbers

    /** The control codes 0–31: their abbreviations and names. */
    private val CONTROLS = listOf(
        "NUL" to R.string.rf_ascii_nul, "SOH" to R.string.rf_ascii_soh, "STX" to R.string.rf_ascii_stx,
        "ETX" to R.string.rf_ascii_etx, "EOT" to R.string.rf_ascii_eot, "ENQ" to R.string.rf_ascii_enq,
        "ACK" to R.string.rf_ascii_ack, "BEL" to R.string.rf_ascii_bel, "BS" to R.string.rf_ascii_bs,
        "HT" to R.string.rf_ascii_ht, "LF" to R.string.rf_ascii_lf, "VT" to R.string.rf_ascii_vt,
        "FF" to R.string.rf_ascii_ff, "CR" to R.string.rf_ascii_cr, "SO" to R.string.rf_ascii_so,
        "SI" to R.string.rf_ascii_si, "DLE" to R.string.rf_ascii_dle, "DC1" to R.string.rf_ascii_dc1,
        "DC2" to R.string.rf_ascii_dc2, "DC3" to R.string.rf_ascii_dc3, "DC4" to R.string.rf_ascii_dc4,
        "NAK" to R.string.rf_ascii_nak, "SYN" to R.string.rf_ascii_syn, "ETB" to R.string.rf_ascii_etb,
        "CAN" to R.string.rf_ascii_can, "EM" to R.string.rf_ascii_em, "SUB" to R.string.rf_ascii_sub,
        "ESC" to R.string.rf_ascii_esc, "FS" to R.string.rf_ascii_fs, "GS" to R.string.rf_ascii_gs,
        "RS" to R.string.rf_ascii_rs, "US" to R.string.rf_ascii_us,
    )

    /** C's escapes for the controls that have one. */
    private val ESCAPES = mapOf(0 to "\\0", 7 to "\\a", 8 to "\\b", 9 to "\\t", 10 to "\\n", 11 to "\\v", 12 to "\\f", 13 to "\\r", 27 to "\\e")

    private fun asciiRow(code: Int): List<Txt> {
        val (char, name) = when (code) {
            in 0..31 -> CONTROLS[code].first to res(CONTROLS[code].second)
            32 -> "SP" to res(R.string.rf_ascii_space)
            127 -> "DEL" to res(R.string.rf_ascii_del)
            else -> code.toChar().toString() to raw("")
        }
        return listOf(
            raw(code.toString()),
            raw("%02X".format(code)),
            raw(code.toString(2).padStart(7, '0')),
            raw(char),
            raw(ESCAPES[code] ?: (if (code < 32) "^" + (code + 64).toChar() else "")),
            name,
        )
    }

    /** ASCII, 0–127: each code in decimal, hex and binary, its character, and the control codes' names. */
    val table by lazy {
        TableSheet(
            raw("ASCII"),
            columns = listOf(raw("Dec"), raw("Hex"), raw("Bin"), res(R.string.rf_char), raw("C / ^"), res(R.string.rf_ascii_name)),
            rows = (0..127).map(::asciiRow),
            note = res(R.string.rf_ascii_note)
        )
    }

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        NumberBasePage()
    }
}
