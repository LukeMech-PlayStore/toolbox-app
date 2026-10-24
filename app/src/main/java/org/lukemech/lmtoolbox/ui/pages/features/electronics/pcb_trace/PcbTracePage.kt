package org.lukemech.lmtoolbox.ui.pages.features.electronics.pcb_trace

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import java.util.Locale
import kotlin.math.pow
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private const val MM_PER_MIL = 0.0254

private enum class Trace { Current, Width }

@Composable
fun PcbTracePage() {
    var thickOz by rememberSaveable { mutableIntStateOf(1) }
    var internal by rememberSaveable { mutableStateOf(false) }
    var tRiseText by rememberSaveable { mutableStateOf("10") }
    val fields = rememberLinkedFields(1, Trace.Current to "1")

    val dT = parseValue(tRiseText)?.takeIf { it > 0 }
    // IPC-2221: I = k·ΔT^0.44·A^0.725 (I in A, A in mil²), A = W·thickness
    val thicknessMil = 1.378 * thickOz
    val k = if (internal) 0.024 else 0.048
    val values = fields.solve { given ->
        if (dT == null) return@solve emptyMap()
        val perArea = k * dT.pow(0.44)
        given[Trace.Current]?.takeIf { it > 0 }?.let { mapOf(Trace.Width to (it / perArea).pow(1 / 0.725) / thicknessMil * MM_PER_MIL) }
            ?: given[Trace.Width]?.takeIf { it > 0 }?.let { mapOf(Trace.Current to perArea * (it / MM_PER_MIL * thicknessMil).pow(0.725)) }
            ?: emptyMap()
    }
    val width = values[Trace.Width]

    CalculatorPage(
        stringResource(R.string.fp_pcb_trace),
        formulas = listOf(
            Formula("""I = k · ΔT^{0.44} · A^{0.725}""", "IPC-2221"),
            Formula("""W = \frac{A}{t_{Cu}}""", stringResource(R.string.ec_width)),
            Formula("""k = 0.048""", stringResource(R.string.ec_external)),
            Formula("""k = 0.024""", stringResource(R.string.ec_internal)),
            Formula("""t_{Cu} = 1.378 \text{ mil/oz}""", stringResource(R.string.ec_copper)),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_copper),
            icon = Icons.Default.Layers,
            selected = thickOz,
            options = listOf(1 to stringResource(R.string.ec_1oz), 2 to stringResource(R.string.ec_2oz)),
            onSelect = { thickOz = it }
        )
        OptionSetting(
            title = stringResource(R.string.ec_layer),
            icon = Icons.Default.FlipToBack,
            selected = internal,
            options = listOf(false to stringResource(R.string.ec_external), true to stringResource(R.string.ec_internal)),
            onSelect = { internal = it }
        )
        UnitField(stringResource(R.string.ec_t_rise), tRiseText, { tRiseText = it }, "°C")
        LinkedField(fields, values, Trace.Current, stringResource(R.string.ec_current), "A")
        LinkedField(fields, values, Trace.Width, stringResource(R.string.ec_width), "mm", sigFigs = 3)

        Results {
            if (width != null) ResultRow(stringResource(R.string.ec_width_mil), String.format(Locale.US, "%.1f mil", width / MM_PER_MIL))
        }
    }
}
