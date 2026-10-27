package org.lukemech.lmtoolbox.features.reference.obd2

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Outline
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Data
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Ground
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Power
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Unused
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.inRows
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** OBD-II's diagnostic connector (SAE J1962): which bus is on which pin, and the pins left to the maker. */
object Obd2Feature : ReferenceFeature() {

    override val route = "obd2"
    override val titleStringResId: Int = R.string.fp_obd2
    override val keywordsResId: Int = R.string.fk_obd2
    override val descriptionResId: Int = R.string.fd_obd2
    override val icon @Composable get() = Icons.Default.DirectionsCar

    private fun maker(n: Int) = Pin(n.toString(), "OEM", Unused, res(R.string.rf_obd_oem))

    override val sheets by lazy {
        listOf(
            PinoutSheet(
                raw("J1962"),
                listOf(
                    maker(1),
                    Pin("2", "J1850 Bus+", Data, res(R.string.rf_obd_j1850p)),
                    maker(3),
                    Pin("4", "GND", Ground, res(R.string.rf_obd_chassis)),
                    Pin("5", "GND", Ground, res(R.string.rf_obd_signal_gnd)),
                    Pin("6", "CAN_H", Data, res(R.string.rf_obd_can)),
                    Pin("7", "K", Data, res(R.string.rf_obd_k)),
                    maker(8),
                    maker(9),
                    Pin("10", "J1850 Bus−", Data, res(R.string.rf_obd_j1850n)),
                    maker(11),
                    maker(12),
                    maker(13),
                    Pin("14", "CAN_L", Data, res(R.string.rf_obd_can)),
                    Pin("15", "L", Data, res(R.string.rf_obd_l)),
                    Pin("16", "+12V", Power, res(R.string.rf_obd_battery)),
                ).inRows(8, 8),
                outline = Outline.Trapezoid,
                note = res(R.string.rf_obd_note)
            )
        )
    }
}
