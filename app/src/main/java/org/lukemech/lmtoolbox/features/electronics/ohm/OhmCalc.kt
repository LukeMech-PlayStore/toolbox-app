package org.lukemech.lmtoolbox.features.electronics.ohm

import kotlin.math.sqrt

internal enum class Ohm { U, I, R, P }

/** U and I from any two of U, I, R, P; R and P from those. */
internal fun solveOhm(given: Map<Ohm, Double>): Map<Ohm, Double> {
    val u = given[Ohm.U]
    val i = given[Ohm.I]
    val r = given[Ohm.R]
    val p = given[Ohm.P]
    val (voltage, current) = when {
        u != null && i != null -> u to i
        u != null && r != null -> u to u / r
        u != null && p != null -> u to p / u
        i != null && r != null -> i * r to i
        i != null && p != null -> p / i to i
        r != null && p != null -> sqrt(p * r) to sqrt(p / r)
        else -> return emptyMap()
    }
    return mapOf(Ohm.U to voltage, Ohm.I to current, Ohm.R to voltage / current, Ohm.P to voltage * current)
}
