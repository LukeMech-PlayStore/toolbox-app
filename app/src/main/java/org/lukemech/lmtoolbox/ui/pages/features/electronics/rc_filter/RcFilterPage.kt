package org.lukemech.lmtoolbox.ui.pages.features.electronics.rc_filter

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.util.Locale
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sqrt
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Rc { R, C, F }

@Composable
fun RcFilterPage() {
    val fields = rememberLinkedFields(2, Rc.R to "1 k", Rc.C to "100 n")
    // fc = 1 / (2π·R·C), any of them from the other two
    val values = fields.solve { given ->
        val missing = Rc.entries.first { it !in given }
        mapOf(missing to 1.0 / (2.0 * PI * given.values.reduce(Double::times)))
    }
    val fc = values[Rc.F]

    CalculatorPage(
        stringResource(R.string.fp_rc_filter),
        formulas = listOf(
            Formula("""f_{c} = \frac{1}{2πRC}""", stringResource(R.string.ec_cutoff)),
            Formula("""A = 20 · \text{log}_{10} \frac{1}{\sqrt{1 + (f/f_{c})^{2}}}"""),
        )
    ) {
        LinkedField(fields, values, Rc.R, stringResource(R.string.ec_resistance), "Ω")
        LinkedField(fields, values, Rc.C, stringResource(R.string.ec_capacitance), "F")
        LinkedField(fields, values, Rc.F, stringResource(R.string.ec_cutoff), "Hz")
        Results {
            if (fc != null && fc > 0) {
                listOf(0.1, 0.316, 1.0, 3.16, 10.0).forEach { ratio ->
                    val db = 20.0 * log10(1.0 / sqrt(1.0 + ratio * ratio))
                    ResultRow(formatValue(fc * ratio, "Hz"), String.format(Locale.US, "%.2f dB", db))
                }
            }
        }
    }
}
