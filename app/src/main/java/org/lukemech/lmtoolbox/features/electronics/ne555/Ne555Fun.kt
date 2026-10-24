package org.lukemech.lmtoolbox.features.electronics.ne555

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.ne555.Ne555Page

/** NE555: the timer's astable and monostable modes. */
object Ne555Feature : AppFeature {

    override val route = "ne555"
    override val titleStringResId: Int = R.string.fp_ne555
    override val keywordsResId: Int = R.string.fk_ne555
    override val descriptionResId: Int = R.string.fd_ne555
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Timer

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        Ne555Page()
    }
}
