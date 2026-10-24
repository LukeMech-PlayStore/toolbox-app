package org.lukemech.lmtoolbox.features.electronics.y_delta

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.y_delta.YDeltaPage

/** Y-Δ: star and delta networks into each other. */
object YDeltaFeature : AppFeature {

    override val route = "y_delta"
    override val titleStringResId: Int = R.string.fp_y_delta
    override val keywordsResId: Int = R.string.fk_y_delta
    override val descriptionResId: Int = R.string.fd_y_delta
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Hub

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        YDeltaPage()
    }
}
