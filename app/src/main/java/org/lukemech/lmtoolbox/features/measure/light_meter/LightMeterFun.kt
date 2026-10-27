package org.lukemech.lmtoolbox.features.measure.light_meter

import android.content.Context
import android.hardware.Sensor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.measure.common.hasSensor
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.measure.light_meter.LightMeterPage

/** Light meter: the light sensor's illuminance in lux. */
object LightMeterFeature : AppFeature {

    override val route = "light_meter"
    override val titleStringResId: Int = R.string.fp_light_meter
    override val keywordsResId: Int = R.string.fk_light_meter
    override val descriptionResId: Int = R.string.fd_light_meter
    override val category = FeatureCategory.Measure
    override val icon @Composable get() = Icons.Default.LightMode

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        LightMeterPage()
    }

    /** Whether the device has a light sensor (most have, by the screen, for its brightness). */
    fun hasSensors(context: Context) = hasSensor(context, Sensor.TYPE_LIGHT)
}
