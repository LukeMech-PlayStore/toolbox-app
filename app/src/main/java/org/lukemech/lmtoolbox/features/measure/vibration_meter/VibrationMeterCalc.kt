package org.lukemech.lmtoolbox.features.measure.vibration_meter

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Standard gravity, m/s² in a g. */
const val STANDARD_GRAVITY = 9.80665

fun toG(ms2: Double) = ms2 / STANDARD_GRAVITY

/**
 * The accelerometer's last [capacity] readings (m/s², gravity taken out), with when they came (ns), the
 * oldest overwritten by the newest.
 */
class SampleWindow(val capacity: Int) {
    private val x = DoubleArray(capacity)
    private val y = DoubleArray(capacity)
    private val z = DoubleArray(capacity)
    private val t = LongArray(capacity)
    private var next = 0

    /** How many readings it holds (up to [capacity]). */
    var size = 0
        private set

    fun add(ax: Double, ay: Double, az: Double, timestampNs: Long) {
        x[next] = ax
        y[next] = ay
        z[next] = az
        t[next] = timestampNs
        next = (next + 1) % capacity
        size = minOf(size + 1, capacity)
    }

    fun clear() {
        next = 0
        size = 0
    }

    /** The last [n] (at most [size]) of an axis (0, 1, 2: x, y, z) or of the timestamps, oldest first. */
    fun axis(axis: Int, n: Int = size): DoubleArray {
        val source = when (axis) { 0 -> x; 1 -> y; else -> z }
        return DoubleArray(minOf(n, size)) { source[index(it, minOf(n, size))] }
    }

    fun timestamps(n: Int = size): LongArray = LongArray(minOf(n, size)) { t[index(it, minOf(n, size))] }

    private fun index(i: Int, n: Int) = ((next - n + i) % capacity + capacity) % capacity
}

/**
 * Gravity taken out of the accelerometer's readings where Android has no linear acceleration sensor: a
 * low-pass of each axis is gravity (what changes slowly), the rest is the motion (as Android's docs do it).
 */
class GravityFilter(private val alpha: Double = 0.8) {
    private val gravity = DoubleArray(3)
    private var started = false

    /** [values] (x, y, z, m/s²) with gravity taken out. */
    fun filter(values: FloatArray): DoubleArray {
        if (!started) {
            for (i in 0..2) gravity[i] = values[i].toDouble()
            started = true
        }
        return DoubleArray(3) { i ->
            gravity[i] = alpha * gravity[i] + (1 - alpha) * values[i]
            values[i] - gravity[i]
        }
    }
}

/** The RMS of the motion's size, √(mean of x² + y² + z²), over readings of each axis (the same count). */
fun rms(x: DoubleArray, y: DoubleArray, z: DoubleArray): Double {
    if (x.isEmpty()) return 0.0
    var sum = 0.0
    for (i in x.indices) sum += x[i] * x[i] + y[i] * y[i] + z[i] * z[i]
    return sqrt(sum / x.size)
}

/** The largest size of the motion, √(x² + y² + z²), among the readings. */
fun peak(x: DoubleArray, y: DoubleArray, z: DoubleArray): Double {
    var max = 0.0
    for (i in x.indices) max = maxOf(max, x[i] * x[i] + y[i] * y[i] + z[i] * z[i])
    return sqrt(max)
}

/** The fast Fourier transform of [re] + j·[im] in place (radix 2: their size a power of two). */
fun fft(re: DoubleArray, im: DoubleArray) {
    val n = re.size
    require(n == im.size && n > 0 && n and (n - 1) == 0) { "A power of two, both" }
    // Bit-reversed order
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
            j = j xor bit
            bit = bit shr 1
        }
        j = j xor bit
        if (i < j) {
            re[i] = re[j].also { re[j] = re[i] }
            im[i] = im[j].also { im[j] = im[i] }
        }
    }
    var len = 2
    while (len <= n) {
        val angle = -2 * PI / len
        val wRe = cos(angle)
        val wIm = sin(angle)
        for (start in 0 until n step len) {
            var uRe = 1.0
            var uIm = 0.0
            for (k in 0 until len / 2) {
                val a = start + k
                val b = a + len / 2
                val tRe = re[b] * uRe - im[b] * uIm
                val tIm = re[b] * uIm + im[b] * uRe
                re[b] = re[a] - tRe
                im[b] = im[a] - tIm
                re[a] += tRe
                im[a] += tIm
                val nextRe = uRe * wRe - uIm * wIm
                uIm = uRe * wIm + uIm * wRe
                uRe = nextRe
            }
        }
        len *= 2
    }
}

/** The largest power of two up to [n] (at least 1). */
fun powerOfTwoBelow(n: Int): Int = if (n < 1) 1 else Integer.highestOneBit(n)

/**
 * The frequency (Hz) the motion has most of, in [axes] (each the same power-of-two count of readings, taken
 * [sampleRate] a second): every axis's spectrum (mean taken out, Hann-windowed) added, so how the phone lies
 * doesn't matter and nothing doubles (as the size of the motion would: |sin| repeats twice as often). The
 * strongest bin, the lowest two left out (drift, the hand holding it), refined between its neighbors; null
 * for too few readings. It can't tell what's above half the [sampleRate].
 */
fun dominantFrequency(axes: List<DoubleArray>, sampleRate: Double): Double? {
    val n = axes.firstOrNull()?.size ?: return null
    if (n < 8) return null
    val window = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / (n - 1)) }
    val power = DoubleArray(n / 2 + 1)
    for (axis in axes) {
        val mean = axis.average()
        val re = DoubleArray(n) { (axis[it] - mean) * window[it] }
        val im = DoubleArray(n)
        fft(re, im)
        for (k in power.indices) power[k] += re[k] * re[k] + im[k] * im[k]
    }
    var best = 2
    for (k in 3 until power.size) if (power[k] > power[best]) best = k
    // A parabola through the peak and its neighbors: where between bins it lies
    val offset = if (best < power.size - 1) {
        val a = power[best - 1]
        val b = power[best]
        val c = power[best + 1]
        val d = a - 2 * b + c
        if (d != 0.0) (0.5 * (a - c) / d).coerceIn(-0.5, 0.5) else 0.0
    } else 0.0
    return (best + offset) * sampleRate / n
}
