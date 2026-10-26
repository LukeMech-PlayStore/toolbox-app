package org.lukemech.lmtoolbox.features.electronics.number_base

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.number_base.NumberBasePage

/** Number bases and bits: bin, oct, dec and hex, two's complement, IEEE 754 floats. */
object NumberBaseFeature : AppFeature {

    override val route = "number_base"
    override val titleStringResId: Int = R.string.fp_number_base
    override val keywordsResId: Int = R.string.fk_number_base
    override val descriptionResId: Int = R.string.fd_number_base
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Numbers

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        NumberBasePage()
    }
}
