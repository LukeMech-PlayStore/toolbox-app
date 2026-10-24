package org.lukemech.lmtoolbox.ui.pages.features.electronics.ohm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.ESeries
import org.lukemech.lmtoolbox.features.electronics.common.fieldText
import org.lukemech.lmtoolbox.features.electronics.ohm.Ohm
import org.lukemech.lmtoolbox.features.electronics.ohm.solveOhm
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.ESeriesSetting
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Nearest
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

@Composable
fun OhmPage() {
    val fields = rememberLinkedFields(2, Ohm.I to "10 m", Ohm.R to "1 k")
    var series by remember { mutableStateOf(ESeries.E24) }
    val values = fields.solve(::solveOhm)

    CalculatorPage(
        stringResource(R.string.fp_ohm),
        formulas = listOf(
            Formula("""U = I · R""", stringResource(R.string.fp_ohm)),
            Formula("""P = U · I = I^{2} · R = \frac{U^{2}}{R}""", stringResource(R.string.ec_power)),
        )
    ) {
        LinkedField(fields, values, Ohm.U, stringResource(R.string.ec_voltage), "V")
        LinkedField(fields, values, Ohm.I, stringResource(R.string.ec_current), "A")
        LinkedField(fields, values, Ohm.R, stringResource(R.string.ec_resistance), "Ω")
        ESeriesSetting(series, listOf(Nearest(values[Ohm.R]) { fields.edit(Ohm.R, fieldText(it, "Ω")) })) { series = it }
        LinkedField(fields, values, Ohm.P, stringResource(R.string.ec_power), "W")
    }
}
