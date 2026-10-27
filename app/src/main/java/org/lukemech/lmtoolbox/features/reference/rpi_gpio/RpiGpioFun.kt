package org.lukemech.lmtoolbox.features.reference.rpi_gpio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Data
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Ground
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Power
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Signal
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.dualRow
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** The Raspberry Pi's 40-pin GPIO header (the B+ and every model since), with each pin's other functions. */
object RpiGpioFeature : ReferenceFeature() {

    override val route = "rpi_gpio"
    override val titleStringResId: Int = R.string.fp_rpi_gpio
    override val keywordsResId: Int = R.string.fk_rpi_gpio
    override val descriptionResId: Int = R.string.fd_rpi_gpio
    override val icon @Composable get() = Icons.Default.DeveloperBoard

    override val sheets by lazy {
        listOf(
            PinoutSheet(
                raw("40-pin"),
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
                ).dualRow(vertical = true),
                note = res(R.string.rf_rpi_note)
            )
        )
    }
}
