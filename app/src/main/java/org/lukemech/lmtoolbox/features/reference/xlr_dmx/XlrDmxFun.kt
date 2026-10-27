package org.lukemech.lmtoolbox.features.reference.xlr_dmx

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Audio
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Data
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Shield
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.inRow
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** XLR: balanced audio on three pins (AES14), and DMX512 lighting control on five (ANSI E1.11). */
object XlrDmxFeature : ReferenceFeature() {

    override val route = "xlr_dmx"
    override val titleStringResId: Int = R.string.fp_xlr_dmx
    override val keywordsResId: Int = R.string.fk_xlr_dmx
    override val descriptionResId: Int = R.string.fd_xlr_dmx
    override val icon @Composable get() = Icons.Default.Mic

    override val sheets by lazy {
        listOf(
            PinoutSheet(
                raw("XLR 3"),
                listOf(
                    Pin("1", "GND", Shield, res(R.string.rf_xlr_ground)),
                    Pin("2", "+", Audio, res(R.string.rf_xlr_hot)),
                    Pin("3", "−", Audio, res(R.string.rf_xlr_cold)),
                ).inRow(),
                note = res(R.string.rf_xlr_note),
                inOrder = true
            ),
            PinoutSheet(
                raw("DMX 5"),
                listOf(
                    Pin("1", "COM", Shield, res(R.string.rf_dmx_common)),
                    Pin("2", "D1−", Data, res(R.string.rf_dmx_data1)),
                    Pin("3", "D1+", Data, res(R.string.rf_dmx_data1)),
                    Pin("4", "D2−", Data, res(R.string.rf_dmx_data2)),
                    Pin("5", "D2+", Data, res(R.string.rf_dmx_data2)),
                ).inRow(),
                note = res(R.string.rf_dmx_note),
                inOrder = true
            ),
        )
    }
}
