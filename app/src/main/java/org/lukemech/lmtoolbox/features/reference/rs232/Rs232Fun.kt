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
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Shield
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Unused
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.inRows
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** RS-232 on a DB9 (DE-9, TIA-574) and a DB25: a computer's (DTE) serial port, each pin's direction from it. */
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
            ),
            PinoutSheet(raw("DB25"), db25(), outline = Outline.Trapezoid, note = res(R.string.rf_rs232_db25_note)),
        )
    }

    /** The original DB25 (EIA RS-232-C): the same nine signals, and a second channel and clocks hardly ever wired. */
    private fun db25(): List<Pin> {
        val rare = res(R.string.rf_rs232_rare)
        val names = mapOf(
            1 to Pin("1", "PG", Shield, res(R.string.rf_rs232_pg)),
            2 to Pin("2", "TXD", Data, res(R.string.rf_rs232_txd)),
            3 to Pin("3", "RXD", Data, res(R.string.rf_rs232_rxd)),
            4 to Pin("4", "RTS", Control, res(R.string.rf_rs232_rts)),
            5 to Pin("5", "CTS", Control, res(R.string.rf_rs232_cts)),
            6 to Pin("6", "DSR", Control, res(R.string.rf_rs232_dsr)),
            7 to Pin("7", "GND", Ground, res(R.string.rf_rs232_gnd)),
            8 to Pin("8", "DCD", Control, res(R.string.rf_rs232_dcd)),
            12 to Pin("12", "SDCD", Unused, rare),
            13 to Pin("13", "SCTS", Unused, rare),
            14 to Pin("14", "STXD", Unused, rare),
            15 to Pin("15", "TXC", Unused, rare),
            16 to Pin("16", "SRXD", Unused, rare),
            17 to Pin("17", "RXC", Unused, rare),
            18 to Pin("18", "LL", Unused, rare),
            19 to Pin("19", "SRTS", Unused, rare),
            20 to Pin("20", "DTR", Control, res(R.string.rf_rs232_dtr)),
            21 to Pin("21", "RL", Unused, rare),
            22 to Pin("22", "RI", Control, res(R.string.rf_rs232_ri)),
            23 to Pin("23", "DSRS", Unused, rare),
            24 to Pin("24", "ETXC", Unused, rare),
            25 to Pin("25", "TM", Unused, rare),
        )
        return (1..25).map { names[it] ?: Pin("$it", "—", Unused, res(R.string.rf_rs232_unassigned)) }.inRows(13, 12, shift = 0.5f)
    }
}
