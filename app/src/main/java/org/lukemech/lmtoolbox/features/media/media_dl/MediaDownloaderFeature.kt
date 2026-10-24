package org.lukemech.lmtoolbox.features.media.media_dl

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.ModuleFeature
import org.lukemech.lmtoolbox.helpers.ResourceTile

/** Media downloader: its pages and yt-dlp's use are in the ytdlp module (features/ytdlp, its media/media_dl). */
object MediaDownloaderFeature : ModuleFeature(
    route = "media_dl",
    titleStringResId = R.string.fp_MediaDl,
    keywordsResId = R.string.fk_MediaDl,
    descriptionResId = R.string.fd_MediaDl,
    category = FeatureCategory.Media,
    moduleIcon = Icons.Default.Download,
    linuxExtensions = listOf("ytdlp"),
    uiClass = "org.lukemech.lmtoolbox.features.media.media_dl.YtDlpUi",
    // yt-dlp downloads into it; the file moves to Downloads once it's done
    cacheName = "media",
    // Downloading, then FFmpeg joining what it got
    resourceTiles = listOf(ResourceTile.Network, ResourceTile.Cpu, ResourceTile.Disk),
) {
    const val S_DSHKEY = "MediaDl_!ShareTo"
    /** Folder (a document tree) the downloads go to; Downloads when unset. */
    const val S_OUTPUT = "MediaDl_output"

    // The last download's choices, for the next one (see MediaDownloads)
    const val S_KIND = "MediaDl_kind"
    const val S_HEIGHT = "MediaDl_height"
    const val S_CONTAINER = "MediaDl_container"
    const val S_AUDIO = "MediaDl_audio"
    const val S_THUMB = "MediaDl_thumbnail"
    const val S_META = "MediaDl_metadata"
    const val S_SUBS = "MediaDl_subtitles"
    const val S_SPONSOR = "MediaDl_sponsorblock"

    override val preferenceKeys = listOf(S_DSHKEY, S_OUTPUT, S_KIND, S_HEIGHT, S_CONTAINER, S_AUDIO, S_THUMB, S_META, S_SUBS, S_SPONSOR)
}
