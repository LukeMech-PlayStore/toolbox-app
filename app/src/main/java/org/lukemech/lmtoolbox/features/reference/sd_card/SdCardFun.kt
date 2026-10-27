package org.lukemech.lmtoolbox.features.reference.sd_card

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Clock
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Control
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Data
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Ground
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Power
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.Txt
import org.lukemech.lmtoolbox.features.reference.common.inRow
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** SD and microSD cards' contacts: each pin's name on the SD bus, and in SPI mode. */
object SdCardFeature : ReferenceFeature() {

    override val route = "sd_card"
    override val titleStringResId: Int = R.string.fp_sd_card
    override val keywordsResId: Int = R.string.fk_sd_card
    override val descriptionResId: Int = R.string.fd_sd_card
    override val icon @Composable get() = Icons.Default.SdCard

    /** A contact: its SD-bus name, and what it is in SPI mode. */
    private fun pin(n: Int, sd: String, kind: PinKind, spi: String) = Pin(n.toString(), sd, kind, Txt.Join(listOf(raw("SPI:"), raw(spi)), " "))

    private val SD by lazy {
        listOf(
            pin(1, "CD/DAT3", Data, "CS"),
            pin(2, "CMD", Control, "DI (MOSI)"),
            pin(3, "VSS1", Ground, "VSS"),
            pin(4, "VDD", Power, "VDD"),
            pin(5, "CLK", Clock, "SCLK"),
            pin(6, "VSS2", Ground, "VSS"),
            pin(7, "DAT0", Data, "DO (MISO)"),
            pin(8, "DAT1", Data, "—"),
            pin(9, "DAT2", Data, "—"),
        )
    }

    private val MICRO_SD by lazy {
        listOf(
            pin(1, "DAT2", Data, "—"),
            pin(2, "CD/DAT3", Data, "CS"),
            pin(3, "CMD", Control, "DI (MOSI)"),
            pin(4, "VDD", Power, "VDD"),
            pin(5, "CLK", Clock, "SCLK"),
            pin(6, "VSS", Ground, "VSS"),
            pin(7, "DAT0", Data, "DO (MISO)"),
            pin(8, "DAT1", Data, "—"),
        )
    }

    override val sheets by lazy {
        listOf(
            // Pin 9 is beside pin 1: 9, 1, 2... 8
            PinoutSheet(raw("SD"), SD.mapIndexed { i, pin -> pin.copy(x = if (i == 8) 0f else i + 1f, y = 0f) }, note = res(R.string.rf_sd_note)),
            PinoutSheet(raw("microSD"), MICRO_SD.inRow(), note = res(R.string.rf_microsd_note)),
        )
    }
}
