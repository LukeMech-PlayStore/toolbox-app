package org.lukemech.lmtoolbox.features.electronics.coil

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Loop
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.coil.CoilPage

/** Coil: inductance of an air-core coil. */
object CoilFeature : AppFeature {

    override val route = "coil"
    override val titleStringResId: Int = R.string.fp_coil
    override val keywordsResId: Int = R.string.fk_coil
    override val descriptionResId: Int = R.string.fd_coil
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Loop

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        CoilPage()
    }
}
