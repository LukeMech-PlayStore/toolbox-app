package org.lukemech.lmtoolbox.ui.pages.features.electronics.smd_code

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.smd_code.parseSmdCode
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CodeField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula

@Composable
fun SmdCodePage() {
    var code by rememberSaveable { mutableStateOf("103") }
    val result = parseSmdCode(code)

    CalculatorPage(
        stringResource(R.string.fp_smd_code),
        formulas = listOf(
            Formula("""R = (10a + b) · 10^{c}""", "abc  (103 = 10 kΩ)"),
            Formula("""R = (100a + 10b + c) · 10^{d}""", "abcd  (1002 = 10 kΩ)"),
            Formula("""R = a.b \text{ Ω}""", "aRb  (4R7 = 4.7 Ω)"),
            Formula("""R = \text{E96}[ab] · 100 · M(x)""", "EIA-96: abx  (01C = 10 kΩ)"),
        )
    ) {
        CodeField(stringResource(R.string.ec_smd_code), code) { code = it }
        Results {
            if (result != null) MainResult(stringResource(R.string.ec_resistance), formatValue(result, "Ω"))
        }
        Note(stringResource(R.string.ec_invalid_code), visible = result == null && code.isNotBlank())
    }
}
