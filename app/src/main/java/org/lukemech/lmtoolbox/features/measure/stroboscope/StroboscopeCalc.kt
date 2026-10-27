package org.lukemech.lmtoolbox.features.measure.stroboscope

import java.util.Locale

/**
 * The fastest the torch flashes (Hz). A torch is switched by a call to the camera service and its LED
 * driver, a few ms each way, and most phones keep up only to 10–20 Hz: past it, flashes are dropped or
 * run together. The rate it reaches is measured and shown, so a phone that can't is seen not to.
 */
const val MAX_HZ = 20.0

/** The slowest (Hz): slower, there's nothing to freeze. */
const val MIN_HZ = 0.5

fun rpm(hz: Double) = hz * 60

fun hzOfRpm(rpm: Double) = rpm / 60

/** [hz] as the torch can flash it ([MIN_HZ]..[MAX_HZ]). */
fun clampRate(hz: Double) = hz.coerceIn(MIN_HZ, MAX_HZ)

/** Each flash's part of a period: short, so what turns is caught in one place, not smeared. */
private const val DUTY = 0.3

/** The shortest flash (ns): shorter, the torch hardly lights before it's off. */
private const val MIN_ON_NS = 8_000_000L

/** How long the torch is on in each [periodNs]: [DUTY] of it, at least [MIN_ON_NS], at most half. */
fun flashOnTime(periodNs: Long): Long = (periodNs * DUTY).toLong().coerceAtLeast(MIN_ON_NS).coerceAtMost(periodNs / 2)

/** Taps further apart than this (ms) start a new tempo. */
const val TAP_GAP_MS = 2000L

/** How many of the last taps the tempo is the average of. */
private const val TAPS = 5

/**
 * The rate (Hz) of [taps] (their times, ms, oldest first): the average time between the last few, from the
 * last gap of more than [TAP_GAP_MS] on; null with fewer than two.
 */
fun tapTempo(taps: List<Long>): Double? {
    val start = (taps.lastIndex downTo 1).firstOrNull { taps[it] - taps[it - 1] > TAP_GAP_MS } ?: 0
    val recent = taps.drop(start).takeLast(TAPS)
    if (recent.size < 2) return null
    val span = recent.last() - recent.first()
    return if (span > 0) (recent.size - 1) * 1000.0 / span else null
}

/** [value] for the rate's field: up to two places, no trailing zeros (12.5, 300). */
fun fieldText(value: Double): String = "%.2f".format(Locale.ROOT, value).trimEnd('0').trimEnd('.')
