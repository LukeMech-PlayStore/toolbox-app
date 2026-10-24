package org.lukemech.lmtoolbox.features.measure.ruler

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.measure.ruler.RulerPage

object RulerFeature : AppFeature {

    override val route = "ruler"
    override val titleStringResId: Int = R.string.fp_ruler
    override val keywordsResId: Int = R.string.fk_ruler
    override val descriptionResId: Int = R.string.fd_ruler
    override val category = FeatureCategory.Measure
    override val icon @Composable get() = Icons.Default.Straighten

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        RulerPage()
    }
}
