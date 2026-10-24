package org.lukemech.lmtoolbox.features.media.video_enc

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoSettings
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.ModuleFeature
import org.lukemech.lmtoolbox.helpers.ResourceTile

/** Video encoder: its pages and FFmpeg's use are in the ffmpeg module (features/ffmpeg, its media/video_enc). */
object VideoEncoderFeature : ModuleFeature(
    route = "video_enc",
    titleStringResId = R.string.fp_VideoEnc,
    keywordsResId = R.string.fk_VideoEnc,
    descriptionResId = R.string.fd_VideoEnc,
    category = FeatureCategory.Media,
    moduleIcon = Icons.Default.VideoSettings,
    linuxExtensions = listOf("ffmpeg"),
    uiClass = "org.lukemech.lmtoolbox.features.media.video_enc.FfmpegUi",
    // The open file's copy (FFmpeg reads it) and the new one, until it's saved
    cacheName = "video",
    resourceTiles = listOf(ResourceTile.Cpu, ResourceTile.Ram, ResourceTile.Disk),
) {
    const val S_DSHKEY = "VideoEnc_!ShareTo"
    /** The last encoding's choices, for the next one (see EncodeOptions). */
    const val S_OPTIONS = "VideoEnc_options"
    /** Folders picked to save videos, sound and GIFs to (OutputFolder); none: Movies, Music, Pictures. */
    const val S_OUTPUT = "VideoEnc_output"
    const val S_OUTPUT_AUDIO = "VideoEnc_output_audio"
    const val S_OUTPUT_GIF = "VideoEnc_output_gif"

    override val preferenceKeys = listOf(S_DSHKEY, S_OPTIONS, S_OUTPUT, S_OUTPUT_AUDIO, S_OUTPUT_GIF)
}
