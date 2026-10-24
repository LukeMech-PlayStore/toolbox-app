package org.lukemech.lmtoolbox.features.measure.compass

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.altitude.AltitudeConverterCompat
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.helpers.ReadingFilter
import org.lukemech.lmtoolbox.ui.widgets.features.measure.compass.CompassWidget
import org.lukemech.lmtoolbox.ui.widgets.features.measure.compass.CompassWidgetService
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import org.lukemech.lmtoolbox.ui.pages.features.measure.compass.CompassPage
import org.lukemech.lmtoolbox.ui.pages.features.measure.compass.AltitudeSetting

object CompassFeature : AppFeature {

    override val route = "compass"
    override val titleStringResId: Int = R.string.fp_compass
    override val keywordsResId: Int = R.string.fk_compass
    override val descriptionResId: Int = R.string.fd_compass
    override val category = FeatureCategory.Measure
    // Same drawable as the widget's notification icon, so the app holds it once
    override val icon @Composable get() = ImageVector.vectorResource(R.drawable.explore)
    override val widget by lazy { CompassWidget() }

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        CompassPage()
    }

    private const val S_ALTITUDE = "compass_altitude"
    /** Whether location was asked for once (after that, Android may stop showing its dialog). */
    const val S_LOCATION_ASKED = "compass_location_asked"

    override val preferenceKeys = listOf(S_ALTITUDE, S_LOCATION_ASKED)

    override val hasSettingsContent get() = true

    @Composable
    override fun SettingsContent() {
        AltitudeSetting()
    }

    /** Whether to show the altitude (setting, on by default; it also needs location, see [altitudeStatus]). */
    fun showAltitude(context: Context) = PreferenceHelper.get(context, S_ALTITUDE, true)

    fun setShowAltitude(context: Context, show: Boolean) {
        PreferenceHelper.put(context, S_ALTITUDE, show)
        refreshWidget(context)
    }

    /**
     * Lets the widget follow a change of the altitude setting or of location access: redrawn, and
     * its service (while on) starts or stops reading GPS.
     */
    fun refreshWidget(context: Context) {
        widget.updateAll(context)
        CompassWidgetService.refresh(context)
    }

    /** Whether the device has the sensors the compass needs. */
    fun hasSensors(context: Context): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null &&
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }

    /**
     * Sensor readings turned into the heading and calibration state. The page and the widget
     * each feed one with their listener's events, so both judge them the same way.
     */
    class SensorState {
        private val gravity = FloatArray(3)
        private val geomagnetic = FloatArray(3)
        private var hasGravity = false
        private var hasGeomagnetic = false
        private var magnetAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        private val filter = ReadingFilter(MIN_CHANGE, delta = ::shortestDelta)

        /** Last heading handed out, null before the first reading. */
        var heading: Float? = null
            private set

        /**
         * Calibration only concerns the magnetometer (the figure-eight motion). Its accuracy is
         * taken from every reading, not just accuracy callbacks, which come rarely and late.
         */
        val isCalibrated get() = magnetAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM

        /** Returns the new heading, or null while [ReadingFilter] holds it back. */
        fun onSensorChanged(event: SensorEvent): Float? {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(event.values, 0, gravity, 0, 3)
                    hasGravity = true
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                    hasGeomagnetic = true
                    magnetAccuracy = event.accuracy
                }
            }
            if (!hasGravity || !hasGeomagnetic) return null
            val azimuth = azimuth(gravity, geomagnetic) ?: return null
            if (!filter.accept(azimuth)) return null
            heading = azimuth
            return azimuth
        }

        fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) magnetAccuracy = accuracy
        }

        private companion object {
            /** Smaller changes are sensor noise; ignoring them keeps the dial steady. */
            const val MIN_CHANGE = 5f
        }
    }

    /** Whether GPS altitude may be read: precise location (approximate fixes have no altitude). */
    fun hasLocationPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Whether the device has GPS at all. */
    fun hasGps(context: Context) = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

    /** Whether location is switched on in the system (else no fixes come, though allowed). */
    fun isLocationEnabled(context: Context) =
        LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))

    /** Altitude above sea level in meters, ± [accuracy] when the fix tells it. */
    data class Altitude(val meters: Float, val accuracy: Float?)

    /** Tops of the arrow's altitude scale (m): the first one above the altitude is used. */
    val ALTITUDE_SCALES = floatArrayOf(100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f)

    /**
     * The arrow's scale for [meters] (index in [ALTITUDE_SCALES]): the first one reaching it. Shown
     * by the head's length (compass_arrow_head's level) and the fill's color.
     */
    fun altitudeScale(meters: Float) = ALTITUDE_SCALES.indexOfFirst { meters <= it }.let { if (it == -1) ALTITUDE_SCALES.lastIndex else it }

    /** The arrow head's size without an altitude (the usual head), in [altitudeScale] steps. */
    const val PLAIN_ARROW_HEAD = 3.75f

    /**
     * compass_arrow_head's level for a head the size of [scale] (an [altitudeScale], or between
     * two while it animates; [PLAIN_ARROW_HEAD] without an altitude).
     */
    fun arrowHeadLevel(scale: Float) = (scale * 12).roundToInt().coerceIn(0, 72)

    /**
     * How far up the arrow's scale (0..1, ticks in fifths; see [altitudeScale]) [altitude] reaches,
     * as the lowest and highest it may be (± its accuracy): the shaft is filled up to the first,
     * faintly up to the second.
     */
    fun altitudeBand(altitude: Altitude): Pair<Float, Float> {
        val top = ALTITUDE_SCALES[altitudeScale(altitude.meters)]
        val accuracy = altitude.accuracy ?: 0f
        return ((altitude.meters - accuracy) / top).coerceIn(0f, 1f) to ((altitude.meters + accuracy) / top).coerceIn(0f, 1f)
    }

    /**
     * Level of compass_altitude_fill / _band (0..10000, clipped from the bottom of the whole dial)
     * that colors the shaft (y 79.171..160.829 of 240) up to [fill].
     */
    fun altitudeLevel(fill: Float): Int {
        val bottom = (240f - 160.829f) / 240f
        val top = (240f - 79.171f) / 240f
        return ((bottom + (top - bottom) * fill) * 10000).roundToInt()
    }

    /** [altitude] as shown next to the heading (its accuracy is on the arrow, see [altitudeBand]). */
    fun altitudeText(context: Context, altitude: Altitude) =
        context.getString(R.string.f_compass_altitude_value, altitude.meters.roundToInt())

    /** What stands in the altitude's place: what's missing for it, or that it's coming / there. */
    enum class AltitudeStatus { Hidden, NoPermission, LocationOff, Searching, Known }

    /** [AltitudeStatus] by the settings and system state, given the [altitude] read so far. */
    fun altitudeStatus(context: Context, altitude: Altitude?) = when {
        !showAltitude(context) || !hasGps(context) -> AltitudeStatus.Hidden
        !hasLocationPermission(context) -> AltitudeStatus.NoPermission
        !isLocationEnabled(context) -> AltitudeStatus.LocationOff
        altitude == null -> AltitudeStatus.Searching
        else -> AltitudeStatus.Known
    }

    /**
     * GPS fixes turned into the altitude above sea level, for the page and the widget alike.
     * [onAltitude] gets each new one on the main thread, between [start] and [stop].
     */
    class AltitudeTracker(context: Context, private val onAltitude: (Altitude) -> Unit) : LocationListener {
        private val context = context.applicationContext
        private val locationManager = context.getSystemService(LocationManager::class.java)
        private val main = Handler(Looper.getMainLooper())
        private val filter = ReadingFilter(MIN_CHANGE, minIntervalMs = 0)
        private var running = false

        /** Last altitude handed out, null before the first fix. */
        var altitude: Altitude? = null
            private set

        /** Asks for fixes; false (and nothing comes) without the permission or GPS. Again after [stop]. */
        @SuppressLint("MissingPermission") // checked first
        fun start(): Boolean {
            if (running) return true
            if (!hasLocationPermission(context) || !hasGps(context)) return false
            running = true
            // GPS itself, and the fused fixes, which get an altitude from it sooner (e.g. with
            // assistance data) and keep giving it indoors for a while
            val providers = listOfNotNull(
                LocationManager.GPS_PROVIDER,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null
            ).filter { it in locationManager.allProviders }
            // A recent fix shows something right away, before GPS gets its own
            providers.mapNotNull { locationManager.getLastKnownLocation(it) }
                .filter { it.hasAltitude() && SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < LAST_FIX_MAX_AGE_NS }
                .maxByOrNull { it.elapsedRealtimeNanos }
                ?.let(::onLocationChanged)
            for (provider in providers) {
                locationManager.requestLocationUpdates(provider, INTERVAL_MS, 0f, this, Looper.getMainLooper())
            }
            return true
        }

        fun stop() {
            if (!running) return
            running = false
            locationManager.removeUpdates(this)
        }

        override fun onLocationChanged(location: Location) {
            if (!location.hasAltitude()) return
            // GPS measures above the ellipsoid, tens of meters off sea level in places; the
            // conversion reads a geoid map from disk, so off the main thread
            val fix = Location(location)
            converter.execute {
                try {
                    AltitudeConverterCompat.addMslAltitudeToLocation(context, fix)
                } catch (e: IOException) {
                    Log.w(TAG, "No sea level altitude, showing the GPS one", e)
                }
                main.post { if (running) publish(fix) }
            }
        }

        private fun publish(fix: Location) {
            val next = if (LocationCompat.hasMslAltitude(fix)) Altitude(
                LocationCompat.getMslAltitudeMeters(fix).toFloat(),
                if (LocationCompat.hasMslAltitudeAccuracy(fix)) LocationCompat.getMslAltitudeAccuracyMeters(fix) else null
            ) else Altitude(
                fix.altitude.toFloat(),
                if (fix.hasVerticalAccuracy()) fix.verticalAccuracyMeters else null
            )
            val previous = altitude
            val shown = when {
                filter.accept(next.meters) -> next
                // Same height, but the accuracy shown changed: that alone (the height kept steady)
                previous != null && next.accuracy?.roundToInt() != previous.accuracy?.roundToInt() -> previous.copy(accuracy = next.accuracy)
                else -> return
            }
            altitude = shown
            onAltitude(shown)
        }

        // Android 10 has no defaults for these: a provider switched on or off would crash the app
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}

        private companion object {
            const val TAG = "AltitudeTracker"
            const val INTERVAL_MS = 1000L
            /** GPS altitude wanders by a few meters; smaller changes would only flicker. */
            const val MIN_CHANGE = 1f
            const val LAST_FIX_MAX_AGE_NS = 2 * 60 * 1_000_000_000L

            /** One thread for every tracker, so fixes are converted in order. */
            val converter: ExecutorService = Executors.newSingleThreadExecutor()
        }
    }

    /**
     * Heading in degrees (0 = north, clockwise) from accelerometer and magnetometer readings,
     * or null when they can't be combined (e.g. the phone is in free fall).
     */
    private fun azimuth(gravity: FloatArray, geomagnetic: FloatArray): Float? {
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        if (!SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)) return null
        SensorManager.getOrientation(rotation, orientation)
        val az = Math.toDegrees(orientation[0].toDouble()).toFloat()
        return (az + 360) % 360
    }

    /** [degrees] in 0..360. */
    fun normalize(degrees: Float) = ((degrees % 360f) + 360f) % 360f

    /**
     * Signed rotation (-180..180) from [from] to [to]. Animations turn by this instead of going
     * straight to the new value, so crossing north (359° -> 1°) doesn't spin the dial around.
     */
    fun shortestDelta(from: Float, to: Float) = ((to - from) % 360f + 540f) % 360f - 180f
}
