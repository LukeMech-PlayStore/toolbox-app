package org.lukemech.lmtoolbox.features.reference.usb

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Control
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Data
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Ground
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Power
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Signal
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.Wire
import org.lukemech.lmtoolbox.features.reference.common.inRow
import org.lukemech.lmtoolbox.features.reference.common.inRows
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** USB's connectors: A, B and micro-B (2.0 and 3.x), mini-B and C, as the USB-IF's specifications number them. */
object UsbFeature : ReferenceFeature() {

    override val route = "usb"
    override val titleStringResId: Int = R.string.fp_usb
    override val keywordsResId: Int = R.string.fk_usb
    override val descriptionResId: Int = R.string.fd_usb
    override val icon @Composable get() = Icons.Default.Usb

    private val VBUS = res(R.string.rf_usb_vbus)
    private val DATA = res(R.string.rf_usb_data)

    // USB 2.0's four, with the colors their wires usually have
    private fun usb2(vararg extra: Pin) = listOf(
        Pin("1", "VBUS", Power, VBUS, wire = listOf(Wire.Red)),
        Pin("2", "D−", Data, DATA, wire = listOf(Wire.White)),
        Pin("3", "D+", Data, DATA, wire = listOf(Wire.Green)),
    ) + extra

    private val GND = Pin("4", "GND", Ground, wire = listOf(Wire.Black))

    /** Mini-B's and micro-B's five: USB 2.0's with the OTG ID pin before ground. */
    private fun otg() = usb2(Pin("4", "ID", Control, res(R.string.rf_usb_id)), GND.copy(number = "5"))

    /**
     * USB 3.x's five more, from pin [first]: the pair named [pair1] (SSRX on an A, as the host receives on
     * it; SSTX on a B), the drain, then [pair2].
     */
    private fun superSpeed(first: Int, pair1: String, pair2: String): List<Pin> {
        fun pair(name: String) = res(if (name == "SSTX") R.string.rf_usb_ss_tx else R.string.rf_usb_ss_rx)
        return listOf(
            Pin("$first", "$pair1−", Data, pair(pair1)),
            Pin("${first + 1}", "$pair1+", Data, pair(pair1)),
            Pin("${first + 2}", "GND_DRAIN", Ground, res(R.string.rf_usb_drain)),
            Pin("${first + 3}", "$pair2−", Data, pair(pair2)),
            Pin("${first + 4}", "$pair2+", Data, pair(pair2)),
        )
    }

    /** One row of a USB-C receptacle: A1–A12, or B1–B12 (the same, turned over). */
    private fun cRow(row: String, lane: Int, other: Int, cc: String, sbu: String) = listOf(
        Pin("${row}1", "GND", Ground),
        Pin("${row}2", "TX$lane+", Data, res(R.string.rf_usb_ss_tx)),
        Pin("${row}3", "TX$lane−", Data, res(R.string.rf_usb_ss_tx)),
        Pin("${row}4", "VBUS", Power, VBUS),
        Pin("${row}5", cc, Control, res(R.string.rf_usb_cc)),
        Pin("${row}6", "D+", Data, DATA),
        Pin("${row}7", "D−", Data, DATA),
        Pin("${row}8", sbu, Signal, res(R.string.rf_usb_sbu)),
        Pin("${row}9", "VBUS", Power, VBUS),
        Pin("${row}10", "RX$other−", Data, res(R.string.rf_usb_ss_rx)),
        Pin("${row}11", "RX$other+", Data, res(R.string.rf_usb_ss_rx)),
        Pin("${row}12", "GND", Ground),
    )

    override val sheets by lazy {
        listOf(
            PinoutSheet(raw("A (2.0)"), usb2(GND).inRow(), inOrder = true),
            PinoutSheet(
                raw("A (3.x)"),
                (usb2(GND) + superSpeed(5, "SSRX", "SSTX")).inRows(4, 5),
                note = res(R.string.rf_usb3_note),
                inOrder = true
            ),
            PinoutSheet(raw("B (2.0)"), usb2(GND).inRow(), inOrder = true),
            PinoutSheet(
                raw("B (3.x)"),
                (usb2(GND) + superSpeed(5, "SSTX", "SSRX")).inRows(4, 5),
                note = res(R.string.rf_usb3b_note),
                inOrder = true
            ),
            PinoutSheet(raw("Mini-B"), otg().inRow(), note = res(R.string.rf_usb_mini_note), inOrder = true),
            PinoutSheet(raw("Micro-B (2.0)"), otg().inRow(), note = res(R.string.rf_usb_micro_note), inOrder = true),
            PinoutSheet(
                raw("Micro-B (3.x)"),
                (otg() + superSpeed(6, "SSTX", "SSRX")).inRows(5, 5),
                note = res(R.string.rf_usb3_micro_note),
                inOrder = true
            ),
            PinoutSheet(
                raw("C"),
                // The receptacle from the front: A1 top left, and B1 under A12 (the plug fits either way up)
                cRow("A", 1, 2, "CC1", "SBU1").mapIndexed { i, pin -> pin.copy(x = i.toFloat(), y = 0f) } +
                    cRow("B", 2, 1, "CC2", "SBU2").mapIndexed { i, pin -> pin.copy(x = 11f - i, y = 1f) },
                note = res(R.string.rf_usbc_note)
            ),
        )
    }
}
