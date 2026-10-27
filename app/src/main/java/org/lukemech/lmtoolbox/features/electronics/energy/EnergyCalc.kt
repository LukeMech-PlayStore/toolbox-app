package org.lukemech.lmtoolbox.features.electronics.energy

/**
 * Units of energy, each as joules ([joules]): the calorie is the thermochemical one (4.184 J), the BTU the
 * International Table's (1055.05585262 J), the electronvolt exact since the SI's 2019 redefinition.
 */
internal enum class EnergyUnit(val symbol: String, val joules: Double) {
    J("J", 1.0),
    KJ("kJ", 1e3),
    MJ("MJ", 1e6),
    Wh("Wh", 3600.0),
    KWh("kWh", 3.6e6),
    Cal("cal", 4.184),
    Kcal("kcal", 4184.0),
    Btu("BTU", 1055.05585262),
    EV("eV", 1.602176634e-19),
}

/** [value] in [from] as [to]. */
internal fun convertEnergy(value: Double, from: EnergyUnit, to: EnergyUnit) = value * from.joules / to.joules

/** A battery's energy (Wh) from its charge ([mAh]) at its nominal [volts]. */
internal fun wattHours(mAh: Double, volts: Double) = mAh * volts / 1000.0
