package org.lukemech.lmtoolbox.features.electronics.led_resistor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.led_resistor.LedResistorPage

/** LED resistor: the series resistor for LEDs. */
object LedResistorFeature : AppFeature {

    override val route = "led_resistor"
    override val titleStringResId: Int = R.string.fp_led_resistor
    override val keywordsResId: Int = R.string.fk_led_resistor
    override val descriptionResId: Int = R.string.fd_led_resistor
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Lightbulb

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        LedResistorPage()
    }
}
