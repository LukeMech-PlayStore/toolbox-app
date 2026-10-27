package org.lukemech.lmtoolbox.features.reference.audio_jack

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Audio
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Ground
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Signal
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.inRow
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/**
 * Audio jacks, 2.5, 3.5 and 6.35 mm alike: mono TS, stereo and balanced TRS, and the headsets' TRRS in both orders,
 * CTIA (most phones now) and OMTP (older).
 */
object AudioJackFeature : ReferenceFeature() {

    override val route = "audio_jack"
    override val titleStringResId: Int = R.string.fp_audio_jack
    override val keywordsResId: Int = R.string.fk_audio_jack
    override val descriptionResId: Int = R.string.fd_audio_jack
    override val icon @Composable get() = Icons.Default.Headphones

    private val LEFT = Pin("T", "L", Audio, res(R.string.rf_jack_left))
    private val RIGHT = Pin("R1", "R", Audio, res(R.string.rf_jack_right))

    private fun mic(number: String) = Pin(number, "MIC", Signal, res(R.string.rf_jack_mic))

    private fun ground(number: String) = Pin(number, "GND", Ground, res(R.string.rf_jack_ground))

    override val sheets by lazy {
        listOf(
            PinoutSheet(
                raw("TS"),
                listOf(Pin("T", "SIG", Audio, res(R.string.rf_jack_mono)), ground("S")).inRow(),
                note = res(R.string.rf_jack_ts_note)
            ),
            PinoutSheet(
                raw("TRS"),
                listOf(LEFT, RIGHT.copy(number = "R"), ground("S")).inRow(),
                note = res(R.string.rf_jack_note)
            ),
            PinoutSheet(
                raw("TRS balanced"),
                listOf(
                    Pin("T", "+", Audio, res(R.string.rf_jack_hot)),
                    Pin("R", "−", Audio, res(R.string.rf_jack_cold)),
                    ground("S"),
                ).inRow(),
                note = res(R.string.rf_jack_balanced_note)
            ),
            PinoutSheet(
                raw("TRRS CTIA"),
                listOf(LEFT, RIGHT, ground("R2"), mic("S")).inRow(),
                note = res(R.string.rf_jack_ctia_note)
            ),
            PinoutSheet(
                raw("TRRS OMTP"),
                listOf(LEFT, RIGHT, mic("R2"), ground("S")).inRow(),
                note = res(R.string.rf_jack_omtp_note)
            ),
        )
    }
}
