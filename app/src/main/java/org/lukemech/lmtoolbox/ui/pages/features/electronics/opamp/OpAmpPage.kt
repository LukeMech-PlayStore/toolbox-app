package org.lukemech.lmtoolbox.ui.pages.features.electronics.opamp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Amp { R1, R2, Gain }

private enum class Signal { Vin, Vout }

@Composable
fun OpAmpPage() {
    var inverting by rememberSaveable { mutableStateOf(false) }
    val amp = rememberLinkedFields(2, Amp.R1 to "10 k", Amp.R2 to "100 k")
    val signal = rememberLinkedFields(1, Signal.Vin to "100 m")

    // Non-inverting: G = 1 + R2/R1; inverting: G = −R2/R1 (a gain typed in counts as its size)
    val ampValues = amp.solve { given ->
        val r1 = given[Amp.R1]
        val r2 = given[Amp.R2]
        // R2/R1 for the gain typed in
        val ratio = given[Amp.Gain]?.let { if (inverting) abs(it) else abs(it) - 1.0 }?.takeIf { it > 0 }
        when {
            r1 != null && r2 != null -> mapOf(Amp.Gain to if (inverting) -r2 / r1 else 1.0 + r2 / r1)
            r1 != null && ratio != null -> mapOf(Amp.R2 to r1 * ratio)
            r2 != null && ratio != null -> mapOf(Amp.R1 to r2 / ratio)
            else -> emptyMap()
        }
    }
    val gain = ampValues[Amp.Gain]?.let { if (inverting) -abs(it) else abs(it) }
    val signalValues = signal.solve { given ->
        if (gain == null || gain == 0.0) return@solve emptyMap()
        given[Signal.Vin]?.let { mapOf(Signal.Vout to gain * it) }
            ?: given[Signal.Vout]?.let { mapOf(Signal.Vin to it / gain) }
            ?: emptyMap()
    }

    CalculatorPage(
        stringResource(R.string.fp_opamp),
        formulas = listOf(
            Formula("""G = 1 + \frac{R_{2}}{R_{1}}""", stringResource(R.string.ec_non_inverting)),
            Formula("""G = −\frac{R_{2}}{R_{1}}""", stringResource(R.string.ec_inverting)),
            Formula("""U_{out} = G · U_{in}""", stringResource(R.string.ec_vout)),
            Formula("""G_{dB} = 20 · \text{log}_{10} |G|""", stringResource(R.string.ec_gain_db)),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_mode),
            icon = Icons.Default.Tune,
            selected = inverting,
            options = listOf(false to stringResource(R.string.ec_non_inverting), true to stringResource(R.string.ec_inverting)),
            onSelect = { inverting = it }
        )
        LinkedField(amp, ampValues, Amp.R1, stringResource(R.string.ec_r1), "Ω")
        LinkedField(amp, ampValues, Amp.R2, stringResource(R.string.ec_r2), "Ω")
        LinkedField(amp, ampValues, Amp.Gain, stringResource(R.string.ec_gain))
        LinkedField(signal, signalValues, Signal.Vin, stringResource(R.string.ec_vin), "V")
        LinkedField(signal, signalValues, Signal.Vout, stringResource(R.string.ec_vout), "V")
        Results {
            if (gain != null && gain != 0.0) {
                ResultRow(stringResource(R.string.ec_gain_db), String.format(Locale.US, "%.2f dB", 20 * log10(abs(gain))))
            }
        }
    }
}
