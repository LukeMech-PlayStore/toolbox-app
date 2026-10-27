package org.lukemech.lmtoolbox.features.reference.arduino

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Analog
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Control
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Data
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Ground
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Power
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Signal
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Unused
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.Txt
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** The Arduino Uno's (R3) and Nano's headers, each pin with what else it does. */
object ArduinoFeature : ReferenceFeature() {

    override val route = "arduino"
    override val titleStringResId: Int = R.string.fp_arduino
    override val keywordsResId: Int = R.string.fk_arduino
    override val descriptionResId: Int = R.string.fd_arduino
    override val icon @Composable get() = Icons.Default.SettingsInputComponent

    private val PWM = raw("PWM")

    private fun and(vararg parts: Txt) = Txt.Join(parts.toList(), ", ")

    // The digital pins both boards share, by number: what else they do
    private val DIGITAL: Map<Int, Txt?> = mapOf(
        0 to raw("UART RX"),
        1 to raw("UART TX"),
        2 to res(R.string.rf_ard_int0),
        3 to and(PWM, res(R.string.rf_ard_int1)),
        4 to null,
        5 to PWM,
        6 to PWM,
        7 to null,
        8 to null,
        9 to PWM,
        10 to and(PWM, raw("SPI SS")),
        11 to and(PWM, raw("SPI MOSI")),
        12 to raw("SPI MISO"),
        13 to and(raw("SPI SCK"), res(R.string.rf_ard_led)),
    )

    private fun digital(n: Int, label: String = n.toString()) = Pin(label, "D$n", if (n in 10..13 || n < 2) Data else Signal, DIGITAL[n])

    private fun analog(n: Int) = Pin("A$n", "A$n", Analog, when (n) {
        4 -> raw("I²C SDA")
        5 -> raw("I²C SCL")
        6, 7 -> res(R.string.rf_ard_analog_only)
        else -> null
    })

    /** The Uno seen from above, the USB socket and the power jack on the left: its four headers. */
    private val UNO: List<Pin> by lazy {
        val top = listOf(
            Pin("SCL", "SCL", Data, res(R.string.rf_ard_scl)),
            Pin("SDA", "SDA", Data, res(R.string.rf_ard_sda)),
            Pin("AREF", "AREF", Analog, res(R.string.rf_ard_aref)),
            Pin("GND", "GND", Ground),
        ) + (13 downTo 8).map { digital(it) } + (7 downTo 0).map { digital(it) }
        val bottom = listOf(
            Pin("NC", "NC", Unused),
            Pin("IOREF", "IOREF", Power, res(R.string.rf_ard_ioref)),
            Pin("RST", "RESET", Control, res(R.string.rf_ard_reset)),
            Pin("3V3", "3.3V", Power, res(R.string.rf_ard_3v3)),
            Pin("5V", "5V", Power),
            Pin("GND", "GND", Ground),
            Pin("GND", "GND", Ground),
            Pin("VIN", "VIN", Power, res(R.string.rf_ard_vin)),
        ) + (0..5).map { analog(it) }
        // The ten-pin header, a gap, the eight-pin one; under them the power header, a gap, the analog one
        top.mapIndexed { i, pin -> pin.copy(x = if (i < 10) i.toFloat() else i + 0.6f, y = 0f) } +
            bottom.mapIndexed { i, pin -> pin.copy(x = if (i < 8) i + 1f else i + 2f, y = 3f) }
    }

    /** The Nano seen from above, USB at the top: pins 16–30 down the left side, 1–15 up the right. */
    private val NANO: List<Pin> by lazy {
        val right = listOf(
            digital(1, "1"), digital(0, "2"),
            Pin("3", "RESET", Control, res(R.string.rf_ard_reset)),
            Pin("4", "GND", Ground),
        ) + (2..12).mapIndexed { i, n -> digital(n, (5 + i).toString()) }
        val left = listOf(digital(13, "16"), Pin("17", "3V3", Power), Pin("18", "AREF", Analog, res(R.string.rf_ard_aref))) +
            (0..7).mapIndexed { i, n -> analog(n).copy(number = (19 + i).toString()) } +
            listOf(
                Pin("27", "5V", Power),
                Pin("28", "RESET", Control, res(R.string.rf_ard_reset)),
                Pin("29", "GND", Ground),
                Pin("30", "VIN", Power, res(R.string.rf_ard_vin_nano)),
            )
        right.mapIndexed { i, pin -> pin.copy(x = 1f, y = 14f - i) } + left.mapIndexed { i, pin -> pin.copy(x = 0f, y = i.toFloat()) }
    }

    override val sheets by lazy {
        listOf(
            PinoutSheet(raw("Uno R3"), UNO, note = res(R.string.rf_ard_uno_note)),
            PinoutSheet(raw("Nano"), NANO.sortedBy { it.number.toInt() }, note = res(R.string.rf_ard_nano_note)),
        )
    }
}
