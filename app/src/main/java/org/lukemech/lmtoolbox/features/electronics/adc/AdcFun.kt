package org.lukemech.lmtoolbox.features.electronics.adc

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataArray
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.adc.AdcPage

/** ADC: codes and voltages of an analog-to-digital converter. */
object AdcFeature : AppFeature {

    override val route = "adc"
    override val titleStringResId: Int = R.string.fp_adc
    override val keywordsResId: Int = R.string.fk_adc
    override val descriptionResId: Int = R.string.fd_adc
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.DataArray

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        AdcPage()
    }
}
