package org.lukemech.lmtoolbox.features.media.image_resize

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.ui.components.ExpandableButton
import androidx.compose.ui.unit.dp
import java.io.File
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.media.image_resize.OptionsForm
import org.lukemech.lmtoolbox.ui.pages.features.media.image_resize.ImageResizerPage
import org.lukemech.lmtoolbox.ui.pages.features.media.image_resize.FolderSetting

/** Image resizer: Android's own decoders, nothing to download (see [ImageResize]). */
object ImageResizerFeature : AppFeature {
    override val route = "image_resize"
    override val titleStringResId: Int = R.string.fp_ImgRes
    override val keywordsResId: Int = R.string.fk_ImgRes
    override val descriptionResId: Int = R.string.fd_ImgRes
    override val category = FeatureCategory.Media
    override val icon @Composable get() = Icons.Default.PhotoSizeSelectLarge

    const val S_DSHKEY = "ImgRes_!ShareTo"
    /** The last resizing's choices, for the next one (see ResizeOptions). */
    const val S_OPTIONS = "ImgRes_options"
    /** Folder (a document tree) pictures are saved to; Pictures when unset. */
    const val S_OUTPUT = "ImgRes_output"

    override val preferenceKeys = listOf(S_DSHKEY, S_OPTIONS, S_OUTPUT)

    /** The new picture until it's saved. */
    fun cacheDir(context: Context) = File(context.cacheDir, "image")
    override fun dataFiles(context: Context) = listOf(cacheDir(context))

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        ImageResizerPage(arguments?.getOrElse("sharedText") { null })
    }

    override val hasSettingsContent get() = true

    /** What a new picture is made as (as its options step has it), where pictures go, and its "Share to" entry. */
    @Composable
    override fun SettingsContent() {
        val context = LocalContext.current
        var options by remember { mutableStateOf(ResizeOptions.load(context)) }

        ExpandableButton(stringResource(R.string.f_ImgRes_default_options), Icons.Default.Tune) {
            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                OptionsForm(null, options, {
                    options = it
                    ResizeOptions.save(context, it)
                }, folder = false)
            }
        }
        FolderSetting()
    }
}
