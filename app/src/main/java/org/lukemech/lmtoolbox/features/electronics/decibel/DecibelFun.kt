package org.lukemech.lmtoolbox.features.electronics.decibel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.decibel.DecibelPage

/** Decibels: and the power or voltage ratios they stand for. */
object DecibelFeature : AppFeature {

    override val route = "decibel"
    override val titleStringResId: Int = R.string.fp_decibel
    override val keywordsResId: Int = R.string.fk_decibel
    override val descriptionResId: Int = R.string.fd_decibel
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.AutoMirrored.Default.VolumeUp

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        DecibelPage()
    }
}
