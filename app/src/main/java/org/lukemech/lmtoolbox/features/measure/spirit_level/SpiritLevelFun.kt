package org.lukemech.lmtoolbox.features.measure.spirit_level

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.ReadingFilter
import org.lukemech.lmtoolbox.ui.widgets.features.measure.spirit_level.SpiritLevelWidget
import kotlin.math.atan2
import org.lukemech.lmtoolbox.ui.pages.features.measure.spirit_level.SpiritLevelPage

object SpiritLevelFeature : AppFeature {

    override val route = "spirit_level"
    override val titleStringResId: Int = R.string.fp_spirit_level
    override val keywordsResId: Int = R.string.fk_spirit_level
    override val descriptionResId: Int = R.string.fd_spirit_level
    override val category = FeatureCategory.Measure
    // Same drawable as the widget's notification icon, so the app holds it once
    override val icon @Composable get() = ImageVector.vectorResource(R.drawable.cyclone)
    override val widget by lazy { SpiritLevelWidget() }

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        SpiritLevelPage()
    }

    /** Whether the device has the sensor the spirit level needs. */
    fun hasSensors(context: Context): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }

    /**
     * Accelerometer readings turned into the tilt and accuracy state. The page and the widget
     * each feed one with their listener's events, so both judge them the same way.
     */
    class SensorState {
        /** Tilt forward / back in degrees. */
        var pitch = 0f
            private set

        /** Tilt left / right in degrees. */
        var roll = 0f
            private set

        private var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        // One per axis, so a move in one doesn't let the other's noise through
        private val pitchFilter = ReadingFilter(MIN_CHANGE)
        private val rollFilter = ReadingFilter(MIN_CHANGE)

        /** Taken from every reading: accuracy callbacks come rarely and late. */
        val isCalibrated get() = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM

        fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            val gX = event.values[0] // right-left
            val gY = event.values[1] // up-down
            val gZ = event.values[2] // Z
            accuracy = event.accuracy
            val newPitch = Math.toDegrees(atan2(-gY.toDouble(), gZ.toDouble())).toFloat()
            val newRoll = Math.toDegrees(atan2(-gX.toDouble(), gZ.toDouble())).toFloat()
            if (pitchFilter.accept(newPitch)) pitch = newPitch
            if (rollFilter.accept(newRoll)) roll = newRoll
        }

        fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            if (sensor.type == Sensor.TYPE_ACCELEROMETER) this.accuracy = accuracy
        }

        private companion object {
            /** Smaller changes are sensor noise (a level needs finer steps than the compass's 5°). */
            const val MIN_CHANGE = 0.5f
        }
    }
}
