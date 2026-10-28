package org.lukemech.lmtoolbox.features.reference.ethernet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.Txt
import org.lukemech.lmtoolbox.features.reference.common.Wire
import org.lukemech.lmtoolbox.features.reference.common.inRow
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** Ethernet's RJ45 (8P8C): the T568A and T568B wiring, what each pin carries, and Power over Ethernet's modes. */
object EthernetFeature : ReferenceFeature() {

    override val route = "ethernet"
    override val titleStringResId: Int = R.string.fp_ethernet
    override val keywordsResId: Int = R.string.fk_ethernet
    override val descriptionResId: Int = R.string.fd_ethernet
    override val icon @Composable get() = Icons.Default.SettingsEthernet

    /** What each pin carries: 10/100BASE-T's (a computer's side, MDI) and 1000BASE-T's pairs. */
    private val SIGNALS = listOf(
        "TX+" to "BI_DA+", "TX−" to "BI_DA−", "RX+" to "BI_DB+", "—" to "BI_DC+",
        "—" to "BI_DC−", "RX−" to "BI_DB−", "—" to "BI_DD+", "—" to "BI_DD−",
    )

    // Pin by pin, a wire's colors: its own, or white with a stripe of it
    private val T568A = listOf(
        listOf(Wire.White, Wire.Green), listOf(Wire.Green), listOf(Wire.White, Wire.Orange), listOf(Wire.Blue),
        listOf(Wire.White, Wire.Blue), listOf(Wire.Orange), listOf(Wire.White, Wire.Brown), listOf(Wire.Brown),
    )
    private val T568B = listOf(
        listOf(Wire.White, Wire.Orange), listOf(Wire.Orange), listOf(Wire.White, Wire.Green), listOf(Wire.Blue),
        listOf(Wire.White, Wire.Blue), listOf(Wire.Green), listOf(Wire.White, Wire.Brown), listOf(Wire.Brown),
    )

    private val COLOR_NAMES = mapOf(
        Wire.Green to R.string.rf_color_green, Wire.Orange to R.string.rf_color_orange,
        Wire.Blue to R.string.rf_color_blue, Wire.Brown to R.string.rf_color_brown,
    )

    private fun colorName(wire: List<Color>): Txt = if (wire.size == 1) res(COLOR_NAMES.getValue(wire[0])) else {
        Txt.Join(listOf(res(R.string.rf_color_white), res(COLOR_NAMES.getValue(wire[1]))), "/")
    }

    private fun wiring(name: String, colors: List<List<Color>>) = PinoutSheet(
        raw(name),
        colors.mapIndexed { i, wire ->
            val (fast, gigabit) = SIGNALS[i]
            // Named by what it carries at 10/100 Mbit/s (where it carries anything) and at 1 Gbit/s
            val carries = if (fast == "—") gigabit else "$fast · $gigabit"
            Pin((i + 1).toString(), carries, PinKind.Data, colorName(wire), wire = wire)
        }.inRow(),
        note = res(R.string.rf_rj45_note)
    )

    override val sheets by lazy {
        listOf(
            wiring("T568A", T568A),
            wiring("T568B", T568B),
            TableSheet(
                res(R.string.rf_signals_poe),
                columns = listOf(res(R.string.rf_pin), raw("T568A"), raw("T568B"), raw("10/100BASE-T"), raw("1000BASE-T"), res(R.string.rf_poe_a), res(R.string.rf_poe_b)),
                rows = (0..7).map { i ->
                    val (fast, gigabit) = SIGNALS[i]
                    listOf(
                        raw((i + 1).toString()),
                        Txt.Swatch(T568A[i], colorName(T568A[i])),
                        Txt.Swatch(T568B[i], colorName(T568B[i])),
                        raw(fast),
                        raw(gigabit),
                        raw(if (i == 0 || i == 1) "DC+" else if (i == 2 || i == 5) "DC−" else "—"),
                        raw(if (i == 3 || i == 4) "DC+" else if (i == 6 || i == 7) "DC−" else "—"),
                    )
                },
                note = res(R.string.rf_poe_note)
            ),
        )
    }
}
