package org.lukemech.lmtoolbox.features.reference.fuses

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.Txt
import org.lukemech.lmtoolbox.features.reference.common.Wire
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** Car blade fuses (ISO 8820-3): the color of each rating, the same on regular (ATO) and mini fuses. */
object FusesFeature : ReferenceFeature() {

    override val route = "fuses"
    override val titleStringResId: Int = R.string.fp_fuses
    override val keywordsResId: Int = R.string.fk_fuses
    override val descriptionResId: Int = R.string.fd_fuses
    override val icon @Composable get() = Icons.Default.ElectricBolt

    private val FUSES: List<Triple<String, Color, Int>> = listOf(
        Triple("1 A", Wire.Black, R.string.rf_color_black),
        Triple("2 A", Wire.Grey, R.string.rf_color_grey),
        Triple("3 A", Wire.Violet, R.string.rf_color_violet),
        Triple("4 A", Wire.Pink, R.string.rf_color_pink),
        Triple("5 A", Wire.Tan, R.string.rf_color_tan),
        Triple("7.5 A", Wire.Brown, R.string.rf_color_brown),
        Triple("10 A", Wire.Red, R.string.rf_color_red),
        Triple("15 A", Wire.Blue, R.string.rf_color_blue),
        Triple("20 A", Wire.Yellow, R.string.rf_color_yellow),
        Triple("25 A", Wire.Natural, R.string.rf_color_natural),
        Triple("30 A", Wire.Green, R.string.rf_color_green),
        Triple("35 A", Wire.Aqua, R.string.rf_color_aqua),
        Triple("40 A", Wire.Orange, R.string.rf_color_orange),
    )

    override val sheets by lazy {
        listOf(
            TableSheet(
                raw("ATO / mini"),
                columns = listOf(res(R.string.rf_rating), res(R.string.rf_tc_color)),
                rows = FUSES.map { (rating, color, name) -> listOf(raw(rating), Txt.Swatch(listOf(color), res(name))) },
                note = res(R.string.rf_fuses_note)
            )
        )
    }
}
