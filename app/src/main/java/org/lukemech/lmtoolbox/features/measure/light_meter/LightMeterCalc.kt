package org.lukemech.lmtoolbox.features.measure.light_meter

import androidx.annotation.StringRes
import kotlin.math.ln
import org.lukemech.lmtoolbox.R

/** Lux in a foot-candle (a lumen per square foot). */
const val LUX_PER_FOOT_CANDLE = 10.7639

fun footCandles(lux: Double) = lux / LUX_PER_FOOT_CANDLE

/** A light everyone knows ([name]), from [lowLux] to [highLux] (the same for a typical value), to hold a reading against. */
class LightReference(@param:StringRes val name: Int, val lowLux: Double, val highLux: Double = lowLux)

/** Typical illuminances, darkest first (as Wikipedia's "Lux" lists them). */
val LIGHT_REFERENCES = listOf(
    LightReference(R.string.f_light_ref_moonlight, 0.05, 0.3),
    LightReference(R.string.f_light_ref_living_room, 50.0),
    LightReference(R.string.f_light_ref_office, 320.0, 500.0),
    LightReference(R.string.f_light_ref_overcast, 1000.0),
    LightReference(R.string.f_light_ref_shade, 10_000.0, 25_000.0),
    LightReference(R.string.f_light_ref_sunlight, 32_000.0, 100_000.0),
)

/**
 * Which of [references] (darkest first) [lux] is like: the one it's within, else the nearest, by ratio (the
 * eye judges light so: 10 lx from 1 is as far as 1000 from 100).
 */
fun referenceFor(lux: Double, references: List<LightReference> = LIGHT_REFERENCES): Int {
    // A dark room's 0 lx is darker than any
    val value = maxOf(lux, 1e-4)
    return references.indices.minBy { i ->
        val ref = references[i]
        when {
            value < ref.lowLux -> ln(ref.lowLux / value)
            value > ref.highLux -> ln(value / ref.highLux)
            else -> 0.0
        }
    }
}
