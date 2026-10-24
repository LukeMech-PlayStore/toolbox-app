package org.lukemech.lmtoolbox.helpers

import android.os.SystemClock
import kotlin.math.abs

/**
 * Smooths a sensor value the same way for every feature (compass, spirit level): a new reading
 * is taken only when it moved more than [tolerance] since the last one taken, and at least
 * [minIntervalMs] after it. One per value (e.g. per spirit level axis), so each keeps its own
 * tolerance. The tolerance and the interval keep sensor noise from shaking the display; the
 * animations towards each taken reading do the rest.
 *
 * [delta] measures the change (e.g. the short way around for angles).
 */
class ReadingFilter(
    private val tolerance: Float,
    private val minIntervalMs: Long = DEFAULT_INTERVAL_MS,
    private val delta: (from: Float, to: Float) -> Float = { from, to -> to - from },
) {
    private var last: Float? = null
    private var takenAt = 0L

    /** Whether to take [value] (then remembered as the last reading). */
    fun accept(value: Float, now: Long = SystemClock.uptimeMillis()): Boolean {
        val previous = last
        if (previous != null) {
            if (now - takenAt < minIntervalMs) return false
            if (abs(delta(previous, value)) <= tolerance) return false
        }
        last = value
        takenAt = now
        return true
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 100L
    }
}
