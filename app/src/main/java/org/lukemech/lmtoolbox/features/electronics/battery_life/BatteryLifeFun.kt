package org.lukemech.lmtoolbox.features.electronics.battery_life

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.battery_life.BatteryLifePage

/** Battery life: how long a battery lasts a load. */
object BatteryLifeFeature : AppFeature {

    override val route = "battery_life"
    override val titleStringResId: Int = R.string.fp_battery_life
    override val keywordsResId: Int = R.string.fk_battery_life
    override val descriptionResId: Int = R.string.fd_battery_life
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.BatteryFull

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        BatteryLifePage()
    }
}
