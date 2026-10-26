package org.lukemech.lmtoolbox.features.reference.rs232

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputHdmi
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Outline
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Control
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Data
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Ground
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.inRows
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** RS-232 on a DB9 (DE-9, TIA-574): a computer's (DTE) serial port, each pin's direction from it. */
object Rs232Feature : ReferenceFeature() {

    override val route = "rs232"
    override val titleStringResId: Int = R.string.fp_rs232
    override val keywordsResId: Int = R.string.fk_rs232
    override val descriptionResId: Int = R.string.fd_rs232
    override val icon @Composable get() = Icons.Default.SettingsInputHdmi

    override val sheets by lazy {
        listOf(
            PinoutSheet(
                raw("DB9"),
                listOf(
                    Pin("1", "DCD", Control, res(R.string.rf_rs232_dcd)),
                    Pin("2", "RXD", Data, res(R.string.rf_rs232_rxd)),
                    Pin("3", "TXD", Data, res(R.string.rf_rs232_txd)),
                    Pin("4", "DTR", Control, res(R.string.rf_rs232_dtr)),
                    Pin("5", "GND", Ground, res(R.string.rf_rs232_gnd)),
                    Pin("6", "DSR", Control, res(R.string.rf_rs232_dsr)),
                    Pin("7", "RTS", Control, res(R.string.rf_rs232_rts)),
                    Pin("8", "CTS", Control, res(R.string.rf_rs232_cts)),
                    Pin("9", "RI", Control, res(R.string.rf_rs232_ri)),
                ).inRows(5, 4, shift = 0.5f),
                outline = Outline.Trapezoid,
                note = res(R.string.rf_rs232_note)
            )
        )
    }
}
