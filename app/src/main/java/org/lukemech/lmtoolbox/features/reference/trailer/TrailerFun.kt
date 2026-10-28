package org.lukemech.lmtoolbox.features.reference.trailer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RvHookup
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Ground
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Power
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Signal
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Unused
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.Wire
import org.lukemech.lmtoolbox.features.reference.common.inRow
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** Trailer sockets: Europe's 7-pin (ISO 1724) and 13-pin (ISO 11446), North America's 4-pin flat one. */
object TrailerFeature : ReferenceFeature() {

    override val route = "trailer"
    override val titleStringResId: Int = R.string.fp_trailer
    override val keywordsResId: Int = R.string.fk_trailer
    override val descriptionResId: Int = R.string.fd_trailer
    override val icon @Composable get() = Icons.Default.RvHookup

    // The lights both have on pins 1–7, each with its circuit's number (DIN 72552) and its wire's color
    private fun lights(ground: Int): List<Pin> = listOf(
        light(1, "L", R.string.rf_trailer_left_indicator, Wire.Yellow),
        light(2, "54G", R.string.rf_trailer_fog, Wire.Blue),
        Pin("3", "31", Ground, res(ground), wire = listOf(Wire.White)),
        light(4, "R", R.string.rf_trailer_right_indicator, Wire.Green),
        light(5, "58R", R.string.rf_trailer_right_tail, Wire.Brown),
        light(6, "54", R.string.rf_trailer_brake, Wire.Red),
        light(7, "58L", R.string.rf_trailer_left_tail, Wire.Black),
    )

    private fun light(n: Int, name: String, description: Int, color: Color) =
        Pin(n.toString(), name, Signal, res(description), wire = listOf(color))

    override val sheets by lazy {
        val europe = res(R.string.rf_trailer_europe)
        listOf(
            PinoutSheet(
                raw("7-pin (12N)"),
                lights(R.string.rf_trailer_ground).inRow(),
                note = res(R.string.rf_trailer_7_note),
                inOrder = true,
                group = europe
            ),
            PinoutSheet(
                raw("13-pin"),
                (lights(R.string.rf_trailer_ground_1_8) + listOf(
                    light(8, "8", R.string.rf_trailer_reverse, Wire.Grey),
                    Pin("9", "9", Power, res(R.string.rf_trailer_permanent)),
                    Pin("10", "10", Power, res(R.string.rf_trailer_ignition)),
                    Pin("11", "11", Ground, res(R.string.rf_trailer_ground_10)),
                    Pin("12", "12", Unused, res(R.string.rf_trailer_unassigned)),
                    Pin("13", "13", Ground, res(R.string.rf_trailer_ground_9)),
                )).inRow(),
                note = res(R.string.rf_trailer_13_note),
                inOrder = true,
                group = europe
            ),
            // Its pins in a row as they are, the ground one on the car's side the socket's
            PinoutSheet(
                raw("4-pin flat"),
                listOf(
                    Pin("1", "GND", Ground, res(R.string.rf_trailer_ground), wire = listOf(Wire.White)),
                    light(2, "TAIL", R.string.rf_trailer_tail, Wire.Brown),
                    light(3, "LT", R.string.rf_trailer_left_stop, Wire.Yellow),
                    light(4, "RT", R.string.rf_trailer_right_stop, Wire.Green),
                ).inRow(),
                note = res(R.string.rf_trailer_4_note),
                group = res(R.string.rf_trailer_america)
            ),
        )
    }
}
