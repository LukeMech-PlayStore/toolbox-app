package org.lukemech.lmtoolbox.features.electronics.color_code

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.color_code.ColorCodePage

/** Color code: a resistor's or inductor's bands for a value, or the value of its bands. */
object ColorCodeFeature : AppFeature {

    override val route = "color_code"
    override val titleStringResId: Int = R.string.fp_color_code
    override val keywordsResId: Int = R.string.fk_color_code
    override val descriptionResId: Int = R.string.fd_color_code
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Palette

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        ColorCodePage()
    }
}
