package org.lukemech.lmtoolbox.ui.widgets.features.measure.compass

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.measure.compass.CompassFeature
import org.lukemech.lmtoolbox.features.measure.compass.CompassFeature.AltitudeStatus
import org.lukemech.lmtoolbox.ui.widgets.helpers.FeatureWidget
import org.lukemech.lmtoolbox.ui.widgets.helpers.LiveWidgetService
import org.lukemech.lmtoolbox.ui.widgets.helpers.WidgetSize
import kotlin.math.roundToInt
import org.lukemech.lmtoolbox.ui.pages.features.measure.compass.isDirectionAligned
import org.lukemech.lmtoolbox.ui.pages.features.measure.compass.bandColor
import org.lukemech.lmtoolbox.ui.pages.features.measure.compass.altitudeColor
import org.lukemech.lmtoolbox.ui.pages.features.measure.compass.WarningColor
import org.lukemech.lmtoolbox.ui.widgets.helpers.Glide
import org.lukemech.lmtoolbox.ui.widgets.helpers.FrameClock
import org.lukemech.lmtoolbox.ui.widgets.helpers.stepTowards

/**
 * Compass on the home screen, on a transparent background. Tapping the dial switches it on:
 * [CompassWidgetService] then keeps the sensors running and turns it like the page does.
 * Tapping it again switches it off (dimmed, pointing north), both with an animation. Below it,
 * while on, the heading and the GPS altitude (see the compass settings), or in amber what it's missing
 * (location); red while the compass needs calibrating. The arrow carries the altitude's scale, its
 * shaft colored up to it like on the page.
 *
 * The dial is the page's: the same vector layers (res/drawable/compass_*), which the launcher
 * turns (w_compass_*_rotate, by image level) and tints itself, so a frame is just a few numbers.
 */
class CompassWidget : FeatureWidget(CompassFeature) {

    /** Everything a frame sets; only what changed since the last one is sent. */
    private data class Look(
        val level: Int,
        val ringColor: Int,
        val arrowColor: Int,
        val scaleColor: Int,
        val northColor: Int,
        val alpha: Int,
        val status: String,
        val statusColor: Int,
        /** Level of compass_altitude_fill, null without an altitude (no scale then). */
        val altitudeLevel: Int?,
        val fillColor: Int,
        val bandColor: Int,
        /** Of the altitude's ticks, fading in with the altitude and out when switched off. */
        val ticksAlpha: Int,
        /** Level of compass_altitude_band: as far as the altitude may be off. */
        val bandLevel: Int,
        /** Level of compass_arrow_head: the altitude's scale, or the usual head. */
        val headLevel: Int,
    )

    /** Look last sent by [updateFrame] (this instance is the live service's). */
    private var sent: Look? = null

    override fun render(context: Context, size: WidgetSize): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.w_compass)
        applyBackground(context, views)
        views.setOnClickPendingIntent(R.id.w_compass_status, openFeatureIntent(context))
        views.setOnClickPendingIntent(
            R.id.w_compass_dial,
            if (CompassFeature.hasSensors(context)) LiveWidgetService.toggleIntent(context, CompassWidgetService::class.java) else openFeatureIntent(context)
        )
        apply(views, look(context, CompassWidgetService.frame), null)
        return views
    }

    /** Sends only what changed since the last frame (usually just the dial's turn). */
    fun updateFrame(context: Context, frame: CompassWidgetService.Frame?) {
        val look = look(context, frame)
        if (look == sent) return
        val views = RemoteViews(context.packageName, R.layout.w_compass)
        apply(views, look, sent)
        partiallyUpdateAll(context, views)
        sent = look
    }

    private fun look(context: Context, frame: CompassWidgetService.Frame?): Look {
        val colors = colorScheme(context)
        val uncalibrated = frame != null && !frame.calibrated
        val arrowColor = if (uncalibrated) colors.error else colors.outline
        // The ring also lights up while the arrow points at a cardinal direction, as on the page
        val ringColor = if (uncalibrated) colors.error else lerp(colors.outline, colors.tertiary, frame?.ringProgress ?: 0f)
        // Off: dimmed (fading in and out as it's switched)
        val alpha = 1f + (OFF_ALPHA - 1f) * (frame?.fade ?: 1f)

        // The altitude next to the heading, or what it's missing
        val altitude = when (frame?.altitudeStatus) {
            AltitudeStatus.Known -> frame.altitude?.let { CompassFeature.altitudeText(context, it) }
            AltitudeStatus.Searching -> context.getString(R.string.f_compass_altitude_searching)
            AltitudeStatus.NoPermission -> "!!!"
            AltitudeStatus.LocationOff -> "!"
            else -> null
        }
        val altitudeMissing = frame?.altitudeStatus == AltitudeStatus.NoPermission || frame?.altitudeStatus == AltitudeStatus.LocationOff

        // Only while on (fading in and out with the dial); off, the space stays so the dial keeps its size.
        // Uncalibrated, only its color tells (as the arrow and ring do)
        val status = when {
            frame == null && !CompassFeature.hasSensors(context) -> context.getString(R.string.f_missing_sensors_title)
            frame == null -> ""
            frame.heading == null -> listOfNotNull("…", altitude).joinToString(" · ")
            else -> listOfNotNull(context.getString(R.string.w_compass_heading, frame.heading), altitude).joinToString(" · ")
        }
        val statusColor = when {
            uncalibrated -> colors.error
            altitudeMissing -> WarningColor
            else -> colors.onBackground
        }.let { it.copy(alpha = it.alpha * (1f - (frame?.fade ?: 0f))) }

        return Look(
            level = ((frame?.azimuth ?: 0f) / 360f * 10000).roundToInt(),
            ringColor = ringColor.toArgb(),
            arrowColor = arrowColor.toArgb(),
            scaleColor = colors.onBackground.toArgb(),
            northColor = colors.primary.toArgb(),
            alpha = (alpha * 255).roundToInt(),
            status = status,
            statusColor = statusColor.toArgb(),
            altitudeLevel = frame?.altitudeFill?.let(CompassFeature::altitudeLevel),
            ticksAlpha = ((frame?.ticksAlpha ?: 0f) * 255).roundToInt(),
            bandLevel = CompassFeature.altitudeLevel(frame?.altitudeBand ?: 0f),
            fillColor = altitudeColor(frame?.headScale ?: CompassFeature.PLAIN_ARROW_HEAD).toArgb(),
            bandColor = bandColor(altitudeColor(frame?.headScale ?: CompassFeature.PLAIN_ARROW_HEAD), colors.onBackground).toArgb(),
            headLevel = CompassFeature.arrowHeadLevel(frame?.headScale ?: CompassFeature.PLAIN_ARROW_HEAD),
        )
    }

    private fun apply(views: RemoteViews, look: Look, previous: Look?) {
        fun changed(value: (Look) -> Any?) = previous == null || value(previous) != value(look)

        if (changed { it.level }) {
            views.setInt(R.id.w_compass_scale, "setImageLevel", look.level)
            views.setInt(R.id.w_compass_north, "setImageLevel", look.level)
        }
        if (changed { it.ringColor }) views.setInt(R.id.w_compass_ring, "setColorFilter", look.ringColor)
        if (changed { it.arrowColor }) views.setInt(R.id.w_compass_arrow_shaft, "setColorFilter", look.arrowColor)
        if (changed { it.scaleColor }) views.setInt(R.id.w_compass_scale, "setColorFilter", look.scaleColor)
        if (changed { it.northColor }) views.setInt(R.id.w_compass_north, "setColorFilter", look.northColor)
        if (changed { it.alpha }) {
            for (layer in intArrayOf(R.id.w_compass_ring, R.id.w_compass_arrow_shaft, R.id.w_compass_scale, R.id.w_compass_north)) {
                views.setInt(layer, "setImageAlpha", look.alpha)
            }
        }
        // The heading changes in 5° steps, not per frame; its view has a fixed size, so a new
        // text doesn't move the dial
        if (changed { it.status }) views.setTextViewText(R.id.w_compass_status, look.status)
        if (changed { it.statusColor }) views.setTextColor(R.id.w_compass_status, look.statusColor)

        // The altitude's scale on the arrow (in its color), the shaft colored up to the altitude
        if (changed { it.altitudeLevel == null }) {
            val visibility = if (look.altitudeLevel == null) View.GONE else View.VISIBLE
            views.setViewVisibility(R.id.w_compass_altitude_ticks, visibility)
            views.setViewVisibility(R.id.w_compass_altitude_fill, visibility)
            views.setViewVisibility(R.id.w_compass_altitude_band, visibility)
        }
        look.altitudeLevel?.let { level ->
            if (changed { it.altitudeLevel }) views.setInt(R.id.w_compass_altitude_fill, "setImageLevel", level)
        }
        if (changed { it.arrowColor }) views.setInt(R.id.w_compass_altitude_ticks, "setColorFilter", look.arrowColor)
        if (changed { it.ticksAlpha }) views.setInt(R.id.w_compass_altitude_ticks, "setImageAlpha", look.ticksAlpha)
        if (changed { it.fillColor }) {
            views.setInt(R.id.w_compass_altitude_fill, "setColorFilter", look.fillColor)
        }
        if (changed { it.bandColor }) views.setInt(R.id.w_compass_altitude_band, "setColorFilter", look.bandColor)
        // In its own color, as on the page; fading with the ticks
        if (changed { it.bandLevel }) views.setInt(R.id.w_compass_altitude_band, "setImageLevel", look.bandLevel)
        if (changed { it.ticksAlpha }) {
            views.setInt(R.id.w_compass_altitude_band, "setImageAlpha", look.ticksAlpha)
        }
        // Its length tells the scale
        if (changed { it.headLevel }) views.setInt(R.id.w_compass_arrow_head, "setImageLevel", look.headLevel)
        if (changed { it.arrowColor }) views.setInt(R.id.w_compass_arrow_head, "setColorFilter", look.arrowColor)
        if (changed { it.alpha }) views.setInt(R.id.w_compass_arrow_head, "setImageAlpha", look.alpha)
    }

    // Last instance removed
    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, CompassWidgetService::class.java))
    }

    private companion object {
        const val OFF_ALPHA = 0.4f
    }
}

/** Keeps the compass widget turning with the sensors while it's on, and its altitude following GPS. */
class CompassWidgetService : LiveWidgetService() {

    /**
     * What the widget shows while live; [heading] (the text) is null until the first reading,
     * [altitude] (meters) until the first GPS fix, see [altitudeStatus]; [altitudeFill] is how far
     * up the arrow's scale it shows (animated), null once the scale is gone. [fade] goes from 0
     * (on) to 1 (off, dimmed) as it's switched.
     */
    data class Frame(
        val azimuth: Float,
        val heading: Float?,
        val calibrated: Boolean,
        val altitude: CompassFeature.Altitude? = null,
        val altitudeStatus: AltitudeStatus = AltitudeStatus.Hidden,
        val altitudeFill: Float? = null,
        /** How far up the scale the altitude may be (± its accuracy), animated like [altitudeFill]. */
        val altitudeBand: Float = 0f,
        /** The arrow head's size (CompassFeature.arrowHeadLevel), animated towards the altitude's scale. */
        val headScale: Float = CompassFeature.PLAIN_ARROW_HEAD,
        val ticksAlpha: Float = 0f,
        val fade: Float = 1f,
        /** How lit up the ring is (0..1): the arrow pointing at a cardinal direction. */
        val ringProgress: Float = 0f,
    )

    override val widget = CompassWidget()
    override val notificationId = 3001
    override val notificationIcon: Int = R.drawable.explore
    override val notificationTitle: Int = R.string.w_compass_live
    override val notificationText: Int = R.string.w_compass_live_desc
    override val sensorTypes = intArrayOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD)

    // Shared with the page, so both show the same heading, calibration state and altitude
    private val state = CompassFeature.SensorState()
    private val altitude by lazy { CompassFeature.AltitudeTracker(this) {} }

    /** The dial's heading, as the page turns it (500ms, LinearOutSlowIn), the shortest way round. */
    private val dial = Glide(ANIMATION_NS, CompassFeature::normalize)
    private val clock = FrameClock()
    private var altitudeStatus = AltitudeStatus.Hidden
    private var altitudeCheckedAt = 0L
    private var fill = 0f
    private var band = 0f
    private var headScale = CompassFeature.PLAIN_ARROW_HEAD
    private var ticksAlpha = 0f
    private var fade = 1f
    private var ring = 0f

    // Switched off: back to how it looks off (north up, dimmed, no altitude scale) first
    override fun stoppingMs() = ANIMATION_NS / 1_000_000 + 50

    // GPS for the altitude, when shown and allowed
    override fun extraServiceTypes() =
        if (CompassFeature.showAltitude(this) && CompassFeature.hasLocationPermission(this)) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0

    override fun onLiveStart() {
        frame = Frame(0f, null, true)
        clock.start()
        followAltitude()
    }

    // The altitude setting or location access changed in the app
    override fun onRefresh() = followAltitude()

    private fun followAltitude() {
        if (hasExtraServiceTypes) altitude.start() else altitude.stop()
        // Checked again on the next frame
        altitudeCheckedAt = 0L
    }

    override fun onLiveStop() {
        altitude.stop()
        frame = null
    }

    // Sensors only set the target; frames turn the dial towards it
    override fun onReading(event: SensorEvent) {
        state.onSensorChanged(event)
    }

    override fun onReadingAccuracy(sensor: Sensor, accuracy: Int) {
        state.onAccuracyChanged(sensor, accuracy)
    }

    /**
     * Turns the dial towards the heading like the page's animations (500ms, LinearOutSlowIn),
     * sending only what changed.
     */
    override fun onFrame(frameTimeNanos: Long) {
        // Going off: back to north, as it stands while off
        val shown = dial.update(if (isStopping) 0f else state.heading, frameTimeNanos, CompassFeature::shortestDelta)
        val passed = clock.passed(frameTimeNanos)

        // Settings and location state change rarely: checked once a second, not every frame
        val reading = altitude.altitude
        if (frameTimeNanos - altitudeCheckedAt > ALTITUDE_CHECK_NS || (reading != null && altitudeStatus == AltitudeStatus.Searching)) {
            altitudeStatus = CompassFeature.altitudeStatus(this, reading).let {
                // Allowed, but not yet for this service (see refresh)
                if (!hasExtraServiceTypes && (it == AltitudeStatus.Searching || it == AltitudeStatus.Known)) AltitudeStatus.NoPermission else it
            }
            altitudeCheckedAt = frameTimeNanos
        }

        // Fading in when switched on, out when off
        fade = stepTowards(fade, if (isStopping) 1f else 0f, passed, ANIMATION_NS)
        // The ring lit up at a cardinal direction (not when going back to north while switching off)
        ring = stepTowards(ring, if (!isStopping && isDirectionAligned(shown)) 1f else 0f, passed, ANIMATION_NS)

        // Up from the tail towards the altitude, as fast as on the page (a whole scale a second),
        // and down again without one (or going off)
        val known = altitudeStatus == AltitudeStatus.Known && reading != null && !isStopping
        val levels = if (known) CompassFeature.altitudeBand(reading) else 0f to 0f
        fill = stepTowards(fill, levels.first, passed, FILL_NS)
        band = stepTowards(band, levels.second, passed, FILL_NS)
        ticksAlpha = stepTowards(ticksAlpha, if (known) 1f else 0f, passed, ANIMATION_NS)
        // The head grows or shrinks to the scale (back to the usual one without), its color along
        val headTarget = if (known) CompassFeature.altitudeScale(reading.meters).toFloat() else CompassFeature.PLAIN_ARROW_HEAD
        headScale = stepTowards(headScale, headTarget, passed, HEAD_NS)

        val next = Frame(
            // The text keeps the last heading while the dial goes back to north
            shown, state.heading, state.isCalibrated, reading, altitudeStatus,
            if (fill > 0f || band > 0f || ticksAlpha > 0f) fill else null,
            band,
            headScale, ticksAlpha, fade, ring
        )
        frame = next
        widget.updateFrame(this, next)
    }

    companion object {
        private const val ANIMATION_NS = 500_000_000L
        private const val ALTITUDE_CHECK_NS = 1_000_000_000L
        private const val FILL_NS = 1_000_000_000L
        /** Time the head takes to change by one scale. */
        private const val HEAD_NS = 150_000_000L

        /** Current frame while live, null while switched off. */
        @Volatile var frame: Frame? = null
            private set

        /** Lets the service, while on, follow a change of the altitude setting or location access. */
        fun refresh(context: Context) {
            if (frame != null) refresh(context, CompassWidgetService::class.java)
        }
    }
}
