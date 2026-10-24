package org.lukemech.lmtoolbox.features.electronics.series_parallel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.JoinInner
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.series_parallel.SeriesParallelPage

/** Series and parallel: two resistors or capacitors together, or a pair for a value. */
object SeriesParallelFeature : AppFeature {

    override val route = "series_parallel"
    override val titleStringResId: Int = R.string.fp_series_parallel
    override val keywordsResId: Int = R.string.fk_series_parallel
    override val descriptionResId: Int = R.string.fd_series_parallel
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.JoinInner

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        SeriesParallelPage()
    }
}
