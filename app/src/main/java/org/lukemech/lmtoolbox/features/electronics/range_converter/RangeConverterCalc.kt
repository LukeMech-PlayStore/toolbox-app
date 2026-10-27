package org.lukemech.lmtoolbox.features.electronics.range_converter

/** A signal range: [lo] to [hi] in [unit] (none for a custom one). */
internal data class SignalRange(val lo: Double, val hi: Double, val unit: String)

/** The usual ranges of instruments and controllers; [Custom] keeps what's typed. */
internal enum class RangePreset(val range: SignalRange?) {
    Current4To20(SignalRange(4.0, 20.0, "mA")),
    Current0To20(SignalRange(0.0, 20.0, "mA")),
    Voltage0To10(SignalRange(0.0, 10.0, "V")),
    Voltage0To5(SignalRange(0.0, 5.0, "V")),
    Voltage1To5(SignalRange(1.0, 5.0, "V")),
    Percent(SignalRange(0.0, 100.0, "%")),
    Custom(null),
}

/** [x] from [lo]–[hi] mapped onto [toLo]–[toHi], linearly (outside the range too); null for an empty range. */
internal fun mapRange(x: Double, lo: Double, hi: Double, toLo: Double, toHi: Double): Double? =
    if (hi == lo) null else toLo + (x - lo) * (toHi - toLo) / (hi - lo)
