package org.lukemech.lmtoolbox.features.reference.capacitor_code

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.ESeries
import org.lukemech.lmtoolbox.features.electronics.common.formatNumber
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.ToolSheet
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res
import org.lukemech.lmtoolbox.ui.pages.features.reference.capacitor_code.CapacitorCodeDecoder

/** Capacitor marking codes: a code read ("104", "4n7", "223J"), the common codes, the tolerance letters. */
object CapacitorCodeFeature : ReferenceFeature() {

    override val route = "capacitor_code"
    override val titleStringResId: Int = R.string.fp_capacitor_code
    override val keywordsResId: Int = R.string.fk_capacitor_code
    override val descriptionResId: Int = R.string.fd_capacitor_code
    override val icon @Composable get() = Icons.Default.QrCodeScanner

    override val sheets by lazy {
        listOf(
            ToolSheet(res(R.string.rf_decode)) { CapacitorCodeDecoder() },
            // E12's values from 10 pF to 8.2 μF, each as its three digits read back
            TableSheet(
                res(R.string.rf_codes),
                columns = listOf(res(R.string.rf_code), raw("pF"), raw("nF"), raw("μF")),
                rows = (0..5).flatMap { multiplier ->
                    ESeries.E12.values.map { mantissa -> "${(mantissa * 10).roundToInt()}$multiplier" }
                }.map { code ->
                    val farads = parseCapacitorCode(code)!!.farads
                    listOf(raw(code), raw(formatNumber(farads * 1e12, 4)), raw(formatNumber(farads * 1e9, 4)), raw(formatNumber(farads * 1e6, 4)))
                }
            ),
            TableSheet(
                res(R.string.ec_tolerance),
                columns = listOf(res(R.string.rf_letter), res(R.string.ec_tolerance)),
                rows = TOLERANCES.map { (letter, tolerance) -> listOf(raw(letter.toString()), raw(tolerance)) },
                note = res(R.string.rf_tolerance_note)
            ),
        )
    }
}
