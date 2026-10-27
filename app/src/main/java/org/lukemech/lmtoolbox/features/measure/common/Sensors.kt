package org.lukemech.lmtoolbox.features.measure.common

import android.content.Context
import android.hardware.SensorManager

/** Whether the device has a sensor of [type] (Sensor.TYPE_*). */
fun hasSensor(context: Context, type: Int): Boolean {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    return sensorManager.getDefaultSensor(type) != null
}

/**
 * How many readings a second a sensor sends, from the first [count] of their [timestampsNs] (oldest first);
 * null for fewer than two. Android asks for a rate, the sensor picks its own: this is the one it keeps.
 */
fun sampleRate(timestampsNs: LongArray, count: Int = timestampsNs.size): Double? {
    if (count < 2) return null
    val span = timestampsNs[count - 1] - timestampsNs[0]
    return if (span > 0) (count - 1) * 1e9 / span else null
}
