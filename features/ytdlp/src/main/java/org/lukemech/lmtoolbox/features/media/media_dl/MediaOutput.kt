package org.lukemech.lmtoolbox.features.media.media_dl

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.features.ytdlp.R
import org.lukemech.lmtoolbox.helpers.OutputFolder
import java.io.File

/** Where downloads go: Downloads, or a folder picked by the user ([MediaDownloaderFeature.S_OUTPUT]). */
object MediaOutput {
    private val folder = OutputFolder(MediaDownloaderFeature.S_OUTPUT)

    /** Copies [file] into the chosen folder; null when none is (it goes to Downloads then). */
    fun save(context: Context, file: File, mimeType: String): Uri? = folder.save(context, file, mimeType)

    /**
     * Where downloads go: Downloads or a folder picked here (and back to Downloads). In the download
     * options, in the settings and before each download.
     */
    @Composable
    fun SaveToSetting() {
        folder.Setting(
            stringResource(R.string.f_MediaDl_save_to),
            stringResource(R.string.f_MediaDl_downloads),
            stringResource(R.string.f_MediaDl_save_to_reset)
        )
    }

    /** The folder's name as the files app shows it, or "Downloads". */
    fun outputFolderName(context: Context): String = folder.name(context, context.getString(R.string.f_MediaDl_downloads))
}
