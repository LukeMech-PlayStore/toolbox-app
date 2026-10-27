package org.lukemech.lmtoolbox.features.reference.esp_boards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
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
import org.lukemech.lmtoolbox.features.reference.common.spec
import org.lukemech.lmtoolbox.features.reference.common.SpecKind
import org.lukemech.lmtoolbox.features.reference.common.Txt
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/**
 * Espressif boards' pins, each with what else it does: the ESP32's DevKitC and DevKit V1, the ESP32-S3's DevKitC-1,
 * the ESP32-C3 SuperMini, the ESP32-CAM, and the ESP8266's NodeMCU and D1 mini.
 */
object EspBoardsFeature : ReferenceFeature() {

    override val route = "esp_boards"
    override val titleStringResId: Int = R.string.fp_esp_boards
    override val keywordsResId: Int = R.string.fk_esp_boards
    override val descriptionResId: Int = R.string.fd_esp_boards
    override val icon @Composable get() = Icons.Default.Wifi

    private val INPUT_ONLY = res(R.string.rf_esp_input_only)
    private val FLASH = res(R.string.rf_esp_flash)
    private val STRAP = res(R.string.rf_esp_strap)
    private val BOOT = res(R.string.rf_esp_boot)
    private val LED = res(R.string.rf_esp_led)

    /** [parts] as one description, ", " between them. */
    private fun and(vararg parts: Txt) = Txt.Join(parts.toList(), ", ")

    /** GPIO[n], labeled [label] on the board (its number, or a D number on the ESP8266's), doing [parts] besides. */
    private fun io(n: Int, kind: PinKind = Signal, vararg parts: Txt, label: String = n.toString()) =
        Pin(label, "GPIO$n", kind, if (parts.isEmpty()) null else and(*parts))

    private fun gnd() = Pin("GND", "GND", Ground)
    private fun v33() = Pin("3V3", "3V3", Power, res(R.string.rf_esp_3v3))
    private fun v5(label: String = "5V") = Pin(label, label, Power, res(R.string.rf_esp_5v))
    private fun en(label: String = "EN") = Pin(label, label, Control, res(R.string.rf_esp_en))

    /** Two rows of pins down a board's sides, seen from above: [left] top to bottom, [right] top to bottom. */
    private fun sides(left: List<Pin>, right: List<Pin>, apart: Float = 3f) =
        left.mapIndexed { i, pin -> pin.copy(x = 0f, y = i.toFloat()) } + right.mapIndexed { i, pin -> pin.copy(x = apart, y = i.toFloat()) }

    // The ESP32's (WROOM/WROVER) pins, by GPIO number: the same on every board with one
    private fun esp32(n: Int, label: String = n.toString()): Pin = when (n) {
        0 -> io(n, Control, raw("ADC2_CH1, Touch1"), BOOT, label = label)
        1 -> io(n, Data, raw("UART0 TX"), label = label)
        2 -> io(n, Signal, raw("ADC2_CH2, Touch2"), STRAP, LED, label = label)
        3 -> io(n, Data, raw("UART0 RX"), label = label)
        4 -> io(n, Signal, raw("ADC2_CH0, Touch0"), label = label)
        5 -> io(n, Data, raw("VSPI CS"), STRAP, label = label)
        in 6..11 -> io(n, Unused, FLASH, label = label)
        12 -> io(n, Signal, raw("ADC2_CH5, Touch5, HSPI MISO"), res(R.string.rf_esp_strap_12), label = label)
        13 -> io(n, Signal, raw("ADC2_CH4, Touch4, HSPI MOSI"), label = label)
        14 -> io(n, Signal, raw("ADC2_CH6, Touch6, HSPI CLK"), label = label)
        15 -> io(n, Signal, raw("ADC2_CH3, Touch3, HSPI CS"), STRAP, label = label)
        16 -> io(n, Data, raw("UART2 RX"), label = label)
        17 -> io(n, Data, raw("UART2 TX"), label = label)
        18 -> io(n, Data, raw("VSPI CLK"), label = label)
        19 -> io(n, Data, raw("VSPI MISO"), label = label)
        21 -> io(n, Data, raw("I²C SDA"), label = label)
        22 -> io(n, Data, raw("I²C SCL"), label = label)
        23 -> io(n, Data, raw("VSPI MOSI"), label = label)
        25 -> io(n, Analog, raw("ADC2_CH8, DAC1"), label = label)
        26 -> io(n, Analog, raw("ADC2_CH9, DAC2"), label = label)
        27 -> io(n, Signal, raw("ADC2_CH7, Touch7"), label = label)
        32 -> io(n, Analog, raw("ADC1_CH4, Touch9"), label = label)
        33 -> io(n, Analog, raw("ADC1_CH5, Touch8"), label = label)
        34 -> io(n, Analog, raw("ADC1_CH6"), INPUT_ONLY, label = label)
        35 -> io(n, Analog, raw("ADC1_CH7"), INPUT_ONLY, label = label)
        36 -> io(n, Analog, raw("ADC1_CH0, SENSOR_VP"), INPUT_ONLY, label = label)
        39 -> io(n, Analog, raw("ADC1_CH3, SENSOR_VN"), INPUT_ONLY, label = label)
        else -> io(n, label = label)
    }

    /** Espressif's ESP32-DevKitC (V4), 38 pins, seen from above, the antenna at the top. */
    private val DEVKITC: List<Pin> by lazy {
        sides(
            listOf(v33(), en()) + listOf(36 to "VP", 39 to "VN", 34 to "34", 35 to "35", 32 to "32", 33 to "33", 25 to "25",
                26 to "26", 27 to "27", 14 to "14", 12 to "12").map { (n, l) -> esp32(n, l) } +
                listOf(gnd(), esp32(13), esp32(9, "SD2"), esp32(10, "SD3"), esp32(11, "CMD"), v5()),
            listOf(gnd(), esp32(23), esp32(22), esp32(1, "TX"), esp32(3, "RX"), esp32(21), gnd(), esp32(19), esp32(18), esp32(5),
                esp32(17), esp32(16), esp32(4), esp32(0), esp32(2), esp32(15), esp32(8, "SD1"), esp32(7, "SD0"), esp32(6, "CLK")),
            apart = 4f
        )
    }

    /** DOIT's ESP32 DevKit V1 (and its clones), 30 pins, seen from above, the antenna at the top. */
    private val DEVKIT_V1: List<Pin> by lazy {
        sides(
            listOf(en()) + listOf(36 to "VP", 39 to "VN", 34 to "D34", 35 to "D35", 32 to "D32", 33 to "D33", 25 to "D25",
                26 to "D26", 27 to "D27", 14 to "D14", 12 to "D12", 13 to "D13").map { (n, l) -> esp32(n, l) } +
                listOf(gnd(), Pin("VIN", "VIN", Power, res(R.string.rf_esp_vin))),
            listOf(23 to "D23", 22 to "D22", 1 to "TX0", 3 to "RX0", 21 to "D21", 19 to "D19", 18 to "D18", 5 to "D5",
                17 to "TX2", 16 to "RX2", 4 to "D4", 2 to "D2", 15 to "D15").map { (n, l) -> esp32(n, l) } + listOf(gnd(), v33()),
            apart = 4f
        )
    }

    // The ESP32-S3's: ADC1 on GPIO1–10, ADC2 on 11–20, touch on 1–14
    private fun s3(n: Int, label: String = n.toString()): Pin {
        val adc = when (n) {
            in 1..10 -> "ADC1_CH${n - 1}"
            in 11..20 -> "ADC2_CH${n - 11}"
            else -> null
        }
        val touch = if (n in 1..14) "Touch$n" else null
        val bus = when (n) {
            8 -> "I²C SDA"
            9 -> "I²C SCL"
            10 -> "FSPI CS"
            11 -> "FSPI MOSI"
            12 -> "FSPI CLK"
            13 -> "FSPI MISO"
            39 -> "JTAG MTCK"
            40 -> "JTAG MTDO"
            41 -> "JTAG MTDI"
            42 -> "JTAG MTMS"
            43 -> "UART0 TX"
            44 -> "UART0 RX"
            19 -> "USB D−"
            20 -> "USB D+"
            else -> null
        }
        val extra = when (n) {
            0 -> BOOT
            3, 45, 46 -> STRAP
            in 35..37 -> res(R.string.rf_esp_psram)
            48 -> res(R.string.rf_esp_rgb)
            else -> null
        }
        val named = listOfNotNull(adc, touch, bus).joinToString(", ").ifEmpty { null }?.let(::raw)
        val kind = when {
            n == 0 -> Control
            n in 43..44 || n in 19..20 -> Data
            adc != null -> Analog
            else -> Signal
        }
        return io(n, kind, *listOfNotNull(named, extra).toTypedArray(), label = label)
    }

    /** Espressif's ESP32-S3-DevKitC-1, 44 pins, seen from above, the antenna at the top. */
    private val S3_DEVKITC: List<Pin> by lazy {
        sides(
            listOf(v33(), v33(), en("RST")) + listOf(4, 5, 6, 7, 15, 16, 17, 18, 8, 3, 46, 9, 10, 11, 12, 13, 14).map { s3(it) } + listOf(v5(), gnd()),
            listOf(gnd(), s3(43, "TX"), s3(44, "RX")) + listOf(1, 2, 42, 41, 40, 39, 38, 37, 36, 35, 0, 45, 48, 47, 21, 20, 19).map { s3(it) } +
                listOf(gnd(), gnd()),
            apart = 4f
        )
    }

    // The ESP32-C3's
    private fun c3(n: Int): Pin = when (n) {
        in 0..4 -> io(n, Analog, raw("ADC1_CH$n" + if (n == 4) ", SPI SCK" else ""), *(if (n == 2) arrayOf(STRAP) else emptyArray()))
        5 -> io(n, Analog, raw("ADC2_CH0, SPI MISO"))
        6 -> io(n, Data, raw("SPI MOSI"))
        7 -> io(n, Data, raw("SPI SS"))
        8 -> io(n, Data, raw("I²C SDA"), STRAP, res(R.string.rf_esp_led_low))
        9 -> io(n, Control, raw("I²C SCL"), BOOT)
        20 -> io(n, Data, raw("UART0 RX"))
        21 -> io(n, Data, raw("UART0 TX"))
        else -> io(n)
    }

    /** The ESP32-C3 SuperMini, 16 pins, seen from above, USB at the top. */
    private val C3_SUPERMINI: List<Pin> by lazy {
        sides(
            listOf(v5(), gnd(), v33()) + listOf(4, 3, 2, 1, 0).map(::c3),
            listOf(5, 6, 7, 8, 9, 10, 20, 21).map(::c3)
        )
    }

    /** AI-Thinker's ESP32-CAM, 16 pins: the microSD card's and the camera's take most of the ESP32's. */
    private val CAM: List<Pin> by lazy {
        sides(
            listOf(
                v5(),
                gnd(),
                io(12, Signal, raw("SD DATA2"), res(R.string.rf_esp_strap_12), label = "IO12"),
                io(13, Signal, raw("SD DATA3"), label = "IO13"),
                io(15, Signal, raw("SD CMD"), STRAP, label = "IO15"),
                io(14, Signal, raw("SD CLK"), label = "IO14"),
                io(2, Signal, raw("SD DATA0"), STRAP, label = "IO2"),
                io(4, Signal, raw("SD DATA1"), res(R.string.rf_esp_cam_flash), label = "IO4"),
            ),
            listOf(
                v33(),
                io(16, Unused, res(R.string.rf_esp_psram), label = "IO16"),
                io(0, Control, res(R.string.rf_esp_cam_xclk), BOOT, label = "IO0"),
                gnd(),
                Pin("VCC", "VCC", Power, res(R.string.rf_esp_cam_vcc)),
                io(3, Data, raw("UART0 RX"), label = "U0R"),
                io(1, Data, raw("UART0 TX"), label = "U0T"),
                gnd(),
            )
        )
    }

    // The ESP8266's, by its D number on NodeMCU and D1 mini boards
    private fun d(label: String): Pin = when (label) {
        "D0" -> io(16, Signal, res(R.string.rf_esp8266_wake), label = label)
        "D1" -> io(5, Data, raw("I²C SCL"), label = label)
        "D2" -> io(4, Data, raw("I²C SDA"), label = label)
        "D3" -> io(0, Control, res(R.string.rf_esp8266_flash), label = label)
        "D4" -> io(2, Signal, raw("UART1 TX"), res(R.string.rf_esp8266_high), LED, label = label)
        "D5" -> io(14, Data, raw("SPI SCK"), label = label)
        "D6" -> io(12, Data, raw("SPI MISO"), label = label)
        "D7" -> io(13, Data, raw("SPI MOSI"), label = label)
        "D8" -> io(15, Data, raw("SPI CS"), res(R.string.rf_esp8266_low), label = label)
        "RX" -> io(3, Data, raw("UART0 RX"), label = label)
        "TX" -> io(1, Data, raw("UART0 TX"), label = label)
        else -> error(label)
    }

    private fun a0() = Pin("A0", "A0", Analog, res(R.string.rf_esp8266_a0))

    /** The NodeMCU (ESP-12), 30 pins, seen from above, the antenna at the top. */
    private val NODEMCU: List<Pin> by lazy {
        sides(
            listOf(
                a0(),
                Pin("RSV", "RSV", Unused, res(R.string.rf_esp_rsv)),
                Pin("RSV", "RSV", Unused, res(R.string.rf_esp_rsv)),
                io(10, Unused, FLASH, label = "SD3"),
                io(9, Unused, FLASH, label = "SD2"),
                Pin("SD1", "SD1", Unused, FLASH),
                Pin("CMD", "CMD", Unused, FLASH),
                Pin("SD0", "SD0", Unused, FLASH),
                Pin("CLK", "CLK", Unused, FLASH),
                gnd(),
                v33(),
                en(),
                Pin("RST", "RST", Control, res(R.string.rf_esp_rst)),
                gnd(),
                Pin("VIN", "VIN", Power, res(R.string.rf_esp_vin)),
            ),
            listOf("D0", "D1", "D2", "D3", "D4").map(::d) + listOf(v33(), gnd()) + listOf("D5", "D6", "D7", "D8", "RX", "TX").map(::d) +
                listOf(gnd(), v33()),
            apart = 4f
        )
    }

    /** Wemos' (LOLIN's) D1 mini, 16 pins, seen from above, the antenna at the top. */
    private val D1_MINI: List<Pin> by lazy {
        sides(
            listOf(Pin("RST", "RST", Control, res(R.string.rf_esp_rst)), a0()) + listOf("D0", "D5", "D6", "D7", "D8").map(::d) + listOf(v33()),
            listOf("TX", "RX", "D1", "D2", "D3", "D4").map(::d) + listOf(gnd(), v5())
        )
    }

    /** Waveshare's ESP32-C3-Zero, 18 pins, seen from above, USB-C at the top, 5V's side on the left. */
    private val C3_ZERO: List<Pin> by lazy {
        fun adc(n: Int) = io(n, Analog, raw("ADC1_CH$n"), *(if (n == 2) arrayOf(STRAP) else emptyArray()), label = n.toString())
        sides(
            listOf(v5(), gnd(), v33()) + (0..4).map(::adc) + io(5, Signal, raw("ADC2_CH0"), label = "5"),
            listOf(
                io(21, Data, raw("UART0 TX"), label = "21"),
                io(20, Data, raw("UART0 RX"), label = "20"),
                io(19, Data, raw("USB D+"), label = "19"),
                io(18, Data, raw("USB D−"), label = "18"),
                io(10, Signal, res(R.string.rf_esp_rgb_10), label = "10"),
                io(9, Control, BOOT, label = "9"),
                io(8, Signal, STRAP, label = "8"),
                io(7, Signal, label = "7"),
                io(6, Signal, label = "6"),
            )
        )
    }

    /** The ESP-01 (ESP8266, 8 pins) as most of its diagrams draw it: two rows of four, from the chip's side. */
    private val ESP01: List<Pin> by lazy {
        listOf(
            gnd(),
            io(2, Signal, raw("UART1 TX"), res(R.string.rf_esp8266_high), label = "IO2"),
            io(0, Control, res(R.string.rf_esp8266_flash), label = "IO0"),
            io(3, Data, raw("UART0 RX"), label = "RX"),
            io(1, Data, raw("UART0 TX"), label = "TX"),
            en("EN"),
            Pin("RST", "RST", Control, res(R.string.rf_esp_rst)),
            Pin("VCC", "VCC", Power, res(R.string.rf_esp_vcc_33)),
        ).mapIndexed { i, pin -> pin.copy(x = (i % 4).toFloat(), y = (i / 4).toFloat()) }
    }

    /** The ESP-12F module (ESP8266) seen from above, its antenna at the top: down both sides, the flash's along the bottom. */
    private val ESP12F: List<Pin> by lazy {
        val left = listOf(
            Pin("RST", "RST", Control, res(R.string.rf_esp_rst)),
            Pin("ADC", "ADC (TOUT)", Analog, res(R.string.rf_esp8266_adc)),
            en(),
            io(16, Signal, res(R.string.rf_esp8266_wake), label = "16"),
            io(14, Data, raw("SPI SCK"), label = "14"),
            io(12, Data, raw("SPI MISO"), label = "12"),
            io(13, Data, raw("SPI MOSI"), label = "13"),
            Pin("VCC", "VCC", Power, res(R.string.rf_esp_vcc_33)),
        )
        val right = listOf(
            io(1, Data, raw("UART0 TX"), label = "TX"),
            io(3, Data, raw("UART0 RX"), label = "RX"),
            io(5, Data, raw("I²C SCL"), label = "5"),
            io(4, Data, raw("I²C SDA"), label = "4"),
            io(0, Control, res(R.string.rf_esp8266_flash), label = "0"),
            io(2, Signal, raw("UART1 TX"), res(R.string.rf_esp8266_high), LED, label = "2"),
            io(15, Data, raw("SPI CS"), res(R.string.rf_esp8266_low), label = "15"),
            gnd(),
        )
        val bottom = listOf("CS0", "MISO", "9", "10", "MOSI", "SCLK").map { Pin(it, it, Unused, FLASH) }
        sides(left, right, apart = 7f) + bottom.mapIndexed { i, pin -> pin.copy(x = i + 0.5f, y = 8f) }
    }

    // What every ESP32's (WROOM) board has
    private fun esp32Specs(flash: String = "4 MB", ram: String = "520 KB") = listOf(
        spec(SpecKind.Chip, "ESP32"), spec(SpecKind.Clock, "2 × 240 MHz"), spec(SpecKind.Flash, flash),
        spec(SpecKind.Ram, ram), spec(SpecKind.Logic, "3.3 V"), spec(SpecKind.Supply, "5 V (USB)"),
        spec(SpecKind.Wireless, "Wi-Fi, BT 4.2"),
    )

    private val C3_SPECS = listOf(
        spec(SpecKind.Chip, "ESP32-C3"), spec(SpecKind.Clock, "160 MHz"), spec(SpecKind.Flash, "4 MB"),
        spec(SpecKind.Ram, "400 KB"), spec(SpecKind.Logic, "3.3 V"), spec(SpecKind.Supply, "5 V (USB-C)"),
        spec(SpecKind.Wireless, "Wi-Fi, BLE 5"),
    )

    private fun esp8266Specs(flash: String, supply: String) = listOf(
        spec(SpecKind.Chip, "ESP8266"), spec(SpecKind.Clock, "80 / 160 MHz"), spec(SpecKind.Flash, flash),
        spec(SpecKind.Ram, "80 KB"), spec(SpecKind.Logic, "3.3 V"), spec(SpecKind.Supply, supply),
        spec(SpecKind.Wireless, "Wi-Fi"),
    )

    override val sheets by lazy {
        val esp32 = raw("ESP32")
        val c3 = raw("ESP32-C3")
        val esp8266 = raw("ESP8266")
        listOf(
            PinoutSheet(raw("ESP32-DevKitC"), DEVKITC, note = res(R.string.rf_esp_devkitc_note), group = esp32, specs = esp32Specs()),
            PinoutSheet(raw("ESP32 DevKit V1 (DOIT)"), DEVKIT_V1, note = res(R.string.rf_esp_devkit_v1_note), group = esp32, specs = esp32Specs()),
            PinoutSheet(
                raw("ESP32-CAM"), CAM, note = res(R.string.rf_esp_cam_note), group = esp32,
                specs = esp32Specs(ram = "520 KB + 4 MB")
            ),
            PinoutSheet(
                raw("ESP32-S3-DevKitC-1"), S3_DEVKITC, note = res(R.string.rf_esp_s3_note), group = raw("ESP32-S3"),
                specs = listOf(
                    spec(SpecKind.Chip, "ESP32-S3"), spec(SpecKind.Clock, "2 × 240 MHz"), spec(SpecKind.Flash, "8 / 16 MB"),
                    spec(SpecKind.Ram, "512 KB + PSRAM"), spec(SpecKind.Logic, "3.3 V"), spec(SpecKind.Supply, "5 V (USB)"),
                    spec(SpecKind.Wireless, "Wi-Fi, BLE 5"),
                )
            ),
            PinoutSheet(raw("ESP32-C3 SuperMini"), C3_SUPERMINI, note = res(R.string.rf_esp_c3_note), group = c3, specs = C3_SPECS),
            PinoutSheet(raw("ESP32-C3-Zero"), C3_ZERO, note = res(R.string.rf_esp_c3_zero_note), group = c3, specs = C3_SPECS),
            PinoutSheet(raw("NodeMCU"), NODEMCU, note = res(R.string.rf_esp_nodemcu_note), group = esp8266, specs = esp8266Specs("4 MB", "5 V (USB, VIN)")),
            PinoutSheet(raw("D1 mini"), D1_MINI, note = res(R.string.rf_esp_d1_note), group = esp8266, specs = esp8266Specs("4 MB", "5 V (USB)")),
            PinoutSheet(raw("ESP-01"), ESP01, note = res(R.string.rf_esp01_note), group = esp8266, specs = esp8266Specs("1 MB", "3.0–3.6 V")),
            PinoutSheet(raw("ESP-12F"), ESP12F, note = res(R.string.rf_esp12f_note), group = esp8266, specs = esp8266Specs("4 MB", "3.0–3.6 V")),
        )
    }
}
