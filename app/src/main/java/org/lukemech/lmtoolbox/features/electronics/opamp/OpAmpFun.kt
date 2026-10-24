package org.lukemech.lmtoolbox.features.electronics.opamp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.opamp.OpAmpPage

/** Op-amp: gain of an inverting or non-inverting amplifier. */
object OpAmpFeature : AppFeature {

    override val route = "opamp"
    override val titleStringResId: Int = R.string.fp_opamp
    override val keywordsResId: Int = R.string.fk_opamp
    override val descriptionResId: Int = R.string.fd_opamp
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.ChangeHistory

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        OpAmpPage()
    }
}
