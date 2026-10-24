package org.lukemech.lmtoolbox.features.electronics.reactance

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Waves
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.reactance.ReactancePage

/** Reactance: of a capacitor, an inductor, and LC resonance. */
object ReactanceFeature : AppFeature {

    override val route = "reactance"
    override val titleStringResId: Int = R.string.fp_reactance
    override val keywordsResId: Int = R.string.fk_reactance
    override val descriptionResId: Int = R.string.fd_reactance
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Waves

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        ReactancePage()
    }
}
