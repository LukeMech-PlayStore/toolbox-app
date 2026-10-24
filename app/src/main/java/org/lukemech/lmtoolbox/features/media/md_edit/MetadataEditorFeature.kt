package org.lukemech.lmtoolbox.features.media.md_edit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.ModuleFeature

/** Metadata editor: its pages are in the exiftool module (features/exiftool, its media/md_edit). */
object MetadataEditorFeature : ModuleFeature(
    route = "md_edit",
    titleStringResId = R.string.fp_MdEd,
    keywordsResId = R.string.fk_MdEd,
    descriptionResId = R.string.fd_MdEd,
    category = FeatureCategory.Media,
    moduleIcon = Icons.Default.EditNote,
    linuxExtensions = listOf("exiftool"),
    uiClass = "org.lukemech.lmtoolbox.features.media.md_edit.ExifToolUi",
    cacheName = "metadata",
) {
    const val S_DONKEY = "MDEdit_!OpenIn"
    const val S_DSHKEY = "MDEdit_!ShareTo"

    override val preferenceKeys = listOf(S_DONKEY, S_DSHKEY)
}
