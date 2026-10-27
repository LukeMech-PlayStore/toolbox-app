package org.lukemech.lmtoolbox.features.reference.arduino

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Analog
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Control
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Data
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Ground
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Power
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Signal
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Unused
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.Spec
import org.lukemech.lmtoolbox.features.reference.common.SpecKind
import org.lukemech.lmtoolbox.features.reference.common.spec
import org.lukemech.lmtoolbox.features.reference.common.Txt
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/**
 * Arduino boards' headers, each pin with what else it does: the Uno-sized ones (Uno R3 and R4, Leonardo), the Mega
 * 2560, the Nano-sized ones (Nano, Nano Every, Nano 33, Nano ESP32), the Pro Mini and Pro Micro; each one's chip,
 * memory and voltages.
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
        return aroundNano((right + left).sortedBy { it.number.toInt() })
    }

    /** A Nano-sized board's [pins] (1 to 30, in order) at their places: 1–15 up the right side, 16–30 down the left. */
    private fun aroundNano(pins: List<Pin>) = pins.mapIndexed { i, pin ->
        if (i < 15) pin.copy(x = 1f, y = 14f - i) else pin.copy(x = 0f, y = i - 15f)
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

    /** The Nano ESP32 (ESP32-S3): the Nano's pins and their numbers, each one's GPIO besides its Arduino name. */
    private val NANO_ESP32: List<Pin> by lazy {
        fun d(label: String, name: String, kind: PinKind, vararg also: String) = Pin(label, name, kind, raw(also.joinToString(", ")))
        listOf(
            d("1", "TX0", Data, "GPIO43", "UART0 TX"),
            d("2", "RX0", Data, "GPIO44", "UART0 RX"),
            Pin("3", "RESET", Control, res(R.string.rf_ard_reset)),
            Pin("4", "GND", Ground),
            d("5", "D2", Signal, "GPIO5", "ADC1_CH4"),
            d("6", "D3", Signal, "GPIO6", "ADC1_CH5"),
            d("7", "D4", Signal, "GPIO7", "ADC1_CH6"),
            d("8", "D5", Signal, "GPIO8", "ADC1_CH7"),
            d("9", "D6", Signal, "GPIO9", "ADC1_CH8"),
            d("10", "D7", Signal, "GPIO10", "ADC1_CH9"),
            d("11", "D8", Signal, "GPIO17", "ADC2_CH6", "UART1 TX"),
            d("12", "D9", Signal, "GPIO18", "ADC2_CH7", "UART1 RX"),
            d("13", "D10", Data, "GPIO21", "SPI SS"),
            d("14", "D11", Data, "GPIO38", "SPI MOSI"),
            d("15", "D12", Data, "GPIO47", "SPI MISO"),
            Pin("16", "D13", Data, Txt.Join(listOf(raw("GPIO48, SPI SCK"), res(R.string.rf_ard_led)), ", ")),
            Pin("17", "3V3", Power),
            d("18", "B0", Signal, "GPIO46", "RGB LED (R)"),
            d("19", "A0", Analog, "GPIO1", "ADC1_CH0"),
            d("20", "A1", Analog, "GPIO2", "ADC1_CH1"),
            d("21", "A2", Analog, "GPIO3", "ADC1_CH2"),
            d("22", "A3", Analog, "GPIO4", "ADC1_CH3"),
            d("23", "A4", Analog, "GPIO11", "ADC2_CH0", "I²C SDA"),
            d("24", "A5", Analog, "GPIO12", "ADC2_CH1", "I²C SCL"),
            d("25", "A6", Analog, "GPIO13", "ADC2_CH2"),
            d("26", "A7", Analog, "GPIO14", "ADC2_CH3"),
            Pin("27", "VBUS", Power, res(R.string.rf_ard_vbus)),
            Pin("28", "B1", Control, Txt.Join(listOf(raw("GPIO0, RGB LED (G)"), res(R.string.rf_ard_boot)), ", ")),
            Pin("29", "GND", Ground),
            Pin("30", "VIN", Power, res(R.string.rf_ard_vin_every)),
        ).let(::aroundNano)
    }

    /** The Pro Micro (ATmega32U4) seen from above, USB at the top: TXO's side on the left, RAW's on the right. */
    private val PRO_MICRO: List<Pin> by lazy {
        val left = listOf(
            LEONARDO.pin(1, "TXO"), LEONARDO.pin(0, "RXI"), gnd(), gnd(),
        ) + (2..9).map { LEONARDO.pin(it) }
        val right = listOf(
            Pin("RAW", "RAW", Power, res(R.string.rf_ard_raw)), gnd(),
            Pin("RST", "RESET", Control, res(R.string.rf_ard_reset)),
            Pin("VCC", "VCC", Power, res(R.string.rf_ard_vcc)),
        ) + (3 downTo 0).map { analog(it) } + listOf(
            Pin("15", "D15", Data, raw("SPI SCK")),
            Pin("14", "D14", Data, raw("SPI MISO")),
            Pin("16", "D16", Data, raw("SPI MOSI")),
            Pin("10", "D10", Signal, and(PWM, raw("A10"))),
        )
        left.mapIndexed { i, pin -> pin.copy(x = 0f, y = i.toFloat()) } + right.mapIndexed { i, pin -> pin.copy(x = 5f, y = i.toFloat()) }
    }

    // Each board's chip, memory and voltages, as tiles over its pins
    private val SPECS_328 = listOf(
        spec(SpecKind.Chip, "ATmega328P"), spec(SpecKind.Clock, "16 MHz"), spec(SpecKind.Flash, "32 KB"),
        spec(SpecKind.Ram, "2 KB"), spec(SpecKind.Logic, "5 V"),
    )

    override val sheets by lazy {
        val unoSized = res(R.string.rf_ard_uno_sized)
        val nanoSized = res(R.string.rf_ard_nano_sized)
        val pro = raw("Pro")
        listOf(
            PinoutSheet(
                raw("Uno R3"), UNO_R3, note = res(R.string.rf_ard_uno_note), group = unoSized,
                specs = SPECS_328 + spec(SpecKind.Supply, "7–12 V")
            ),
            PinoutSheet(
                raw("Uno R4 (Minima, WiFi)"), UNO_R4_PINS, note = res(R.string.rf_ard_r4_note), group = unoSized,
                specs = listOf(
                    spec(SpecKind.Chip, "RA4M1"), spec(SpecKind.Clock, "48 MHz"), spec(SpecKind.Flash, "256 KB"),
                    spec(SpecKind.Ram, "32 KB"), spec(SpecKind.Logic, "5 V"), spec(SpecKind.Supply, "6–24 V"),
                    Spec(SpecKind.Wireless, res(R.string.rf_ard_r4_wireless)),
                )
            ),
            PinoutSheet(
                raw("Leonardo"), LEONARDO_PINS, note = res(R.string.rf_ard_leonardo_note), group = unoSized,
                specs = listOf(
                    spec(SpecKind.Chip, "ATmega32U4"), spec(SpecKind.Clock, "16 MHz"), spec(SpecKind.Flash, "32 KB"),
                    spec(SpecKind.Ram, "2.5 KB"), spec(SpecKind.Logic, "5 V"), spec(SpecKind.Supply, "7–12 V"),
                )
            ),
            PinoutSheet(
                raw("Mega 2560"), MEGA_PINS, note = res(R.string.rf_ard_mega_note), group = raw("Mega"),
                specs = listOf(
                    spec(SpecKind.Chip, "ATmega2560"), spec(SpecKind.Clock, "16 MHz"), spec(SpecKind.Flash, "256 KB"),
                    spec(SpecKind.Ram, "8 KB"), spec(SpecKind.Logic, "5 V"), spec(SpecKind.Supply, "7–12 V"),
                )
            ),
            PinoutSheet(
                raw("Nano"),
                nanoShape(ATMEGA328, ::uno328Analog, res(R.string.rf_ard_vin_nano)),
                note = res(R.string.rf_ard_nano_note),
                group = nanoSized,
                specs = SPECS_328 + spec(SpecKind.Supply, "7–12 V")
            ),
            PinoutSheet(
                raw("Nano Every"),
                nanoShape(NANO_EVERY, ::nanoAnalog, res(R.string.rf_ard_vin_every)),
                note = res(R.string.rf_ard_every_note),
                group = nanoSized,
                specs = listOf(
                    spec(SpecKind.Chip, "ATmega4809"), spec(SpecKind.Clock, "20 MHz"), spec(SpecKind.Flash, "48 KB"),
                    spec(SpecKind.Ram, "6 KB"), spec(SpecKind.Logic, "5 V"), spec(SpecKind.Supply, "7–21 V"),
                )
            ),
            PinoutSheet(
                raw("Nano 33 (IoT, BLE)"),
                nanoShape(NANO_33, ::nanoAnalog, res(R.string.rf_ard_supply), v5 = "+5V (VUSB)" to res(R.string.rf_ard_vusb)),
                note = res(R.string.rf_ard_nano33_note),
                group = nanoSized,
                specs = listOf(
                    spec(SpecKind.Chip, "SAMD21 / nRF52840"), spec(SpecKind.Clock, "48 / 64 MHz"), spec(SpecKind.Flash, "256 KB / 1 MB"),
                    spec(SpecKind.Ram, "32 / 256 KB"), spec(SpecKind.Logic, "3.3 V"), spec(SpecKind.Wireless, "Wi-Fi, BLE / BLE"),
                )
            ),
            PinoutSheet(
                raw("Nano ESP32"),
                NANO_ESP32,
                note = res(R.string.rf_ard_nano_esp32_note),
                group = nanoSized,
                specs = listOf(
                    spec(SpecKind.Chip, "ESP32-S3"), spec(SpecKind.Clock, "240 MHz"), spec(SpecKind.Flash, "16 MB"),
                    spec(SpecKind.Ram, "512 KB + 8 MB"), spec(SpecKind.Logic, "3.3 V"), spec(SpecKind.Supply, "6–21 V"),
                    spec(SpecKind.Wireless, "Wi-Fi, BLE"),
                )
            ),
            PinoutSheet(
                raw("Pro Mini"), PRO_MINI, note = res(R.string.rf_ard_pro_mini_note), group = pro,
                specs = listOf(
                    spec(SpecKind.Chip, "ATmega328P"), spec(SpecKind.Clock, "16 / 8 MHz"), spec(SpecKind.Flash, "32 KB"),
                    spec(SpecKind.Ram, "2 KB"), spec(SpecKind.Logic, "5 / 3.3 V"), spec(SpecKind.Supply, "RAW ≤ 12 V"),
                )
            ),
            PinoutSheet(
                raw("Pro Micro"), PRO_MICRO, note = res(R.string.rf_ard_pro_micro_note), group = pro,
                specs = listOf(
                    spec(SpecKind.Chip, "ATmega32U4"), spec(SpecKind.Clock, "16 / 8 MHz"), spec(SpecKind.Flash, "32 KB"),
                    spec(SpecKind.Ram, "2.5 KB"), spec(SpecKind.Logic, "5 / 3.3 V"), spec(SpecKind.Supply, "RAW ≤ 12 V"),
                )
            ),
        )
    }
}
