package org.lukemech.lmtoolbox.features.media.file_size

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.media.file_size.FileSizePage

/**
 * File size: a shortcut asking what kind of file it is, then opening the tool for it (the media encoder,
 * the image resizer) aiming at a file size (see [FileSizeRequest]).
 */
object FileSizeFeature : AppFeature {
    override val route = "file_size"
    override val titleStringResId: Int = R.string.fp_FileSize
    override val keywordsResId: Int = R.string.fk_FileSize
    override val descriptionResId: Int = R.string.fd_FileSize
    override val category = FeatureCategory.Media
    override val icon @Composable get() = Icons.Default.Compress

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        FileSizePage()
    }
}

/** A tool opened from the [FileSizeFeature] shortcut: its page starts aiming at a file size. Taken once, by that page. */
object FileSizeRequest {
    @Volatile
    private var route: String? = null

    fun request(feature: AppFeature) {
        route = feature.route
    }

    /** Whether [feature] was asked for (forgotten then). */
    fun take(feature: AppFeature): Boolean = (route == feature.route).also { if (it) route = null }
}
