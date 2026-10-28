package org.lukemech.lmtoolbox.features.reference.thermocouples

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.Txt
import org.lukemech.lmtoolbox.features.reference.common.Wire
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** Thermocouple types: their wires' alloys, IEC 60584-3's colors and IEC 60584-1's temperature ranges. */
object ThermocouplesFeature : ReferenceFeature() {

    override val route = "thermocouples"
    override val titleStringResId: Int = R.string.fp_thermocouples
    override val keywordsResId: Int = R.string.fk_thermocouples
    override val descriptionResId: Int = R.string.fd_thermocouples
    override val icon @Composable get() = Icons.Default.Thermostat

    /** A type: its letter, its + and − wires, its color (the + wire's and the sheath's) and its range (°C). */
    private class Type(val letter: String, val plus: String, val minus: String, val color: Color, val colorName: Int, val range: String)

    private val TYPES = listOf(
        Type("K", "Ni-Cr", "Ni-Al", Wire.Green, R.string.rf_color_green, "−270 … 1372"),
        Type("J", "Fe", "Cu-Ni", Wire.Black, R.string.rf_color_black, "−210 … 1200"),
        Type("T", "Cu", "Cu-Ni", Wire.Brown, R.string.rf_color_brown, "−270 … 400"),
        Type("E", "Ni-Cr", "Cu-Ni", Wire.Violet, R.string.rf_color_violet, "−270 … 1000"),
        Type("N", "Ni-Cr-Si", "Ni-Si", Wire.Pink, R.string.rf_color_pink, "−270 … 1300"),
        Type("R", "Pt-13%Rh", "Pt", Wire.Orange, R.string.rf_color_orange, "−50 … 1768"),
        Type("S", "Pt-10%Rh", "Pt", Wire.Orange, R.string.rf_color_orange, "−50 … 1768"),
        Type("B", "Pt-30%Rh", "Pt-6%Rh", Wire.Grey, R.string.rf_color_grey, "0 … 1820"),
    )

    override val sheets by lazy {
        listOf(
            TableSheet(
                raw("IEC 60584"),
                columns = listOf(res(R.string.rf_type), res(R.string.rf_tc_plus), res(R.string.rf_tc_minus), res(R.string.rf_tc_color), res(R.string.rf_tc_range)),
                rows = TYPES.map { t ->
                    listOf(
                        raw(t.letter), raw(t.plus), raw(t.minus),
                        Txt.Swatch(listOf(t.color, Wire.White), Txt.Join(listOf(res(t.colorName), res(R.string.rf_color_white)), " / ")),
                        raw(t.range),
                    )
                },
                note = res(R.string.rf_tc_note)
            )
        )
    }
}
