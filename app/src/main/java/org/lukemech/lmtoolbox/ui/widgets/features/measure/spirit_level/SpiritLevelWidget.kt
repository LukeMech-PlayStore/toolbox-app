package org.lukemech.lmtoolbox.ui.widgets.features.measure.spirit_level

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.measure.spirit_level.SpiritLevelFeature
import org.lukemech.lmtoolbox.ui.widgets.helpers.FeatureWidget
import org.lukemech.lmtoolbox.ui.widgets.helpers.LiveWidgetService
import org.lukemech.lmtoolbox.ui.widgets.helpers.WidgetSize
import kotlin.math.min
import kotlin.math.roundToInt
import org.lukemech.lmtoolbox.ui.pages.features.measure.spirit_level.tiltFraction
import org.lukemech.lmtoolbox.ui.pages.features.measure.spirit_level.isNearCenter
import org.lukemech.lmtoolbox.ui.pages.features.measure.spirit_level.BUBBLE_TRAVEL

/**
 * Spirit level on the home screen, on a transparent background. Tapping it switches it on:
 * [SpiritLevelWidgetService] then keeps the accelerometer running and moves the bubble like the
 * page does. Tapping it again switches it off (dimmed, bubble in the middle), both with an
 * animation like the compass. Below it, while on, the tilt; red while the readings may be off.
 *
 * The layers are the page's (res-features/measure/spirit_level/drawable/level_*). The launcher draws and
 * tints them; the bubble is moved by its margins (Android 12+; it stays in the middle before).
 */
class SpiritLevelWidget : FeatureWidget(SpiritLevelFeature) {

    /** Everything a frame sets; only what changed since the last one is sent. */
    private data class Look(
        val dx: Int,
        val dy: Int,
        val ringColor: Int,
        val crossColor: Int,
        val bubbleColor: Int,
        val alpha: Int,
        val status: String,
        val statusColor: Int,
    )

    /** Looks last sent by [updateFrame], per instance (this instance is the live service's). */
    private val sent = mutableMapOf<Int, Look>()

    override fun render(context: Context, size: WidgetSize): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.w_level)
        applyBackground(context, views)
        views.setOnClickPendingIntent(
            R.id.w_level_dial,
            if (SpiritLevelFeature.hasSensors(context)) LiveWidgetService.toggleIntent(context, SpiritLevelWidgetService::class.java)
            else openFeatureIntent(context)
        )
        views.setOnClickPendingIntent(R.id.w_level_status, openFeatureIntent(context))
        apply(views, look(context, size, SpiritLevelWidgetService.frame), null)
        return views
    }

    /** Sends only what changed since the last frame. */
    fun updateFrame(context: Context, frame: SpiritLevelWidgetService.Frame?) {
        partiallyUpdateEach(context) { id, size ->
            val look = look(context, size, frame)
            val previous = sent[id]
            if (look == previous) return@partiallyUpdateEach null
            sent[id] = look
            RemoteViews(context.packageName, R.layout.w_level).also { apply(it, look, previous) }
        }
    }

    private fun look(context: Context, size: WidgetSize, frame: SpiritLevelWidgetService.Frame?): Look {
        val colors = colorScheme(context)
        val lowAccuracy = frame != null && !frame.calibrated
        val ringColor = if (lowAccuracy) colors.error else lerp(colors.outline, colors.primary, frame?.ringProgress ?: 0f)
        // Off: dimmed (fading in and out as it's switched)
        val fade = frame?.fade ?: 1f
        val alpha = 1f + (OFF_ALPHA - 1f) * fade

        // Only while on (fading in and out with the dial); off, the space stays so the dial keeps its size
        val status = when (frame) {
            null if !SpiritLevelFeature.hasSensors(context) -> context.getString(R.string.f_missing_sensors_title)
            null -> ""
            else -> context.getString(R.string.w_level_values, frame.roll, frame.pitch)
        }
        val statusColor = (if (lowAccuracy) colors.error else colors.onBackground)
            .let { it.copy(alpha = it.alpha * (1f - (frame?.fade ?: 0f))) }

        // The dial's side (the layers are square, fitted into the space above the text)
        val density = context.resources.displayMetrics.density
        val sideDp = min(size.widthDp - PADDING_DP * 2, size.heightDp - PADDING_TOP_DP - PADDING_BOTTOM_DP - TEXT_DP)
        val travel = sideDp * density * BUBBLE_TRAVEL

        return Look(
            // The bubble moves against the tilt horizontally, with it vertically (as on the page)
            dx = (-(frame?.x ?: 0f) * travel).roundToInt(),
            dy = ((frame?.y ?: 0f) * travel).roundToInt(),
            ringColor = ringColor.toArgb(),
            crossColor = colors.outline.toArgb(),
            bubbleColor = colors.tertiary.toArgb(),
            alpha = (alpha * 255).roundToInt(),
            status = status,
            statusColor = statusColor.toArgb(),
        )
    }

    private fun apply(views: RemoteViews, look: Look, previous: Look?) {
        fun changed(value: (Look) -> Any) = previous == null || value(previous) != value(look)

        // Shifted by opposite margins on both sides, so the bubble keeps its size
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val px = TypedValue.COMPLEX_UNIT_PX
            if (changed { it.dx }) {
                views.setViewLayoutMargin(R.id.w_level_bubble, RemoteViews.MARGIN_LEFT, look.dx.toFloat(), px)
                views.setViewLayoutMargin(R.id.w_level_bubble, RemoteViews.MARGIN_RIGHT, -look.dx.toFloat(), px)
            }
            if (changed { it.dy }) {
                views.setViewLayoutMargin(R.id.w_level_bubble, RemoteViews.MARGIN_TOP, look.dy.toFloat(), px)
                views.setViewLayoutMargin(R.id.w_level_bubble, RemoteViews.MARGIN_BOTTOM, -look.dy.toFloat(), px)
            }
        }
        if (changed { it.ringColor }) views.setInt(R.id.w_level_ring, "setColorFilter", look.ringColor)
        if (changed { it.crossColor }) views.setInt(R.id.w_level_cross, "setColorFilter", look.crossColor)
        if (changed { it.bubbleColor }) views.setInt(R.id.w_level_bubble, "setColorFilter", look.bubbleColor)
        if (changed { it.alpha }) {
            for (layer in intArrayOf(R.id.w_level_ring, R.id.w_level_cross, R.id.w_level_bubble)) {
                views.setInt(layer, "setImageAlpha", look.alpha)
            }
        }
        // Its view has a fixed size, so a new text doesn't move the dial
        if (changed { it.status }) views.setTextViewText(R.id.w_level_status, look.status)
        if (changed { it.statusColor }) views.setTextColor(R.id.w_level_status, look.statusColor)
    }

    // Last instance removed
    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, SpiritLevelWidgetService::class.java))
    }

    private companion object {
        const val OFF_ALPHA = 0.4f

        /** From res-features/measure/spirit_level/layout/w_level.xml: padding, and the text with its margin. */
        const val PADDING_DP = 10
        const val PADDING_TOP_DP = 10
        const val PADDING_BOTTOM_DP = 8
        const val TEXT_DP = 28
    }
}

/** Keeps the spirit level widget's bubble moving with the accelerometer while it's on. */
class SpiritLevelWidgetService : LiveWidgetService() {

    /**
     * What the widget shows while live: the bubble ([x], [y] as tilt fractions), how centered the
     * ring's color is, and the tilt in degrees for the text; [fade] goes from 0 (on) to 1 (off,
     * dimmed) as it's switched.
     */
    data class Frame(
        val x: Float, val y: Float, val ringProgress: Float, val roll: Float, val pitch: Float, val calibrated: Boolean,
        val fade: Float = 1f,
    )

    override val widget = SpiritLevelWidget()
    override val notificationId = 3002
    override val notificationIcon: Int = R.drawable.cyclone
    override val notificationTitle: Int = R.string.w_level_live
    override val notificationText: Int = R.string.w_level_live_desc
    override val sensorTypes = intArrayOf(Sensor.TYPE_ACCELEROMETER)

    // Shared with the page, so both show the same tilt and accuracy state
    private val state = SpiritLevelFeature.SensorState()

    private val x = Axis()
    private val y = Axis()
    private var ring = 0f
    private var fade = 1f
    private var lastFrameAt = 0L

    // Switched off: back to how it looks off (bubble in the middle, dimmed) first
    override fun stoppingMs() = BUBBLE_ANIMATION_NS / 1_000_000 + 50

    /** One coordinate of the bubble, animated towards its target like animateFloatAsState. */
    private class Axis {
        var shown = 0f
        private var from = 0f
        private var to = 0f
        private var startedAt = 0L

        fun update(target: Float, now: Long) {
            if (target != to) {
                from = shown
                to = target
                startedAt = now
            }
            val progress = ((now - startedAt) / BUBBLE_ANIMATION_NS.toFloat()).coerceIn(0f, 1f)
            shown = from + (to - from) * LinearOutSlowInEasing.transform(progress)
        }
    }

    override fun onLiveStart() {
        frame = Frame(0f, 0f, 0f, 0f, 0f, true)
        lastFrameAt = System.nanoTime()
    }

    override fun onLiveStop() {
        frame = null
    }

    // Sensors only set the target; frames move the bubble towards it
    override fun onReading(event: SensorEvent) {
        state.onSensorChanged(event)
    }

    override fun onReadingAccuracy(sensor: Sensor, accuracy: Int) {
        state.onAccuracyChanged(sensor, accuracy)
    }

    /**
     * Moves the bubble like the page's animations (400ms, LinearOutSlowIn per axis; the ring's
     * color fades in 300ms), sending only what changed.
     */
    override fun onFrame(frameTimeNanos: Long) {
        // Going off: back to the middle, as it stands while off
        x.update(if (isStopping) 0f else tiltFraction(state.roll), frameTimeNanos)
        y.update(if (isStopping) 0f else tiltFraction(state.pitch), frameTimeNanos)

        // By the time passed, so a late frame doesn't slow the fades down
        // Never back in time: the first display frame may be stamped before the start (System.nanoTime)
        val passed = (frameTimeNanos - lastFrameAt).coerceAtLeast(0)
        val ringStep = passed / RING_ANIMATION_NS.toFloat()
        val centered = !isStopping && isNearCenter(x.shown, y.shown)
        ring = (ring + if (centered) ringStep else -ringStep).coerceIn(0f, 1f)
        // Fading in when switched on, out when off
        val fadeStep = passed / BUBBLE_ANIMATION_NS.toFloat()
        fade = (fade + if (isStopping) fadeStep else -fadeStep).coerceIn(0f, 1f)
        lastFrameAt = frameTimeNanos

        val next = Frame(x.shown, y.shown, ring, state.roll, state.pitch, state.isCalibrated, fade)
        frame = next
        widget.updateFrame(this, next)
    }

    companion object {
        private const val BUBBLE_ANIMATION_NS = 400_000_000L
        private const val RING_ANIMATION_NS = 300_000_000L

        /** Current frame while live, null while switched off. */
        @Volatile var frame: Frame? = null
            private set
    }
}
