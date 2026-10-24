package org.lukemech.lmtoolbox.ui.pages.features.electronics.coil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlin.math.sqrt
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Coil { Turns, Inductance }

@Composable
fun CoilPage() {
    var dText by rememberSaveable { mutableStateOf("10") }
    var hText by rememberSaveable { mutableStateOf("20") }
    val fields = rememberLinkedFields(1, Coil.Turns to "50")

    val d = parseValue(dText)?.takeIf { it > 0 }
    val h = parseValue(hText)?.takeIf { it > 0 }
    // Wheeler's formula: L(μH) = d²·n² / (18d + 40h), d and h in inches
    val values = fields.solve { given ->
        if (d == null || h == null) return@solve emptyMap()
        val dIn = d / 25.4
        val hIn = h / 25.4
        val perTurn = dIn * dIn / (18.0 * dIn + 40.0 * hIn) * 1e-6
        given[Coil.Turns]?.let { mapOf(Coil.Inductance to it * it * perTurn) }
            ?: given[Coil.Inductance]?.takeIf { it > 0 }?.let { mapOf(Coil.Turns to sqrt(it / perTurn)) }
            ?: emptyMap()
    }

    CalculatorPage(
        stringResource(R.string.fp_coil),
        formulas = listOf(
            Formula("""L = \frac{d^{2} · n^{2}}{457.2d + 1016h} \text{ μH}""", "${stringResource(R.string.ec_inductance)} (d, h [mm])"),
        )
    ) {
        UnitField(stringResource(R.string.ec_core_d), dText, { dText = it }, "mm")
        UnitField(stringResource(R.string.ec_height), hText, { hText = it }, "mm")
        LinkedField(fields, values, Coil.Turns, stringResource(R.string.ec_turns), sigFigs = 3)
        LinkedField(fields, values, Coil.Inductance, stringResource(R.string.ec_inductance), "H")
    }
}
