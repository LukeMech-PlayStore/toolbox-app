package org.lukemech.lmtoolbox.features.reference.stm32

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
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
import org.lukemech.lmtoolbox.features.reference.common.SpecKind
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res
import org.lukemech.lmtoolbox.features.reference.common.spec

/**
 * STM32 boards' pins, each with its peripherals: the Blue Pill (STM32F103) and the Black Pill (STM32F401/F411).
 * Their functions as the chips' datasheets list them, the default ones first (remap: an alternate place).
 */
object Stm32Feature : ReferenceFeature() {

    override val route = "stm32"
    override val titleStringResId: Int = R.string.fp_stm32
    override val keywordsResId: Int = R.string.fk_stm32
    override val descriptionResId: Int = R.string.fd_stm32
    override val icon @Composable get() = Icons.Default.Memory

    /**
     * A pill seen from above: its two long rows ([top] left to right, [bottom] under it), and its SWD header ([swd])
     * down its right end.
     */
    private fun pill(top: List<Pin>, bottom: List<Pin>, swd: List<Pin>) =
        top.mapIndexed { i, pin -> pin.copy(x = i.toFloat(), y = 0f) } +
            bottom.mapIndexed { i, pin -> pin.copy(x = i.toFloat(), y = 4f) } +
            swd.mapIndexed { i, pin -> pin.copy(x = top.size + 0.6f, y = i + 0.5f) }

    private val BLUE_PILL: List<Pin> by lazy {
        pill(
            listOf(
                Pin("B12", "PB12", Data, raw("SPI2_NSS, I2C2_SMBA, USART3_CK, TIM1_BKIN")),
                Pin("B13", "PB13", Data, raw("SPI2_SCK, USART3_CTS, TIM1_CH1N")),
                Pin("B14", "PB14", Data, raw("SPI2_MISO, USART3_RTS, TIM1_CH2N")),
                Pin("B15", "PB15", Data, raw("SPI2_MOSI, TIM1_CH3N")),
                Pin("A8", "PA8", Data, raw("TIM1_CH1, USART1_CK, MCO")),
                Pin("A9", "PA9", Data, raw("USART1_TX, TIM1_CH2")),
                Pin("A10", "PA10", Data, raw("USART1_RX, TIM1_CH3")),
                Pin("A11", "PA11", Data, raw("USB_DM, CAN_RX, USART1_CTS, TIM1_CH4")),
                Pin("A12", "PA12", Data, raw("USB_DP, CAN_TX, USART1_RTS, TIM1_ETR")),
                Pin("A15", "PA15", Data, raw("JTDI, SPI1_NSS remap, TIM2_CH1_ETR")),
                Pin("B3", "PB3", Data, raw("JTDO/TRACESWO, SPI1_SCK remap, TIM2_CH2")),
                Pin("B4", "PB4", Data, raw("NJTRST, SPI1_MISO remap, TIM3_CH1")),
                Pin("B5", "PB5", Data, raw("I2C1_SMBA, SPI1_MOSI remap, TIM3_CH2")),
                Pin("B6", "PB6", Data, raw("I2C1_SCL, USART1_TX remap, TIM4_CH1")),
                Pin("B7", "PB7", Data, raw("I2C1_SDA, USART1_RX remap, TIM4_CH2")),
                Pin("B8", "PB8", Data, raw("I2C1_SCL remap, CAN_RX remap, TIM4_CH3")),
                Pin("B9", "PB9", Data, raw("I2C1_SDA remap, CAN_TX remap, TIM4_CH4")),
                Pin("5V", "5V", Power, res(R.string.rf_stm32_5v)),
                Pin("GND", "GND", Ground),
                Pin("3V3", "3V3", Power),
            ),
            listOf(
                Pin("GND", "GND", Ground),
                Pin("GND", "GND", Ground),
                Pin("3V3", "3V3", Power),
                Pin("R", "NRST", Control, res(R.string.rf_stm32_reset)),
                Pin("B11", "PB11", Data, raw("I2C2_SDA, USART3_RX, TIM2_CH4")),
                Pin("B10", "PB10", Data, raw("I2C2_SCL, USART3_TX, TIM2_CH3")),
                Pin("B1", "PB1", Analog, raw("ADC12_IN9, TIM3_CH4, TIM1_CH3N")),
                Pin("B0", "PB0", Analog, raw("ADC12_IN8, TIM3_CH3, TIM1_CH2N")),
                Pin("A7", "PA7", Analog, raw("ADC12_IN7, SPI1_MOSI, TIM3_CH2, TIM1_CH1N")),
                Pin("A6", "PA6", Analog, raw("ADC12_IN6, SPI1_MISO, TIM3_CH1, TIM1_BKIN")),
                Pin("A5", "PA5", Analog, raw("ADC12_IN5, SPI1_SCK")),
                Pin("A4", "PA4", Analog, raw("ADC12_IN4, SPI1_NSS, USART2_CK")),
                Pin("A3", "PA3", Analog, raw("ADC12_IN3, TIM2_CH4, USART2_RX")),
                Pin("A2", "PA2", Analog, raw("ADC12_IN2, TIM2_CH3, USART2_TX")),
                Pin("A1", "PA1", Analog, raw("ADC12_IN1, TIM2_CH2, USART2_RTS")),
                Pin("A0", "PA0", Analog, raw("ADC12_IN0, TIM2_CH1_ETR, USART2_CTS, WKUP")),
                Pin("C15", "PC15", Signal, raw("OSC32_OUT, LSE")),
                Pin("C14", "PC14", Signal, raw("OSC32_IN, LSE")),
                Pin("C13", "PC13", Signal, raw("TAMPER-RTC, onboard LED")),
                Pin("VB", "VBAT", Power, res(R.string.rf_stm32_vbat)),
            ),
            listOf(
                Pin("3V3", "3V3", Power),
                Pin("DIO", "SWDIO (PA13)", Data, raw("PA13, JTMS/SWDIO")),
                Pin("CLK", "SWCLK (PA14)", Data, raw("PA14, JTCK/SWCLK")),
                Pin("GND", "GND", Ground),
            )
        )
    }

    private val BLACK_PILL: List<Pin> by lazy {
        pill(
            listOf(
                Pin("B12", "PB12", Data, raw("SPI2_NSS, I2C2_SMBA, TIM1_BKIN")),
                Pin("B13", "PB13", Data, raw("SPI2_SCK, TIM1_CH1N")),
                Pin("B14", "PB14", Data, raw("SPI2_MISO, TIM1_CH2N")),
                Pin("B15", "PB15", Data, raw("SPI2_MOSI, TIM1_CH3N")),
                Pin("A8", "PA8", Data, raw("MCO1, TIM1_CH1, USART1_CK, I2C3_SCL")),
                Pin("A9", "PA9", Data, raw("USART1_TX, USB_OTG_FS_VBUS, TIM1_CH2, I2C3_SMBA")),
                Pin("A10", "PA10", Data, raw("USART1_RX, USB_OTG_FS_ID, TIM1_CH3")),
                Pin("A11", "PA11", Data, raw("USB_OTG_FS_DM, USART1_CTS, TIM1_CH4")),
                Pin("A12", "PA12", Data, raw("USB_OTG_FS_DP, USART1_RTS, TIM1_ETR")),
                Pin("A15", "PA15", Data, raw("JTDI, SPI1_NSS, SPI3_NSS, I2S3_WS, TIM2_CH1")),
                Pin("B3", "PB3", Data, raw("JTDO, TRACESWO, SPI1_SCK, SPI3_SCK, I2S3_CK")),
                Pin("B4", "PB4", Data, raw("NJTRST, SPI1_MISO, SPI3_MISO, TIM3_CH1")),
                Pin("B5", "PB5", Data, raw("I2C1_SMBA, SPI1_MOSI, SPI3_MOSI, TIM3_CH2")),
                Pin("B6", "PB6", Data, raw("I2C1_SCL, USART1_TX, TIM4_CH1")),
                Pin("B7", "PB7", Data, raw("I2C1_SDA, USART1_RX, TIM4_CH2")),
                Pin("B8", "PB8", Data, raw("I2C1_SCL, TIM4_CH3, SDIO_D4")),
                Pin("B9", "PB9", Data, raw("I2C1_SDA, TIM4_CH4, SDIO_D5")),
                Pin("5V", "5V", Power, res(R.string.rf_stm32_5v)),
                Pin("GND", "GND", Ground),
                Pin("3V3", "3V3", Power),
            ),
            listOf(
                Pin("5V", "5V", Power, res(R.string.rf_stm32_5v)),
                Pin("GND", "GND", Ground),
                Pin("3V3", "3V3", Power),
                Pin("B10", "PB10", Data, raw("I2C2_SCL, SPI2_SCK, TIM2_CH3")),
                Pin("B2", "PB2", Signal, raw("BOOT1")),
                Pin("B1", "PB1", Analog, raw("ADC1_IN9, TIM3_CH4")),
                Pin("B0", "PB0", Analog, raw("ADC1_IN8, TIM3_CH3")),
                Pin("A7", "PA7", Analog, raw("ADC1_IN7, SPI1_MOSI, TIM3_CH2, TIM14_CH1")),
                Pin("A6", "PA6", Analog, raw("ADC1_IN6, SPI1_MISO, TIM3_CH1, TIM13_CH1")),
                Pin("A5", "PA5", Analog, raw("ADC1_IN5, SPI1_SCK")),
                Pin("A4", "PA4", Analog, raw("ADC1_IN4, SPI1_NSS, SPI3_NSS, I2S3_WS, USART2_CK")),
                Pin("A3", "PA3", Analog, raw("ADC1_IN3, USART2_RX, TIM2_CH4, TIM5_CH4")),
                Pin("A2", "PA2", Analog, raw("ADC1_IN2, USART2_TX, TIM2_CH3, TIM5_CH3")),
                Pin("A1", "PA1", Analog, raw("ADC1_IN1, TIM2_CH2, TIM5_CH2, USART2_RTS")),
                Pin("A0", "PA0", Analog, raw("ADC1_IN0, TIM2_CH1, TIM5_CH1, USART2_CTS, WKUP")),
                Pin("NRST", "NRST", Control, res(R.string.rf_stm32_reset)),
                Pin("C15", "PC15", Signal, raw("OSC32_OUT")),
                Pin("C14", "PC14", Signal, raw("OSC32_IN")),
                Pin("C13", "PC13", Signal, raw("RTC_AF1, TAMPER")),
                Pin("VBAT", "VBAT", Power, res(R.string.rf_stm32_vbat)),
            ),
            listOf(
                Pin("3V3", "3V3", Power),
                Pin("A13", "PA13", Data, raw("JTMS, SWDIO")),
                Pin("A14", "PA14", Data, raw("JTCK, SWCLK")),
                Pin("GND", "GND", Ground),
            )
        )
    }

    override val sheets by lazy {
            listOf(
                PinoutSheet(
                raw("Blue Pill"), BLUE_PILL, note = res(R.string.rf_stm32_blue_note),
                specs = listOf(
                    spec(SpecKind.Chip, "STM32F103C8 (Cortex-M3)"), spec(SpecKind.Clock, "72 MHz"), spec(SpecKind.Flash, "64 KB"),
                    spec(SpecKind.Ram, "20 KB"), spec(SpecKind.Logic, "3.3 V"), spec(SpecKind.Supply, "5 V (USB) / 3.3 V"),
                )
            ),
            PinoutSheet(
                raw("Black Pill"), BLACK_PILL, note = res(R.string.rf_stm32_black_note),
                specs = listOf(
                    spec(SpecKind.Chip, "STM32F411CE / F401CC (Cortex-M4)"), spec(SpecKind.Clock, "100 / 84 MHz"),
                    spec(SpecKind.Flash, "512 / 256 KB"), spec(SpecKind.Ram, "128 / 64 KB"), spec(SpecKind.Logic, "3.3 V"),
                    spec(SpecKind.Supply, "5 V (USB-C) / 3.3 V"),
                )
            ),
        )
    }
}
