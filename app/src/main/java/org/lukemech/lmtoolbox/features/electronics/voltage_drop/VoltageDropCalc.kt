package org.lukemech.lmtoolbox.features.electronics.voltage_drop

import kotlin.math.PI
import kotlin.math.pow

/** An AWG wire's diameter (mm): 0.127 mm · 92^((36 − AWG) / 39); 0 is 1/0, −3 is 4/0. */
internal fun awgDiameterMm(awg: Double) = 0.127 * 92.0.pow((36 - awg) / 39.0)

/** A round wire's cross-section (mm²) from its diameter (mm). */
internal fun crossSectionMm2(diameterMm: Double) = PI * diameterMm * diameterMm / 4
