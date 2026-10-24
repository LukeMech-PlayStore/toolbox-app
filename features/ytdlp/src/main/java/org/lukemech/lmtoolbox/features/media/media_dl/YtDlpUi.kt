package org.lukemech.lmtoolbox.features.media.media_dl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.features.ytdlp.R
import org.lukemech.lmtoolbox.helpers.FeatureUi
import org.lukemech.lmtoolbox.ui.components.ExpandableButton
import org.lukemech.lmtoolbox.ui.pages.features.media.media_dl.OptionsForm
import org.lukemech.lmtoolbox.ui.pages.features.media.media_dl.MediaDownloaderPage

/** The media downloader's pages (see [MediaDownloaderFeature]). */
@Suppress("unused") // Loaded by its name, MediaDownloaderFeature.uiClass
object YtDlpUi : FeatureUi {
    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        MediaDownloaderPage(arguments?.getOrElse("sharedText") { null })
    }

    override val hasSettingsContent get() = true

    /**
     * The download options a new download starts with (with where downloads go): as its options
     * step has them; and its "Share to" entry.
     */
    @Composable
    override fun SettingsContent() {
        val context = LocalContext.current
        var options by remember { mutableStateOf(MediaOptions.load(context)) }

        ExpandableButton(stringResource(R.string.f_MediaDl_default_options), Icons.Default.Tune) {
            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                OptionsForm(null, options, {
                    options = it
                    MediaOptions.save(context, it)
                })
            }
        }
    }
}
