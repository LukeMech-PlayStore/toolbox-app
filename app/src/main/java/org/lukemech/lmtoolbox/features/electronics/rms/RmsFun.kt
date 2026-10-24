package org.lukemech.lmtoolbox.features.electronics.rms

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.rms.RmsPage

/** RMS: peak and average values of a waveform. */
object RmsFeature : AppFeature {

    override val route = "rms"
    override val titleStringResId: Int = R.string.fp_rms
    override val keywordsResId: Int = R.string.fk_rms
    override val descriptionResId: Int = R.string.fd_rms
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.AutoMirrored.Default.ShowChart

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        RmsPage()
    }
}
