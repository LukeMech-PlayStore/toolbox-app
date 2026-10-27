package org.lukemech.lmtoolbox.ui.pages.features.electronics.battery_life

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import java.util.Locale
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.battery_life.BatteryLifeFeature
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.SectionTitle
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Battery { Capacity, Life }

@Composable
fun BatteryLifePage() {
    val fields = rememberLinkedFields(1, Battery.Capacity to "2000")
    var i1Text by rememberSaveable { mutableStateOf("100 m") }
    var t1Text by rememberSaveable { mutableStateOf("1") }
    var i2Text by rememberSaveable { mutableStateOf("1 m") }
    var t2Text by rememberSaveable { mutableStateOf("59") }

    val i1 = parseValue(i1Text)
    val t1 = parseValue(t1Text)
    val i2 = parseValue(i2Text)
    val t2 = parseValue(t2Text)

    // Average over a cycle: (I1·T1 + I2·T2) / (T1 + T2); the life in hours is the capacity (mAh) over it
    val avg = if (i1 != null && t1 != null && i2 != null && t2 != null && t1 + t2 > 0) (i1 * t1 + i2 * t2) / (t1 + t2) else null
    val values = fields.solve { given ->
        if (avg == null || avg <= 0) return@solve emptyMap()
        given[Battery.Capacity]?.let { mapOf(Battery.Life to it / 1000.0 / avg) }
            ?: given[Battery.Life]?.let { mapOf(Battery.Capacity to it * 1000.0 * avg) }
            ?: emptyMap()
    }
    val hours = values[Battery.Life]
    // The battery picked for its usual capacity: none (its own) once the capacity's another
    var picked by rememberSaveable { mutableIntStateOf(-1) }
    val typical = BatteryLifeFeature.TYPICAL
    if (picked >= 0 && values[Battery.Capacity] != typical[picked].mAh.toDouble()) picked = -1

    CalculatorPage(
        stringResource(R.string.fp_battery_life),
        formulas = listOf(
            Formula("""I_{avg} = \frac{I_{1} t_{1} + I_{2} t_{2}}{t_{1} + t_{2}}""", stringResource(R.string.ec_avg_current)),
            Formula("""t = \frac{C}{I_{avg}}""", stringResource(R.string.ec_life)),
        )
    ) {
        LinkedField(fields, values, Battery.Capacity, stringResource(R.string.ec_capacity), "mAh")
        LinkedField(fields, values, Battery.Life, stringResource(R.string.ec_life), "h")
        SectionTitle(stringResource(R.string.ec_stage1))
        UnitField(stringResource(R.string.ec_current), i1Text, { i1Text = it }, "A")
        UnitField(stringResource(R.string.ec_duration), t1Text, { t1Text = it }, "min")
        SectionTitle(stringResource(R.string.ec_stage2))
        UnitField(stringResource(R.string.ec_current), i2Text, { i2Text = it }, "A")
        UnitField(stringResource(R.string.ec_duration), t2Text, { t2Text = it }, "min")

        Results {
            if (avg != null) ResultRow(stringResource(R.string.ec_avg_current), formatValue(avg, "A"))
            if (hours != null && hours >= 48) ResultRow(stringResource(R.string.ec_life), String.format(Locale.US, "%.1f d", hours / 24))
        }
        Note(stringResource(R.string.ec_battery_note), visible = picked >= 0)
    }
}
