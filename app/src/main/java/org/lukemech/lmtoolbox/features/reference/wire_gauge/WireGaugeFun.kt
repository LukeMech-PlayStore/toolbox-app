package org.lukemech.lmtoolbox.features.reference.wire_gauge

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.runtime.Composable
import java.util.Locale
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.voltage_drop.awgDiameterMm
import org.lukemech.lmtoolbox.features.electronics.voltage_drop.crossSectionMm2
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** Wire gauges: AWG from its formula (the voltage drop calculator's) with NEC ampacities, and the British SWG. */
object WireGaugeFeature : ReferenceFeature() {

    override val route = "wire_gauge"
    override val titleStringResId: Int = R.string.fp_wire_gauge
    override val keywordsResId: Int = R.string.fk_wire_gauge
    override val descriptionResId: Int = R.string.fd_wire_gauge
    override val icon @Composable get() = Icons.Default.LinearScale

    /**
     * NEC table 310.16's ampacity (A) of copper at 60 °C and 75 °C insulation, by AWG (−3 is 4/0): up to three
     * current-carrying conductors in a raceway or cable, 30 °C around them. Smaller sizes aren't in it.
     */
    private val NEC_COPPER = mapOf(
        14 to (15 to 20), 12 to (20 to 25), 10 to (30 to 35), 8 to (40 to 50), 6 to (55 to 65), 4 to (70 to 85),
        3 to (85 to 100), 2 to (95 to 115), 1 to (110 to 130), 0 to (125 to 150), -1 to (145 to 175),
        -2 to (165 to 200), -3 to (195 to 230),
    )

    private fun awgName(n: Int) = if (n > 0) n.toString() else "${"0".repeat(1 - n)} (${1 - n}/0)"

    /** BS 3737's Standard Wire Gauge: the gauge and its diameter in inches (7/0 is −6). */
    private val SWG = listOf(
        -6 to 0.500, -5 to 0.464, -4 to 0.432, -3 to 0.400, -2 to 0.372, -1 to 0.348, 0 to 0.324,
        1 to 0.300, 2 to 0.276, 3 to 0.252, 4 to 0.232, 5 to 0.212, 6 to 0.192, 7 to 0.176, 8 to 0.160,
        9 to 0.144, 10 to 0.128, 11 to 0.116, 12 to 0.104, 13 to 0.092, 14 to 0.080, 15 to 0.072,
        16 to 0.064, 17 to 0.056, 18 to 0.048, 19 to 0.040, 20 to 0.036, 21 to 0.032, 22 to 0.028,
        23 to 0.024, 24 to 0.022, 25 to 0.020, 26 to 0.018, 27 to 0.0164, 28 to 0.0148, 29 to 0.0136,
        30 to 0.0124, 31 to 0.0116, 32 to 0.0108, 33 to 0.0100, 34 to 0.0092, 35 to 0.0084, 36 to 0.0076,
        37 to 0.0068, 38 to 0.0060, 39 to 0.0052, 40 to 0.0048,
    )

    private fun f(format: String, value: Double) = raw(String.format(Locale.US, format, value))

    override val sheets by lazy {
        listOf(
            TableSheet(
                raw("AWG"),
                columns = listOf(raw("AWG"), raw("Ø mm"), raw("mm²"), res(R.string.rf_ampacity_60), res(R.string.rf_ampacity_75)),
                rows = (-3..40).map { n ->
                    val d = awgDiameterMm(n.toDouble())
                    val ampacity = NEC_COPPER[n]
                    listOf(
                        raw(awgName(n)), f("%.3f", d), f(if (d > 1) "%.2f" else "%.4f", crossSectionMm2(d)),
                        raw(ampacity?.first?.let { "$it A" } ?: "—"), raw(ampacity?.second?.let { "$it A" } ?: "—"),
                    )
                },
                note = res(R.string.rf_awg_note)
            ),
            TableSheet(
                raw("SWG"),
                columns = listOf(raw("SWG"), raw("Ø in"), raw("Ø mm"), raw("mm²")),
                rows = SWG.map { (n, inches) ->
                    val d = inches * 25.4
                    listOf(
                        raw(if (n >= 0) n.toString() else "${1 - n}/0"), raw(inches.toString()), f("%.3f", d),
                        f(if (d > 1) "%.2f" else "%.4f", crossSectionMm2(d)),
                    )
                },
                note = res(R.string.rf_swg_note)
            ),
        )
    }
}
