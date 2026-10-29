package org.lukemech.lmtoolbox.ui.widgets.helpers

import androidx.compose.animation.core.LinearOutSlowInEasing

/*
 * A live widget's animations, frame by frame (its service draws each frame itself, see LiveWidgetService.onFrame):
 * as the page's animate*AsState would move the same things.
 */

/**
 * A value easing towards its target over [durationNs], as the page's animateFloatAsState does (LinearOutSlowIn): a new
 * target starts from where it is. [normalize] keeps what's [shown] in range (an angle's 0-360).
 */
class Glide(private val durationNs: Long, private val normalize: (Float) -> Float = { it }) {
    var shown = 0f
        private set
    private var from = 0f
    private var to = 0f
    private var target: Float? = null
    private var startedAt = 0L

    /**
     * Towards [target] (null: the one it has) at frame time [now]; [delta] tells how far that is from what's shown (an
     * angle's shortest way round). What's shown now.
     */
    fun update(target: Float?, now: Long, delta: (from: Float, to: Float) -> Float = { a, b -> b - a }): Float {
        if (target != null && target != this.target) {
            this.target = target
            from = shown
            to = shown + delta(shown, target)
            startedAt = now
        }
        val progress = ((now - startedAt) / durationNs.toFloat()).coerceIn(0f, 1f)
        shown = normalize(from + (to - from) * LinearOutSlowInEasing.transform(progress))
        return shown
    }
}

/** [value] moved towards [target] by what [passedNs] allow, [perUnitNs] for each 1 of change (a fade, a fill). */
fun stepTowards(value: Float, target: Float, passedNs: Long, perUnitNs: Long): Float {
    val max = passedNs / perUnitNs.toFloat()
    return value + (target - value).coerceIn(-max, max)
}

/** How long since the last frame: by the time passed, so a late frame doesn't slow the fades down. */
class FrameClock {
    private var last = 0L

    /** From now (as the widget goes live). */
    fun start() {
        last = System.nanoTime()
    }

    /** Ns since the last frame, [now] the new one's time; never back in time (the first frame may be stamped before [start]). */
    fun passed(now: Long): Long = (now - last).coerceAtLeast(0).also { last = now }
}
