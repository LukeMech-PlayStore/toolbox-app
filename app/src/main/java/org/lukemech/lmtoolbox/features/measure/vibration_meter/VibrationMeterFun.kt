package org.lukemech.lmtoolbox.features.measure.vibration_meter

import android.content.Context
import android.hardware.Sensor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.measure.common.hasSensor
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.measure.vibration_meter.VibrationMeterPage

/** Vibration meter: the accelerometer's motion (gravity taken out) as RMS, peak and its main frequency. */
object VibrationMeterFeature : AppFeature {

    override val route = "vibration_meter"
    override val titleStringResId: Int = R.string.fp_vibration_meter
    override val keywordsResId: Int = R.string.fk_vibration_meter
    override val descriptionResId: Int = R.string.fd_vibration_meter
    override val category = FeatureCategory.Measure
    override val icon @Composable get() = Icons.Default.Vibration

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        VibrationMeterPage()
    }

    /** Whether the device has an accelerometer (every phone has). */
    fun hasSensors(context: Context) = hasSensor(context, Sensor.TYPE_ACCELEROMETER)

    /**
     * The sensor read: Android's linear acceleration (gravity taken out by it, from the gyroscope too) where
     * there's one, else the accelerometer (its gravity taken out here, see [GravityFilter]).
     */
    fun sensorType(context: Context) =
        if (hasSensor(context, Sensor.TYPE_LINEAR_ACCELERATION)) Sensor.TYPE_LINEAR_ACCELERATION else Sensor.TYPE_ACCELEROMETER
}
