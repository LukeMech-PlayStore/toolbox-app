package org.lukemech.lmtoolbox.features.reference.car_audio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Audio
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Control
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Ground
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Power
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Unused
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.Wire
import org.lukemech.lmtoolbox.features.reference.common.inRow
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** A car radio's ISO 10487 connectors: A (power) and B (speakers). */
object CarAudioFeature : ReferenceFeature() {

    override val route = "car_audio"
    override val titleStringResId: Int = R.string.fp_car_audio
    override val keywordsResId: Int = R.string.fk_car_audio
    override val descriptionResId: Int = R.string.fd_car_audio
    override val icon @Composable get() = Icons.Default.Radio

    private fun varies(n: Int) = Pin("A$n", "—", Unused, res(R.string.rf_car_varies))

    /** A speaker's two wires: + in its color, − in its color with a black stripe. */
    private fun speaker(n: Int, name: Int, color: Color) = listOf(
        Pin("B$n", "+", Audio, res(name), wire = listOf(color)),
        Pin("B${n + 1}", "−", Audio, res(name), wire = listOf(color, Wire.Black)),
    )

    override val sheets by lazy {
        listOf(
            PinoutSheet(
                raw("A"),
                listOf(
                    varies(1), varies(2), varies(3),
                    Pin("A4", "+12V", Power, res(R.string.rf_car_permanent), wire = listOf(Wire.Yellow)),
                    Pin("A5", "ANT / REM", Control, res(R.string.rf_car_remote)),
                    Pin("A6", "ILL", Control, res(R.string.rf_car_illumination)),
                    Pin("A7", "+12V ACC", Power, res(R.string.rf_car_switched), wire = listOf(Wire.Red)),
                    Pin("A8", "GND", Ground, wire = listOf(Wire.Black)),
                ).inRow(),
                note = res(R.string.rf_car_a_note),
                inOrder = true
            ),
            PinoutSheet(
                raw("B"),
                (speaker(1, R.string.rf_car_rr, Wire.Violet) + speaker(3, R.string.rf_car_rf, Wire.Grey) +
                    speaker(5, R.string.rf_car_lf, Wire.White) + speaker(7, R.string.rf_car_lr, Wire.Green)).inRow(),
                note = res(R.string.rf_car_b_note),
                inOrder = true
            ),
        )
    }
}
