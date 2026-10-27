package org.lukemech.lmtoolbox.features.electronics.bjt

/** What a transistor switching a load takes at its base. */
internal data class Switch(
    /** Collector current: the load's. */
    val ic: Double,
    /** Base current, the overdrive factor times what the gain alone needs. */
    val ib: Double,
    /** Base resistor. */
    val rb: Double,
    /** Heat in the transistor (VCE(sat)·IC, the base's VBE·IB on top). */
    val transistorPower: Double,
    /** Heat in the base resistor. */
    val rbPower: Double,
)

/** The load's current from its resistance: what's left of [vcc] past VCE(sat), through [rLoad]. */
internal fun loadCurrent(vcc: Double, rLoad: Double, vceSat: Double) = (vcc - vceSat) / rLoad

/**
 * The base resistor that saturates a load of [ic] from a drive of [vDrive]: IB = k·IC / hFE, RB =
 * (Udrive − VBE) / IB. Null where the drive can't open the base.
 */
internal fun bjtSwitch(ic: Double, hfe: Double, overdrive: Double, vDrive: Double, vbe: Double, vceSat: Double): Switch? {
    if (ic <= 0 || hfe <= 0 || overdrive <= 0 || vDrive <= vbe) return null
    val ib = overdrive * ic / hfe
    val rb = (vDrive - vbe) / ib
    return Switch(ic, ib, rb, vceSat * ic + vbe * ib, ib * ib * rb)
}

internal enum class Region { Cutoff, Active, Saturation }

/** A transistor's operating point (Q-point): its voltages to ground and currents. */
internal data class QPoint(val vb: Double, val ve: Double, val vc: Double, val ib: Double, val ic: Double, val region: Region) {
    val vce get() = vc - ve
}

/** The collector–emitter voltage below which the transistor is taken as saturated. */
internal const val VCE_SAT = 0.2

/**
 * Voltage-divider bias: the divider's Thévenin equivalent (UTh = Vcc·R2 / (R1 + R2), RTh = R1 ∥ R2) drives the
 * base, IB = (UTh − VBE) / (RTh + (β + 1)·RE). Saturated (VCE under [VCE_SAT]), the collector current is what
 * RC and RE let through instead; with UTh below VBE, nothing flows.
 */
internal fun dividerBias(vcc: Double, r1: Double, r2: Double, rc: Double, re: Double, beta: Double, vbe: Double): QPoint {
    val vth = vcc * r2 / (r1 + r2)
    val rth = r1 * r2 / (r1 + r2)
    if (vth <= vbe) return QPoint(vth, 0.0, vcc, 0.0, 0.0, Region.Cutoff)
    val ib = (vth - vbe) / (rth + (beta + 1) * re)
    val ic = beta * ib
    val ve = (ic + ib) * re
    val vc = vcc - ic * rc
    if (vc - ve >= VCE_SAT) return QPoint(ve + vbe, ve, vc, ib, ic, Region.Active)
    // Saturated: VCE stays at VCE(sat), the current the resistors allow (the base's share in RE left out)
    val icSat = (vcc - VCE_SAT) / (rc + re)
    val veSat = icSat * re
    return QPoint(veSat + vbe, veSat, veSat + VCE_SAT, ((vth - vbe - veSat) / rth).coerceAtLeast(0.0), icSat, Region.Saturation)
}
