package org.lukemech.lmtoolbox.features.media.video_enc

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.features.ffmpeg.R
import org.lukemech.lmtoolbox.helpers.FeatureUi
import org.lukemech.lmtoolbox.ui.components.ExpandableButton
import org.lukemech.lmtoolbox.ui.pages.features.media.video_enc.VideoEncoderPage
import org.lukemech.lmtoolbox.ui.pages.features.media.video_enc.OutputForm
import org.lukemech.lmtoolbox.ui.pages.features.media.video_enc.FolderSetting

/** The video encoder's pages (see [VideoEncoderFeature]). */
@Suppress("unused") // Loaded by its name, VideoEncoderFeature.uiClass
object FfmpegUi : FeatureUi {
    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        VideoEncoderPage(arguments?.getOrElse("sharedText") { null })
    }

    override val hasSettingsContent get() = true

    /** What a new file is made as (as its output step has it); and its "Share to" entry. */
    @Composable
    override fun SettingsContent() {
        val context = LocalContext.current
        var options by remember { mutableStateOf(EncodeOptions.load(context)) }

        ExpandableButton(stringResource(R.string.f_VidEnc_default_options), Icons.Default.Tune) {
            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                OutputForm(null, VideoJobs.encoders, options, {
                    options = it
                    EncodeOptions.save(context, it)
                }, folder = false)
            }
        }
        // Where each type goes: videos, sound, GIFs
        Format.Kind.entries.forEach { FolderSetting(it) }
    }
}
