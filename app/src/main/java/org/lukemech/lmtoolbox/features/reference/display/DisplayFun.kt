package org.lukemech.lmtoolbox.features.reference.display

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Outline
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Analog
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Clock
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Control
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Data
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Ground
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Power
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Shield
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Unused
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.Txt
import org.lukemech.lmtoolbox.features.reference.common.inRows
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res
import org.lukemech.lmtoolbox.features.reference.common.staggered

/**
 * A screen's connectors: HDMI's full-size (A), mini (C) and micro (D), DisplayPort and Mini DisplayPort, DVI-I and
 * VGA. HDMI's three have the same 19 signals, as DisplayPort's two have the same 20, each in its own order.
 */
object DisplayFeature : ReferenceFeature() {

    override val route = "display"
    override val titleStringResId: Int = R.string.fp_display
    override val keywordsResId: Int = R.string.fk_display
    override val descriptionResId: Int = R.string.fd_display
    override val icon @Composable get() = Icons.Default.Tv

    private class Signal(val kind: PinKind, val description: Txt)

    private val TMDS = Signal(Data, res(R.string.rf_display_tmds))
    private val TMDS_SHIELD = Signal(Shield, res(R.string.rf_display_shield))
    private val TMDS_CLOCK = Signal(Clock, res(R.string.rf_display_clock))

    private val hdmiSignals by lazy {
        mapOf(
            "TMDS Data2+" to TMDS, "TMDS Data2 Shield" to TMDS_SHIELD, "TMDS Data2−" to TMDS,
            "TMDS Data1+" to TMDS, "TMDS Data1 Shield" to TMDS_SHIELD, "TMDS Data1−" to TMDS,
            "TMDS Data0+" to TMDS, "TMDS Data0 Shield" to TMDS_SHIELD, "TMDS Data0−" to TMDS,
            "TMDS Clock+" to TMDS_CLOCK, "TMDS Clock Shield" to TMDS_SHIELD, "TMDS Clock−" to TMDS_CLOCK,
            "CEC" to Signal(Control, res(R.string.rf_display_cec)),
            "Utility" to Signal(Unused, res(R.string.rf_display_utility)),
            "SCL" to Signal(Clock, res(R.string.rf_display_scl)),
            "SDA" to Signal(Data, res(R.string.rf_display_sda)),
            "DDC/CEC GND" to Signal(Ground, res(R.string.rf_display_ddc_ground)),
            "+5V" to Signal(Power, res(R.string.rf_display_5v)),
            "HPD" to Signal(Control, res(R.string.rf_display_hpd)),
        )
    }

    private val dpSignals by lazy {
        val lane = Signal(Data, res(R.string.rf_display_dp_lane))
        val aux = Signal(Data, res(R.string.rf_display_dp_aux))
        mapOf(
            "ML_Lane0+" to lane, "ML_Lane0−" to lane, "ML_Lane1+" to lane, "ML_Lane1−" to lane,
            "ML_Lane2+" to lane, "ML_Lane2−" to lane, "ML_Lane3+" to lane, "ML_Lane3−" to lane,
            "GND" to Signal(Ground, res(R.string.rf_display_ground)),
            "CONFIG1" to Signal(Control, res(R.string.rf_display_dp_config1)),
            "CONFIG2" to Signal(Control, res(R.string.rf_display_dp_config2)),
            "AUX+" to aux, "AUX−" to aux,
            "HPD" to Signal(Control, res(R.string.rf_display_hpd)),
            "Return" to Signal(Ground, res(R.string.rf_display_dp_return)),
            "DP_PWR" to Signal(Power, res(R.string.rf_display_dp_pwr)),
        )
    }

    /** [names] as pins 1, 2, 3..., each as [signals] has it. */
    private fun numbered(signals: Map<String, Signal>, vararg names: String) = names.mapIndexed { i, name ->
        val signal = signals.getValue(name)
        Pin("${i + 1}", name, signal.kind, signal.description)
    }

    override val sheets by lazy {
        val hdmi = raw("HDMI")
        val dp = raw("DisplayPort")
        val analog = raw("DVI, VGA")
        listOf(
            PinoutSheet(
                raw("HDMI A"),
                numbered(
                    hdmiSignals,
                    "TMDS Data2+", "TMDS Data2 Shield", "TMDS Data2−", "TMDS Data1+", "TMDS Data1 Shield", "TMDS Data1−",
                    "TMDS Data0+", "TMDS Data0 Shield", "TMDS Data0−", "TMDS Clock+", "TMDS Clock Shield", "TMDS Clock−",
                    "CEC", "Utility", "SCL", "SDA", "DDC/CEC GND", "+5V", "HPD",
                ).staggered(),
                note = res(R.string.rf_display_staggered_note),
                group = hdmi
            ),
            PinoutSheet(
                raw("Mini HDMI (C)"),
                numbered(
                    hdmiSignals,
                    "TMDS Data2 Shield", "TMDS Data2+", "TMDS Data2−", "TMDS Data1 Shield", "TMDS Data1+", "TMDS Data1−",
                    "TMDS Data0 Shield", "TMDS Data0+", "TMDS Data0−", "TMDS Clock Shield", "TMDS Clock+", "TMDS Clock−",
                    "DDC/CEC GND", "CEC", "SCL", "SDA", "Utility", "+5V", "HPD",
                ).inRows(10, 9, shift = 0.5f),
                inOrder = true,
                group = hdmi
            ),
            PinoutSheet(
                raw("Micro HDMI (D)"),
                numbered(
                    hdmiSignals,
                    "HPD", "Utility", "TMDS Data2+", "TMDS Data2 Shield", "TMDS Data2−", "TMDS Data1+", "TMDS Data1 Shield",
                    "TMDS Data1−", "TMDS Data0+", "TMDS Data0 Shield", "TMDS Data0−", "TMDS Clock+", "TMDS Clock Shield",
                    "TMDS Clock−", "CEC", "DDC/CEC GND", "SCL", "SDA", "+5V",
                ).inRows(10, 9, shift = 0.5f),
                inOrder = true,
                group = hdmi
            ),
            PinoutSheet(
                raw("DisplayPort"),
                numbered(
                    dpSignals,
                    "ML_Lane0+", "GND", "ML_Lane0−", "ML_Lane1+", "GND", "ML_Lane1−", "ML_Lane2+", "GND", "ML_Lane2−",
                    "ML_Lane3+", "GND", "ML_Lane3−", "CONFIG1", "CONFIG2", "AUX+", "GND", "AUX−", "HPD", "Return", "DP_PWR",
                ).staggered(),
                note = res(R.string.rf_display_dp_note),
                group = dp
            ),
            PinoutSheet(
                raw("Mini DisplayPort"),
                numbered(
                    dpSignals,
                    "GND", "HPD", "ML_Lane0+", "CONFIG1", "ML_Lane0−", "CONFIG2", "GND", "GND", "ML_Lane1+", "ML_Lane3+",
                    "ML_Lane1−", "ML_Lane3−", "GND", "GND", "ML_Lane2+", "AUX+", "ML_Lane2−", "AUX−", "GND", "DP_PWR",
                ).inRows(10, 10),
                note = res(R.string.rf_display_dp_note),
                inOrder = true,
                group = dp
            ),
            PinoutSheet(raw("DVI-I"), dvi(), note = res(R.string.rf_display_dvi_note), group = analog),
            PinoutSheet(raw("VGA"), vga(), outline = Outline.Trapezoid, note = res(R.string.rf_display_vga_note), group = analog),
        )
    }

    /** DVI-I: three rows of eight digital pins, the analog ones around the flat C5 at their right. */
    private fun dvi(): List<Pin> {
        val ground = res(R.string.rf_display_ground)
        val digital = listOf(
            "TMDS Data2−" to TMDS, "TMDS Data2+" to TMDS, "TMDS Data2/4 Shield" to TMDS_SHIELD,
            "TMDS Data4−" to TMDS, "TMDS Data4+" to TMDS,
            "DDC Clock" to Signal(Clock, res(R.string.rf_display_scl)), "DDC Data" to Signal(Data, res(R.string.rf_display_sda)),
            "V-Sync" to Signal(Analog, res(R.string.rf_display_vsync)),
            "TMDS Data1−" to TMDS, "TMDS Data1+" to TMDS, "TMDS Data1/3 Shield" to TMDS_SHIELD,
            "TMDS Data3−" to TMDS, "TMDS Data3+" to TMDS,
            "+5V" to Signal(Power, res(R.string.rf_display_5v)), "GND" to Signal(Ground, ground),
            "HPD" to Signal(Control, res(R.string.rf_display_hpd)),
            "TMDS Data0−" to TMDS, "TMDS Data0+" to TMDS, "TMDS Data0/5 Shield" to TMDS_SHIELD,
            "TMDS Data5−" to TMDS, "TMDS Data5+" to TMDS,
            "TMDS Clock Shield" to TMDS_SHIELD, "TMDS Clock+" to TMDS_CLOCK, "TMDS Clock−" to TMDS_CLOCK,
        ).mapIndexed { i, (name, signal) -> Pin("${i + 1}", name, signal.kind, signal.description) }.inRows(8, 8, 8)
        val color = res(R.string.rf_display_analog_color)
        return digital + listOf(
            Pin("C1", "Red", Analog, color, x = 9f, y = 0f),
            Pin("C2", "Green", Analog, color, x = 10f, y = 0f),
            Pin("C3", "Blue", Analog, color, x = 9f, y = 2f),
            Pin("C4", "H-Sync", Analog, res(R.string.rf_display_hsync), x = 10f, y = 2f),
            Pin("C5", "Analog GND", Ground, res(R.string.rf_display_analog_ground), x = 9.5f, y = 1f),
        )
    }

    /** VGA's DE-15, the computer's socket from the front: 1 at the top right, the middle row half a pitch out to the left. */
    private fun vga(): List<Pin> {
        val color = res(R.string.rf_display_analog_color)
        val colorReturn = res(R.string.rf_display_analog_return)
        val reserved = res(R.string.rf_display_reserved)
        val ground = res(R.string.rf_display_ground)
        return listOf(
            Pin("1", "Red", Analog, color),
            Pin("2", "Green", Analog, color),
            Pin("3", "Blue", Analog, color),
            Pin("4", "ID2", Unused, reserved),
            Pin("5", "GND", Ground, ground),
            Pin("6", "Red return", Ground, colorReturn),
            Pin("7", "Green return", Ground, colorReturn),
            Pin("8", "Blue return", Ground, colorReturn),
            Pin("9", "+5V", Power, res(R.string.rf_display_vga_5v)),
            Pin("10", "Sync GND", Ground, ground),
            Pin("11", "ID0", Unused, reserved),
            Pin("12", "SDA", Data, res(R.string.rf_display_sda)),
            Pin("13", "H-Sync", Analog, res(R.string.rf_display_hsync)),
            Pin("14", "V-Sync", Analog, res(R.string.rf_display_vsync)),
            Pin("15", "SCL", Clock, res(R.string.rf_display_scl)),
        ).mapIndexed { i, pin ->
            val row = i / 5
            val col = i % 5
            pin.copy(x = (if (row == 1) 4f else 4.5f) - col, y = row.toFloat())
        }
    }
}
