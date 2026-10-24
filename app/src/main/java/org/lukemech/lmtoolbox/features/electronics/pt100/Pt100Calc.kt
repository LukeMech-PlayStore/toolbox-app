package org.lukemech.lmtoolbox.features.electronics.pt100

import kotlin.math.sqrt

// Callendar–Van Dusen (IEC 60751): R(t) = R0·(1 + A·t + B·t² + C·(t − 100)·t³), the C term below 0 °C only
internal const val A = 3.9083e-3

internal const val B = -5.775e-7

internal const val C = -4.183e-12

internal fun resistanceAt(t: Double, r0: Double): Double =
    r0 * (1 + A * t + B * t * t + (if (t < 0) C * (t - 100) * t * t * t else 0.0))

/** The temperature at [r]: the quadratic's root from 0 °C up, Newton's method below. */
internal fun temperatureAt(r: Double, r0: Double): Double {
    var t = (-A + sqrt(A * A - 4 * B * (1 - r / r0))) / (2 * B)
    if (r < r0) repeat(20) {
        val slope = r0 * (A + 2 * B * t + C * (4 * t * t * t - 300 * t * t))
        t -= (resistanceAt(t, r0) - r) / slope
    }
    return t
}
