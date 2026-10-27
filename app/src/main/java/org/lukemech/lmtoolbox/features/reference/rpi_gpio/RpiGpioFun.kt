package org.lukemech.lmtoolbox.features.reference.rpi_gpio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Analog
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Control
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Data
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Ground
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Power
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Signal
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.dualRow
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/**
 * The Raspberry Pi's GPIO headers, with each pin's other functions: the 40-pin one (the B+ and every model since),
 * the first models' 26-pin one (both revisions), and the Pico's pins.
 */
object RpiGpioFeature : ReferenceFeature() {

    override val route = "rpi_gpio"
    override val titleStringResId: Int = R.string.fp_rpi_gpio
    override val keywordsResId: Int = R.string.fk_rpi_gpio
    override val descriptionResId: Int = R.string.fd_rpi_gpio
    override val icon @Composable get() = Icons.Default.DeveloperBoard

    /** The 40-pin header, in its two rows: odd pins on the left, even on the right. */
    private val FORTY: List<Pin> by lazy {
        listOf(
            Pin("1", "3V3", Power),
            Pin("2", "5V", Power),
            Pin("3", "GPIO2", Data, raw("I²C1 SDA")),
            Pin("4", "5V", Power),
            Pin("5", "GPIO3", Data, raw("I²C1 SCL")),
            Pin("6", "GND", Ground),
            Pin("7", "GPIO4", Signal, raw("GPCLK0")),
            Pin("8", "GPIO14", Data, raw("UART TXD")),
            Pin("9", "GND", Ground),
            Pin("10", "GPIO15", Data, raw("UART RXD")),
            Pin("11", "GPIO17", Signal, raw("SPI1 CE1")),
            Pin("12", "GPIO18", Signal, raw("PCM CLK, PWM0, SPI1 CE0")),
            Pin("13", "GPIO27", Signal),
            Pin("14", "GND", Ground),
            Pin("15", "GPIO22", Signal),
            Pin("16", "GPIO23", Signal),
            Pin("17", "3V3", Power),
            Pin("18", "GPIO24", Signal),
            Pin("19", "GPIO10", Data, raw("SPI0 MOSI")),
            Pin("20", "GND", Ground),
            Pin("21", "GPIO9", Data, raw("SPI0 MISO")),
            Pin("22", "GPIO25", Signal),
            Pin("23", "GPIO11", Data, raw("SPI0 SCLK")),
            Pin("24", "GPIO8", Data, raw("SPI0 CE0")),
            Pin("25", "GND", Ground),
            Pin("26", "GPIO7", Data, raw("SPI0 CE1")),
            Pin("27", "GPIO0", Data, res(R.string.rf_rpi_id_sd)),
            Pin("28", "GPIO1", Data, res(R.string.rf_rpi_id_sc)),
            Pin("29", "GPIO5", Signal, raw("GPCLK1")),
            Pin("30", "GND", Ground),
            Pin("31", "GPIO6", Signal, raw("GPCLK2")),
            Pin("32", "GPIO12", Signal, raw("PWM0")),
            Pin("33", "GPIO13", Signal, raw("PWM1")),
            Pin("34", "GND", Ground),
            Pin("35", "GPIO19", Signal, raw("PCM FS, PWM1, SPI1 MISO")),
            Pin("36", "GPIO16", Signal, raw("SPI1 CE2")),
            Pin("37", "GPIO26", Signal),
            Pin("38", "GPIO20", Signal, raw("PCM DIN, SPI1 MOSI")),
            Pin("39", "GND", Ground),
            Pin("40", "GPIO21", Signal, raw("PCM DOUT, SPI1 SCLK")),
        ).dualRow(vertical = true)
    }

    /** The first Model B's (rev. 1): GPIO0, GPIO1 and GPIO21 where rev. 2 has GPIO2, GPIO3 and GPIO27. */
    private val REV1: List<Pin> by lazy {
        FORTY.take(26).map {
            when (it.number) {
                "3" -> it.copy(name = "GPIO0", description = raw("I²C0 SDA"))
                "5" -> it.copy(name = "GPIO1", description = raw("I²C0 SCL"))
                "13" -> it.copy(name = "GPIO21")
                else -> it
            }
        }
    }

    /** A Pico's GPn: what else it can be (a UART's, I²C's, SPI's, the ADC's), as its pinout prints them. */
    private fun gp(number: Int, n: Int): Pin {
        val uart = when (n) {
            0, 12, 16 -> "UART0 TX"
            1, 13, 17 -> "UART0 RX"
            4, 8 -> "UART1 TX"
            5, 9 -> "UART1 RX"
            else -> null
        }
        val i2c = if (n <= 21 || n in 26..27) "I²C${(n / 2) % 2} ${if (n % 2 == 0) "SDA" else "SCL"}" else null
        val spi = if (n <= 19) "SPI${if (n < 8 || n >= 16) 0 else 1} ${listOf("RX", "CSn", "SCK", "TX")[n % 4]}" else null
        val adc = if (n in 26..28) "ADC${n - 26}" else null
        val functions = listOfNotNull(adc, uart, i2c, spi)
        return Pin(number.toString(), "GP$n", if (adc != null) Analog else Signal, functions.takeIf { it.isNotEmpty() }?.let { raw(it.joinToString(", ")) })
    }

    /** The Pico seen from above, USB at the top: pins 1–20 down the left side, 21–40 up the right. */
    private val PICO: List<Pin> by lazy {
        fun gnd(n: Int) = Pin(n.toString(), "GND", Ground)
        val left = listOf(
            gp(1, 0), gp(2, 1), gnd(3), gp(4, 2), gp(5, 3), gp(6, 4), gp(7, 5), gnd(8), gp(9, 6), gp(10, 7),
            gp(11, 8), gp(12, 9), gnd(13), gp(14, 10), gp(15, 11), gp(16, 12), gp(17, 13), gnd(18), gp(19, 14), gp(20, 15),
        )
        val right = listOf(
            gp(21, 16), gp(22, 17), gnd(23), gp(24, 18), gp(25, 19), gp(26, 20), gp(27, 21), gnd(28), gp(29, 22),
            Pin("30", "RUN", Control, res(R.string.rf_pico_run)),
            gp(31, 26), gp(32, 27),
            Pin("33", "AGND", Ground, res(R.string.rf_pico_agnd)),
            gp(34, 28),
            Pin("35", "ADC_VREF", Analog, res(R.string.rf_pico_vref)),
            Pin("36", "3V3(OUT)", Power, res(R.string.rf_pico_3v3)),
            Pin("37", "3V3_EN", Control, res(R.string.rf_pico_3v3_en)),
            gnd(38),
            Pin("39", "VSYS", Power, res(R.string.rf_pico_vsys)),
            Pin("40", "VBUS", Power, res(R.string.rf_pico_vbus)),
        )
        left.mapIndexed { i, pin -> pin.copy(x = 0f, y = i.toFloat()) } + right.mapIndexed { i, pin -> pin.copy(x = 3f, y = 19f - i) }
    }

    override val sheets by lazy {
        val pi = raw("Raspberry Pi")
        listOf(
            PinoutSheet(raw("40-pin (B+, 2–5, Zero)"), FORTY, note = res(R.string.rf_rpi_note), group = pi),
            PinoutSheet(raw("26-pin (A, B rev. 2)"), FORTY.take(26), note = res(R.string.rf_rpi_26_note), group = pi),
            PinoutSheet(raw("26-pin (B rev. 1)"), REV1, note = res(R.string.rf_rpi_rev1_note), group = pi),
            PinoutSheet(raw("Pico, Pico W, Pico 2"), PICO, note = res(R.string.rf_pico_note), group = raw("Pico")),
        )
    }
}
