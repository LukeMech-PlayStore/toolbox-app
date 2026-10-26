package org.lukemech.lmtoolbox.ui.pages.features.reference.capacitor_code

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatNumber
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.reference.capacitor_code.parseCapacitorCode
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CodeField

/** A capacitor's marking typed, read as its capacitance (in pF, nF and μF too) and its tolerance. */
@Composable
fun CapacitorCodeDecoder() {
    var code by rememberSaveable { mutableStateOf("104K") }
    val result = parseCapacitorCode(code)

    CodeField(stringResource(R.string.rf_marking), code) { code = it }
    Results {
        if (result != null) {
            MainResult(stringResource(R.string.ec_capacitance), formatValue(result.farads, "F"))
            ResultRow("pF", formatNumber(result.farads * 1e12, 6))
            ResultRow("nF", formatNumber(result.farads * 1e9, 6))
            ResultRow("μF", formatNumber(result.farads * 1e6, 6))
            result.tolerance?.let { ResultRow(stringResource(R.string.ec_tolerance), it) }
        }
    }
    Note(stringResource(R.string.ec_invalid_code), visible = result == null && code.isNotBlank())
    Note(stringResource(R.string.rf_cap_code_hint))
}
