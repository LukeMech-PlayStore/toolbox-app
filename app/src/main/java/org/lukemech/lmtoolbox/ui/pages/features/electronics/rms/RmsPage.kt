package org.lukemech.lmtoolbox.ui.pages.features.electronics.rms

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Waves
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlin.math.PI
import kotlin.math.sqrt
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

/** A waveform's peak and average (rectified) values per its RMS value. */
private enum class Waveform(@param:StringRes val label: Int, val peak: Double, val average: Double) {
    Sine(R.string.ec_sine, sqrt(2.0), 2 * sqrt(2.0) / PI),
    Square(R.string.ec_square, 1.0, 1.0),
    Triangle(R.string.ec_triangle, sqrt(3.0), sqrt(3.0) / 2),
}

private enum class Level { Rms, Peak, PeakToPeak, Average }

@Composable
fun RmsPage() {
    var waveform by rememberSaveable { mutableStateOf(Waveform.Sine) }
    val fields = rememberLinkedFields(1, Level.Rms to "230")

    // Each of them is the RMS value times its factor
    fun factor(level: Level) = when (level) {
        Level.Rms -> 1.0
        Level.Peak -> waveform.peak
        Level.PeakToPeak -> 2 * waveform.peak
        Level.Average -> waveform.average
    }
    val values = fields.solve { given ->
        val (level, value) = given.entries.first()
        val rms = value / factor(level)
        Level.entries.associateWith { rms * factor(it) }
    }

    CalculatorPage(
        stringResource(R.string.fp_rms),
        formulas = listOf(
            Formula("""U_{p} = \sqrt{2} · U_{RMS}""", stringResource(R.string.ec_sine)),
            Formula("""U_{avg} = \frac{2\sqrt{2}}{π} · U_{RMS}"""),
            Formula("""U_{p} = U_{avg} = U_{RMS}""", stringResource(R.string.ec_square)),
            Formula("""U_{p} = \sqrt{3} · U_{RMS}""", stringResource(R.string.ec_triangle)),
            Formula("""U_{avg} = \frac{\sqrt{3}}{2} · U_{RMS}"""),
            Formula("""U_{pp} = 2 · U_{p}""", stringResource(R.string.ec_peak_to_peak)),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_waveform),
            icon = Icons.Default.Waves,
            selected = waveform,
            options = Waveform.entries.map { it to stringResource(it.label) },
            onSelect = { waveform = it }
        )
        LinkedField(fields, values, Level.Rms, stringResource(R.string.ec_rms_value), "V")
        LinkedField(fields, values, Level.Peak, stringResource(R.string.ec_peak), "V")
        LinkedField(fields, values, Level.PeakToPeak, stringResource(R.string.ec_peak_to_peak), "V")
        LinkedField(fields, values, Level.Average, stringResource(R.string.ec_avg), "V")
    }
}
