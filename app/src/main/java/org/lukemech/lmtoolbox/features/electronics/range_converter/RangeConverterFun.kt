package org.lukemech.lmtoolbox.features.electronics.range_converter

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.range_converter.RangeConverterPage

/** Range converter: a signal from one range to another (4–20 mA to %, 0–10 V to engineering units...). */
object RangeConverterFeature : AppFeature {

    override val route = "range_converter"
    override val titleStringResId: Int = R.string.fp_range_converter
    override val keywordsResId: Int = R.string.fk_range_converter
    override val descriptionResId: Int = R.string.fd_range_converter
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Speed

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        RangeConverterPage()
    }
}
