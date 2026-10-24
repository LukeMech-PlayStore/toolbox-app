package org.lukemech.lmtoolbox.features.electronics.smd_code

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.smd_code.SmdCodePage

/** SMD code: a resistor's value from its marking. */
object SmdCodeFeature : AppFeature {

    override val route = "smd_code"
    override val titleStringResId: Int = R.string.fp_smd_code
    override val keywordsResId: Int = R.string.fk_smd_code
    override val descriptionResId: Int = R.string.fd_smd_code
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Tag

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        SmdCodePage()
    }
}
