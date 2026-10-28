package org.lukemech.lmtoolbox.features.reference.isp_icsp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
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
import org.lukemech.lmtoolbox.features.reference.common.inRow
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** Programming headers: AVR's 6 and 10-pin ISP and Microchip PIC's 6-pin ICSP (the PICkit one). */
object IspIcspFeature : ReferenceFeature() {

    private val AVR = raw("AVR")

    override val route = "isp_icsp"
    override val titleStringResId: Int = R.string.fp_isp_icsp
    override val keywordsResId: Int = R.string.fk_isp_icsp
    override val descriptionResId: Int = R.string.fd_isp_icsp
    override val icon @Composable get() = Icons.Default.Cable

    override val sheets by lazy {
        listOf(
            PinoutSheet(
                raw("AVR ISP"),
                listOf(
                    Pin("1", "MISO", Data, res(R.string.rf_isp_miso)),
                    Pin("2", "VCC", Power, res(R.string.rf_isp_vcc)),
                    Pin("3", "SCK", Clock, res(R.string.rf_isp_sck)),
                    Pin("4", "MOSI", Data, res(R.string.rf_isp_mosi)),
                    Pin("5", "RESET", Control, res(R.string.rf_isp_reset)),
                    Pin("6", "GND", Ground),
                ).dualRow(vertical = true),
                note = res(R.string.rf_isp_note),
                group = AVR
            ),
            PinoutSheet(
                raw("AVR ISP 10"),
                listOf(
                    Pin("1", "MOSI", Data, res(R.string.rf_isp_mosi)),
                    Pin("2", "VCC", Power, res(R.string.rf_isp_vcc)),
                    Pin("3", "NC", Unused),
                    Pin("4", "GND", Ground),
                    Pin("5", "RESET", Control, res(R.string.rf_isp_reset)),
                    Pin("6", "GND", Ground),
                    Pin("7", "SCK", Clock, res(R.string.rf_isp_sck)),
                    Pin("8", "GND", Ground),
                    Pin("9", "MISO", Data, res(R.string.rf_isp_miso)),
                    Pin("10", "GND", Ground),
                ).dualRow(vertical = true),
                note = res(R.string.rf_isp10_note),
                group = AVR
            ),
            PinoutSheet(
                raw("PIC ICSP"),
                listOf(
                    Pin("1", "MCLR / VPP", Control, res(R.string.rf_icsp_vpp)),
                    Pin("2", "VDD", Power, res(R.string.rf_isp_vcc)),
                    Pin("3", "VSS", Ground),
                    Pin("4", "PGD (ICSPDAT)", Data, res(R.string.rf_icsp_pgd)),
                    Pin("5", "PGC (ICSPCLK)", Clock, res(R.string.rf_icsp_pgc)),
                    Pin("6", "PGM (LVP)", Control, res(R.string.rf_icsp_pgm)),
                ).inRow(),
                note = res(R.string.rf_icsp_note),
                group = raw("PIC")
            ),
        )
    }
}
