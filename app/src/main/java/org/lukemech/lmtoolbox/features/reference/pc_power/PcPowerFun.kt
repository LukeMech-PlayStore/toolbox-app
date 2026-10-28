package org.lukemech.lmtoolbox.features.reference.pc_power

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Control
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Ground
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Power
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Unused
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.Wire
import org.lukemech.lmtoolbox.features.reference.common.inRow
import org.lukemech.lmtoolbox.features.reference.common.inRows
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/**
 * A PC power supply's connectors: ATX's 24-pin (and the older 20-pin), the CPU's EPS 8-pin and ATX12V 4-pin, the
 * graphics card's PCIe 6 and 8-pin and 12V-2x6, and the drives' SATA, Molex and floppy.
 */
object PcPowerFeature : ReferenceFeature() {

    override val route = "pc_power"
    override val titleStringResId: Int = R.string.fp_pc_power
    override val keywordsResId: Int = R.string.fk_pc_power
    override val descriptionResId: Int = R.string.fd_pc_power
    override val icon @Composable get() = Icons.Default.ElectricalServices

    // ATX's wire colors, the same on every connector
    private fun v33(n: Int) = Pin(n.toString(), "+3.3V", Power, wire = listOf(Wire.Orange))
    private fun v5(n: Int) = Pin(n.toString(), "+5V", Power, wire = listOf(Wire.Red))
    private fun v12(n: Int) = Pin(n.toString(), "+12V", Power, wire = listOf(Wire.Yellow))
    private fun gnd(n: Int) = Pin(n.toString(), "COM", Ground, wire = listOf(Wire.Black))
    private fun sense(n: Int, name: String) = Pin(n.toString(), name, Ground, res(R.string.rf_pc_sense), wire = listOf(Wire.Black))

    override val sheets by lazy {
        val board = res(R.string.rf_pc_board)
        val card = res(R.string.rf_pc_card)
        val drives = res(R.string.rf_pc_drives)
        listOf(
            PinoutSheet(
                raw("ATX 24"),
                listOf(
                    v33(1), v33(2), gnd(3), v5(4), gnd(5), v5(6), gnd(7),
                    Pin("8", "PWR_OK", Control, res(R.string.rf_pc_pwr_ok), wire = listOf(Wire.Grey)),
                    Pin("9", "+5VSB", Power, res(R.string.rf_pc_5vsb), wire = listOf(Wire.Violet)),
                    v12(10), v12(11), v33(12),
                    v33(13).copy(description = res(R.string.rf_pc_3v3_sense)),
                    Pin("14", "−12V", Power, wire = listOf(Wire.Blue)),
                    gnd(15),
                    Pin("16", "PS_ON#", Control, res(R.string.rf_pc_ps_on), wire = listOf(Wire.Green)),
                    gnd(17), gnd(18), gnd(19),
                    Pin("20", "NC", Unused, res(R.string.rf_pc_nc)),
                    v5(21), v5(22), v5(23), gnd(24),
                ).inRows(12, 12),
                note = res(R.string.rf_pc_atx_note),
                group = board
            ),
            PinoutSheet(
                raw("ATX 20"),
                listOf(
                    v33(1), v33(2), gnd(3), v5(4), gnd(5), v5(6), gnd(7),
                    Pin("8", "PWR_OK", Control, res(R.string.rf_pc_pwr_ok), wire = listOf(Wire.Grey)),
                    Pin("9", "+5VSB", Power, res(R.string.rf_pc_5vsb), wire = listOf(Wire.Violet)),
                    v12(10),
                    v33(11).copy(description = res(R.string.rf_pc_3v3_sense)),
                    Pin("12", "−12V", Power, wire = listOf(Wire.Blue)),
                    gnd(13),
                    Pin("14", "PS_ON#", Control, res(R.string.rf_pc_ps_on), wire = listOf(Wire.Green)),
                    gnd(15), gnd(16), gnd(17),
                    Pin("18", "−5V", Power, res(R.string.rf_pc_minus5), wire = listOf(Wire.White)),
                    v5(19), v5(20),
                ).inRows(10, 10),
                note = res(R.string.rf_pc_atx20_note),
                group = board
            ),
            PinoutSheet(
                raw("EPS 8"),
                listOf(gnd(1), gnd(2), gnd(3), gnd(4), v12(5), v12(6), v12(7), v12(8)).inRows(4, 4),
                note = res(R.string.rf_pc_eps_note),
                group = board
            ),
            PinoutSheet(
                raw("ATX12V 4"),
                listOf(gnd(1), gnd(2), v12(3), v12(4)).inRows(2, 2),
                note = res(R.string.rf_pc_p4_note),
                group = board
            ),
            PinoutSheet(
                raw("PCIe 6"),
                listOf(v12(1), v12(2), v12(3), gnd(4), sense(5, "Sense"), gnd(6)).inRows(3, 3),
                note = res(R.string.rf_pc_pcie_note),
                group = card
            ),
            PinoutSheet(
                raw("PCIe 8"),
                listOf(v12(1), v12(2), v12(3), sense(4, "Sense2"), gnd(5), sense(6, "Sense1"), gnd(7), gnd(8)).inRows(4, 4),
                note = res(R.string.rf_pc_pcie_note),
                group = card
            ),
            PinoutSheet(raw("12V-2x6"), hpwr(), note = res(R.string.rf_pc_hpwr_note), group = card),
            PinoutSheet(
                raw("SATA"),
                listOf(
                    v33(1), v33(2), v33(3), gnd(4), gnd(5), gnd(6), v5(7), v5(8), v5(9), gnd(10),
                    Pin("11", "DAS/DSS", Control, res(R.string.rf_pc_sata_11)),
                    gnd(12), v12(13), v12(14), v12(15),
                ).inRow(),
                note = res(R.string.rf_pc_sata_note),
                group = drives
            ),
            PinoutSheet(raw("Molex"), listOf(v12(1), gnd(2), gnd(3), v5(4)).inRow(), note = res(R.string.rf_pc_molex_note), group = drives),
            PinoutSheet(raw("Floppy"), listOf(v5(1), gnd(2), gnd(3), v12(4)).inRow(), note = res(R.string.rf_pc_floppy_note), group = drives),
        )
    }

    /**
     * 12V-2x6 (12VHPWR before it): six +12 V over six grounds, and the four small sideband pins under them, which
     * tell the card how much it may draw.
     */
    private fun hpwr(): List<Pin> {
        val power = (1..6).map { v12(it) } + (7..12).map { gnd(it) }
        val sense = res(R.string.rf_pc_hpwr_sense)
        return power.inRows(6, 6) + listOf(
            Pin("S1", "CARD_PWR_STABLE", Control, res(R.string.rf_pc_hpwr_stable)),
            Pin("S2", "CARD_CBL_PRES#", Control, res(R.string.rf_pc_hpwr_present)),
            Pin("S3", "SENSE0", Control, sense),
            Pin("S4", "SENSE1", Control, sense),
        ).mapIndexed { i, pin -> pin.copy(x = i + 1f, y = 2f) }
    }
}
