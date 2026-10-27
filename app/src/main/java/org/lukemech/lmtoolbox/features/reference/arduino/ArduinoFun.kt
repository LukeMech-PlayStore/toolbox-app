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

/**
 * Arduino boards' headers, each pin with what else it does: the Uno-sized ones (Uno R3 and R4, Leonardo), the Mega
 * 2560, the Nano-sized ones (Nano, Nano Every, Nano 33) and the Pro Mini.
 */
object ArduinoFeature : ReferenceFeature() {

    override val route = "arduino"
    override val titleStringResId: Int = R.string.fp_arduino
    override val keywordsResId: Int = R.string.fk_arduino
    override val descriptionResId: Int = R.string.fd_arduino
    override val icon @Composable get() = Icons.Default.SettingsInputComponent

    private val PWM = raw("PWM")
    private val INT = res(R.string.rf_ard_int)

    private fun and(vararg parts: Txt) = Txt.Join(parts.toList(), ", ")

    /** What a board's digital pins do besides: by number, and which of them carry data (a UART, SPI, I²C). */
    private class Digital(val functions: Map<Int, Txt>, val data: Set<Int>) {
        fun pin(n: Int, label: String = n.toString()) = Pin(label, "D$n", if (n in data) Data else Signal, functions[n])
    }

    // The ATmega328P's (Uno R3, Nano, Pro Mini)
    private val ATMEGA328 = Digital(
        mapOf(
            0 to raw("UART RX"),
            1 to raw("UART TX"),
            2 to res(R.string.rf_ard_int0),
            3 to and(PWM, res(R.string.rf_ard_int1)),
            5 to PWM,
            6 to PWM,
            9 to PWM,
            10 to and(PWM, raw("SPI SS")),
            11 to and(PWM, raw("SPI MOSI")),
            12 to raw("SPI MISO"),
            13 to and(raw("SPI SCK"), res(R.string.rf_ard_led)),
        ),
        setOf(0, 1, 10, 11, 12, 13)
    )

    private val UNO_R4 = Digital(
        mapOf(
            0 to raw("Serial1 RX"),
            1 to raw("Serial1 TX"),
            2 to INT,
            3 to and(PWM, INT),
            4 to raw("CAN TX"),
            5 to and(PWM, raw("CAN RX")),
            6 to PWM,
            9 to PWM,
            10 to and(PWM, raw("SPI SS")),
            11 to and(PWM, raw("SPI MOSI")),
            12 to raw("SPI MISO"),
            13 to and(raw("SPI SCK"), res(R.string.rf_ard_led)),
        ),
        setOf(0, 1, 4, 5, 10, 11, 12, 13)
    )

    // The ATmega32U4's: SPI only on the ICSP header, analog inputs on some digital pins
    private val LEONARDO = Digital(
        mapOf(
            0 to and(raw("Serial1 RX"), INT),
            1 to and(raw("Serial1 TX"), INT),
            2 to and(raw("I²C SDA"), INT),
            3 to and(PWM, raw("I²C SCL"), INT),
            4 to raw("A6"),
            5 to PWM,
            6 to and(PWM, raw("A7")),
            7 to INT,
            8 to raw("A8"),
            9 to and(PWM, raw("A9")),
            10 to and(PWM, raw("A10")),
            11 to PWM,
            12 to raw("A11"),
            13 to and(PWM, res(R.string.rf_ard_led)),
        ),
        setOf(0, 1, 2, 3)
    )

    private val MEGA = Digital(
        (2..13).associateWith { PWM } + (44..46).associateWith { PWM } + mapOf(
            0 to raw("Serial RX0"),
            1 to raw("Serial TX0"),
            2 to and(PWM, INT),
            3 to and(PWM, INT),
            13 to and(PWM, res(R.string.rf_ard_led)),
            14 to raw("Serial3 TX3"),
            15 to raw("Serial3 RX3"),
            16 to raw("Serial2 TX2"),
            17 to raw("Serial2 RX2"),
            18 to and(raw("Serial1 TX1"), INT),
            19 to and(raw("Serial1 RX1"), INT),
            20 to and(raw("I²C SDA"), INT),
            21 to and(raw("I²C SCL"), INT),
            50 to raw("SPI MISO"),
            51 to raw("SPI MOSI"),
            52 to raw("SPI SCK"),
            53 to raw("SPI SS"),
        ),
        setOf(0, 1) + (14..21) + (50..53)
    )

    private val NANO_EVERY = Digital(
        mapOf(
            0 to raw("Serial1 RX"),
            1 to raw("Serial1 TX"),
            3 to PWM,
            5 to PWM,
            6 to PWM,
            9 to PWM,
            10 to PWM,
            11 to raw("SPI MOSI"),
            12 to raw("SPI MISO"),
            13 to and(raw("SPI SCK"), res(R.string.rf_ard_led)),
        ),
        setOf(0, 1, 11, 12, 13)
    )

    private val NANO_33 = Digital(
        mapOf(
            0 to raw("Serial1 RX"),
            1 to raw("Serial1 TX"),
            10 to raw("SPI SS"),
            11 to raw("SPI MOSI"),
            12 to raw("SPI MISO"),
            13 to and(raw("SPI SCK"), res(R.string.rf_ard_led)),
        ),
        setOf(0, 1, 10, 11, 12, 13)
    )

    /** An analog input, A[n]; [also]: what else it does (I²C on A4, A5). */
    private fun analog(n: Int, also: Txt? = null) = Pin("A$n", "A$n", Analog, also)

    private fun uno328Analog(n: Int) = analog(n, when (n) {
        4 -> raw("I²C SDA")
        5 -> raw("I²C SCL")
        6, 7 -> res(R.string.rf_ard_analog_only)
        else -> null
    })

    private fun gnd(label: String = "GND") = Pin(label, "GND", Ground)

    /**
     * An Uno-sized board seen from above, the USB socket and the power jack on the left: the ten and eight-pin
     * headers at the top (SCL, SDA, AREF, GND, D13–D0), the power one and the analog one under them, and,
     * on the Mega, its longer ones.
     */
    private fun unoShape(
        digital: Digital,
        analog: (Int) -> Pin,
        scl: Txt,
        sda: Txt,
        vin: Txt,
        v33: Txt?,
        analogs: Int = 6,
        moreTop: List<Pin> = emptyList(),
    ): List<Pin> {
        val top = listOf(
            Pin("SCL", "SCL", Data, scl),
            Pin("SDA", "SDA", Data, sda),
            Pin("AREF", "AREF", Analog, res(R.string.rf_ard_aref)),
            gnd(),
        ) + (13 downTo 8).map { digital.pin(it) } + (7 downTo 0).map { digital.pin(it) } + moreTop
        val bottom = listOf(
            Pin("NC", "NC", Unused),
            Pin("IOREF", "IOREF", Power, res(R.string.rf_ard_ioref)),
            Pin("RST", "RESET", Control, res(R.string.rf_ard_reset)),
            Pin("3V3", "3.3V", Power, v33),
            Pin("5V", "5V", Power),
            gnd(),
            gnd(),
            Pin("VIN", "VIN", Power, vin),
        ) + (0 until analogs).map(analog)
        // Headers of eight or ten pins, a gap between them; the bottom ones under the top ones, offset as on the board
        fun along(i: Int, first: Int) = if (i < first) i.toFloat() else i + 0.6f * (1 + (i - first) / 8)
        val bottomRow = if (moreTop.isEmpty()) 3f else 17f
        return top.mapIndexed { i, pin -> pin.copy(x = along(i, 10), y = 0f) } +
            bottom.mapIndexed { i, pin -> pin.copy(x = along(i, 8) + 1f, y = bottomRow) }
    }

    private val UNO_R3: List<Pin> by lazy {
        unoShape(ATMEGA328, ::uno328Analog, res(R.string.rf_ard_scl), res(R.string.rf_ard_sda), res(R.string.rf_ard_vin), res(R.string.rf_ard_3v3))
    }

    private val UNO_R4_PINS: List<Pin> by lazy {
        unoShape(
            UNO_R4,
            { n ->
                analog(n, when (n) {
                    0 -> res(R.string.rf_ard_dac)
                    1 -> res(R.string.rf_ard_opamp_plus)
                    2 -> res(R.string.rf_ard_opamp_minus)
                    3 -> res(R.string.rf_ard_opamp_out)
                    4 -> raw("I²C SDA")
                    5 -> raw("I²C SCL")
                    else -> null
                })
            },
            res(R.string.rf_ard_scl), res(R.string.rf_ard_sda), res(R.string.rf_ard_vin_r4), null
        )
    }

    private val LEONARDO_PINS: List<Pin> by lazy {
        unoShape(LEONARDO, { analog(it) }, res(R.string.rf_ard_scl_d3), res(R.string.rf_ard_sda_d2), res(R.string.rf_ard_vin), res(R.string.rf_ard_3v3))
    }

    /** The Mega: the Uno's headers longer (D14–D21 at the top, A8–A15 under), and the double one along its right edge. */
    private val MEGA_PINS: List<Pin> by lazy {
        val headers = unoShape(
            MEGA, { analog(it) }, res(R.string.rf_ard_scl_d21), res(R.string.rf_ard_sda_d20), res(R.string.rf_ard_vin), res(R.string.rf_ard_3v3),
            analogs = 16,
            moreTop = (14..21).map { MEGA.pin(it) }
        )
        // Pairs down the edge: 5V and 5V, 22 and 23... 52 and 53, GND and GND; the even ones inside
        val double = listOf(Pin("5V", "5V", Power), Pin("5V", "5V", Power)) +
            (22..53).map { MEGA.pin(it) } +
            listOf(gnd(), gnd())
        val x = headers.maxOf { it.x } + 1.2f
        headers + double.mapIndexed { i, pin -> pin.copy(x = x + i % 2, y = (i / 2).toFloat()) }
    }

    /**
     * A Nano-sized board seen from above, USB at the top: pins 16–30 down the left side, 1–15 up the right,
     * numbered around the board from TX (1). [v5]: pin 27's name and what it does, [vin] pin 30's.
     */
    private fun nanoShape(digital: Digital, analog: (Int) -> Pin, vin: Txt, v5: Pair<String, Txt?> = "5V" to null, v33: Txt? = null): List<Pin> {
        val right = listOf(
            digital.pin(1, "1"), digital.pin(0, "2"),
            Pin("3", "RESET", Control, res(R.string.rf_ard_reset)),
            Pin("4", "GND", Ground),
        ) + (2..12).mapIndexed { i, n -> digital.pin(n, (5 + i).toString()) }
        val left = listOf(digital.pin(13, "16"), Pin("17", "3V3", Power, v33), Pin("18", "AREF", Analog, res(R.string.rf_ard_aref))) +
            (0..7).mapIndexed { i, n -> analog(n).copy(number = (19 + i).toString()) } +
            listOf(
                Pin("27", v5.first, Power, v5.second),
                Pin("28", "RESET", Control, res(R.string.rf_ard_reset)),
                Pin("29", "GND", Ground),
                Pin("30", "VIN", Power, vin),
            )
        return (right.mapIndexed { i, pin -> pin.copy(x = 1f, y = 14f - i) } + left.mapIndexed { i, pin -> pin.copy(x = 0f, y = i.toFloat()) })
            .sortedBy { it.number.toInt() }
    }

    private fun nanoAnalog(n: Int) = analog(n, when (n) {
        4 -> raw("I²C SDA")
        5 -> raw("I²C SCL")
        else -> null
    })

    /**
     * The Pro Mini seen from above, the programming (FTDI) header at the top: TXO's side on the left, RAW's on the
     * right, A4–A7's pads inside.
     */
    private val PRO_MINI: List<Pin> by lazy {
        val ftdi = listOf(
            Pin("BLK", "GND", Ground),
            Pin("GND", "GND", Ground),
            Pin("VCC", "VCC", Power, res(R.string.rf_ard_vcc)),
            Pin("RXI", "D0", Data, raw("UART RX")),
            Pin("TXO", "D1", Data, raw("UART TX")),
            Pin("GRN", "DTR", Control, res(R.string.rf_ard_dtr)),
        )
        val left = listOf(
            ATMEGA328.pin(1, "TXO"), ATMEGA328.pin(0, "RXI"),
            Pin("RST", "RESET", Control, res(R.string.rf_ard_reset)), gnd(),
        ) + (2..9).map { ATMEGA328.pin(it) }
        val right = listOf(
            Pin("RAW", "RAW", Power, res(R.string.rf_ard_raw)), gnd(),
            Pin("RST", "RESET", Control, res(R.string.rf_ard_reset)),
            Pin("VCC", "VCC", Power, res(R.string.rf_ard_vcc)),
        ) + (3 downTo 0).map(::uno328Analog) + (13 downTo 10).map { ATMEGA328.pin(it) }
        val inside = (4..7).map(::uno328Analog)
        ftdi.mapIndexed { i, pin -> pin.copy(x = i * 0.8f + 0.5f, y = 0f) } +
            left.mapIndexed { i, pin -> pin.copy(x = 0f, y = i + 1.5f) } +
            right.mapIndexed { i, pin -> pin.copy(x = 5f, y = i + 1.5f) } +
            inside.mapIndexed { i, pin -> pin.copy(x = 3.8f, y = i + 5.5f) }
    }

    override val sheets by lazy {
        val unoSized = res(R.string.rf_ard_uno_sized)
        val nanoSized = res(R.string.rf_ard_nano_sized)
        listOf(
            PinoutSheet(raw("Uno R3"), UNO_R3, note = res(R.string.rf_ard_uno_note), group = unoSized),
            PinoutSheet(raw("Uno R4 (Minima, WiFi)"), UNO_R4_PINS, note = res(R.string.rf_ard_r4_note), group = unoSized),
            PinoutSheet(raw("Leonardo"), LEONARDO_PINS, note = res(R.string.rf_ard_leonardo_note), group = unoSized),
            PinoutSheet(raw("Mega 2560"), MEGA_PINS, note = res(R.string.rf_ard_mega_note), group = raw("Mega")),
            PinoutSheet(
                raw("Nano"),
                nanoShape(ATMEGA328, ::uno328Analog, res(R.string.rf_ard_vin_nano)),
                note = res(R.string.rf_ard_nano_note),
                group = nanoSized
            ),
            PinoutSheet(
                raw("Nano Every"),
                nanoShape(NANO_EVERY, ::nanoAnalog, res(R.string.rf_ard_vin_every)),
                note = res(R.string.rf_ard_every_note),
                group = nanoSized
            ),
            PinoutSheet(
                raw("Nano 33 (IoT, BLE)"),
                nanoShape(NANO_33, ::nanoAnalog, res(R.string.rf_ard_supply), v5 = "+5V (VUSB)" to res(R.string.rf_ard_vusb)),
                note = res(R.string.rf_ard_nano33_note),
                group = nanoSized
            ),
            PinoutSheet(raw("Pro Mini"), PRO_MINI, note = res(R.string.rf_ard_pro_mini_note), group = raw("Pro")),
        )
    }
}
