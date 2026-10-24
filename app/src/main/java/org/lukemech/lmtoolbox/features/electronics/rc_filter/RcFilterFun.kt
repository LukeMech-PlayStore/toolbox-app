package org.lukemech.lmtoolbox.features.electronics.rc_filter

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.rc_filter.RcFilterPage

/** RC filter: cutoff frequency and response. */
object RcFilterFeature : AppFeature {

    override val route = "rc_filter"
    override val titleStringResId: Int = R.string.fp_rc_filter
    override val keywordsResId: Int = R.string.fk_rc_filter
    override val descriptionResId: Int = R.string.fd_rc_filter
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.FilterAlt

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        RcFilterPage()
    }
}
