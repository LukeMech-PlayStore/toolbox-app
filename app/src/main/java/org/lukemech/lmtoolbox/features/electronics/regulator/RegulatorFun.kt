package org.lukemech.lmtoolbox.features.electronics.regulator

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.regulator.RegulatorPage

/** Voltage regulator: an LM317's resistors. */
object RegulatorFeature : AppFeature {

    override val route = "regulator"
    override val titleStringResId: Int = R.string.fp_regulator
    override val keywordsResId: Int = R.string.fk_regulator
    override val descriptionResId: Int = R.string.fd_regulator
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Tune

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        RegulatorPage()
    }
}
