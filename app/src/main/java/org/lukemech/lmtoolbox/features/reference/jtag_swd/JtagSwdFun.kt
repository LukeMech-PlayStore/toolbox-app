package org.lukemech.lmtoolbox.features.reference.jtag_swd

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Clock
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Control
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Data
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Ground
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Power
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Unused
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.dualRow
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/**
 * ARM's debug headers: the 20-pin JTAG one (0.1"), the 10-pin Cortex Debug one for SWD (0.05") and its 20-pin form
 * with trace (Cortex Debug + ETM).
 */
object JtagSwdFeature : ReferenceFeature() {

    override val route = "jtag_swd"
    override val titleStringResId: Int = R.string.fp_jtag_swd
    override val keywordsResId: Int = R.string.fk_jtag_swd
    override val descriptionResId: Int = R.string.fd_jtag_swd
    override val icon @Composable get() = Icons.Default.BugReport

    private val VTREF = res(R.string.rf_jtag_vtref)

    private fun gnd(n: Int) = Pin(n.toString(), "GND", Ground)

    override val sheets by lazy {
        listOf(
            PinoutSheet(
                raw("JTAG 20"),
                listOf(
                    Pin("1", "VTref", Power, VTREF),
                    Pin("2", "Vsupply", Unused, res(R.string.rf_jtag_vsupply)),
                    Pin("3", "nTRST", Control, res(R.string.rf_jtag_ntrst)),
                    gnd(4),
                    Pin("5", "TDI", Data, res(R.string.rf_jtag_tdi)),
                    gnd(6),
                    Pin("7", "TMS / SWDIO", Data, res(R.string.rf_jtag_tms)),
                    gnd(8),
                    Pin("9", "TCK / SWCLK", Clock, res(R.string.rf_jtag_tck)),
                    gnd(10),
                    Pin("11", "RTCK", Clock, res(R.string.rf_jtag_rtck)),
                    gnd(12),
                    Pin("13", "TDO / SWO", Data, res(R.string.rf_jtag_tdo)),
                    gnd(14),
                    Pin("15", "nSRST", Control, res(R.string.rf_jtag_nsrst)),
                    gnd(16),
                    Pin("17", "DBGRQ", Control, res(R.string.rf_jtag_dbgrq)),
                    gnd(18),
                    Pin("19", "DBGACK", Control, res(R.string.rf_jtag_dbgack)),
                    gnd(20),
                ).dualRow(),
                note = res(R.string.rf_jtag_note)
            ),
            PinoutSheet(
                raw("SWD 10"),
                listOf(
                    Pin("1", "VTref", Power, VTREF),
                    Pin("2", "SWDIO / TMS", Data, res(R.string.rf_jtag_tms)),
                    gnd(3),
                    Pin("4", "SWCLK / TCK", Clock, res(R.string.rf_jtag_tck)),
                    gnd(5),
                    Pin("6", "SWO / TDO", Data, res(R.string.rf_jtag_tdo)),
                    Pin("7", "KEY", Unused, res(R.string.rf_jtag_key)),
                    Pin("8", "NC / TDI", Unused, res(R.string.rf_jtag_tdi)),
                    Pin("9", "GNDDetect", Ground, res(R.string.rf_jtag_gnddetect)),
                    Pin("10", "nRESET", Control, res(R.string.rf_jtag_nsrst)),
                ).dualRow(),
                note = res(R.string.rf_swd_note)
            ),
            PinoutSheet(
                raw("Cortex 20 (ETM)"),
                listOf(
                    Pin("1", "VTref", Power, VTREF),
                    Pin("2", "SWDIO / TMS", Data, res(R.string.rf_jtag_tms)),
                    gnd(3),
                    Pin("4", "SWCLK / TCK", Clock, res(R.string.rf_jtag_tck)),
                    gnd(5),
                    Pin("6", "SWO / TDO", Data, res(R.string.rf_jtag_tdo)),
                    Pin("7", "KEY", Unused, res(R.string.rf_jtag_key)),
                    Pin("8", "NC / TDI", Unused, res(R.string.rf_jtag_tdi)),
                    Pin("9", "GNDDetect", Ground, res(R.string.rf_jtag_gnddetect)),
                    Pin("10", "nRESET", Control, res(R.string.rf_jtag_nsrst)),
                    Pin("11", "GND / TgtPwr", Ground, res(R.string.rf_jtag_tgtpwr)),
                    Pin("12", "TRACECLK", Clock, res(R.string.rf_jtag_traceclk)),
                    Pin("13", "GND / TgtPwr", Ground, res(R.string.rf_jtag_tgtpwr)),
                    Pin("14", "TRACEDATA0", Data, res(R.string.rf_jtag_trace)),
                    gnd(15),
                    Pin("16", "TRACEDATA1", Data, res(R.string.rf_jtag_trace)),
                    gnd(17),
                    Pin("18", "TRACEDATA2", Data, res(R.string.rf_jtag_trace)),
                    gnd(19),
                    Pin("20", "TRACEDATA3", Data, res(R.string.rf_jtag_trace)),
                ).dualRow(),
                note = res(R.string.rf_cortex20_note)
            ),
        )
    }
}
