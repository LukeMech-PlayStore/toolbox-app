package org.lukemech.lmtoolbox.ui.pages.features.electronics.bjt

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.bjt.QPoint
import org.lukemech.lmtoolbox.features.electronics.bjt.Region
import org.lukemech.lmtoolbox.features.electronics.bjt.bjtSwitch
import org.lukemech.lmtoolbox.features.electronics.bjt.dividerBias
import org.lukemech.lmtoolbox.features.electronics.bjt.loadCurrent
import org.lukemech.lmtoolbox.features.electronics.common.ESeries
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.ESeriesSetting
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField

@Composable
fun BjtPage() {
    var bias by rememberSaveable { mutableStateOf(false) }

    CalculatorPage(
        stringResource(R.string.fp_bjt),
        formulas = listOf(
            Formula("""I_{B} = k · \frac{I_{C}}{h_{FE}}""", stringResource(R.string.ec_bjt_switch)),
            Formula("""R_{B} = \frac{U_{drive} − U_{BE}}{I_{B}}"""),
            Formula("""I_{C} = \frac{U_{CC} − U_{CE(sat)}}{R_{L}}"""),
            Formula("""U_{Th} = U_{CC} · \frac{R_{2}}{R_{1} + R_{2}}""", stringResource(R.string.ec_bjt_bias)),
            Formula("""R_{Th} = \frac{R_{1} · R_{2}}{R_{1} + R_{2}}"""),
            Formula("""I_{B} = \frac{U_{Th} − U_{BE}}{R_{Th} + (β + 1) · R_{E}}"""),
            Formula("""U_{CE} = U_{CC} − I_{C} · R_{C} − I_{E} · R_{E}"""),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_mode),
            icon = Icons.Default.Tune,
            selected = bias,
            options = listOf(false to stringResource(R.string.ec_bjt_switch), true to stringResource(R.string.ec_bjt_bias)),
            onSelect = { bias = it }
        )
        RevealSection(!bias) { SwitchSection() }
        RevealSection(bias) { BiasSection() }
    }
}

/** The base resistor that saturates a switched load. */
@Composable
private fun SwitchSection() {
    var vccText by rememberSaveable { mutableStateOf("12") }
    var byResistance by rememberSaveable { mutableStateOf(false) }
    var icText by rememberSaveable { mutableStateOf("100 m") }
    var rlText by rememberSaveable { mutableStateOf("120") }
    var driveText by rememberSaveable { mutableStateOf("5") }
    var hfeText by rememberSaveable { mutableStateOf("100") }
    var overdriveText by rememberSaveable { mutableStateOf("3") }
    var vbeText by rememberSaveable { mutableStateOf("0.7") }
    var vceSatText by rememberSaveable { mutableStateOf("0.2") }
    var series by remember { mutableStateOf(ESeries.E24) }

    val vcc = parseValue(vccText)
    val vceSat = parseValue(vceSatText) ?: 0.0
    val ic = if (byResistance) {
        val rl = parseValue(rlText)
        if (vcc != null && rl != null && rl > 0) loadCurrent(vcc, rl, vceSat) else null
    } else parseValue(icText)
    val drive = parseValue(driveText)
    val vbe = parseValue(vbeText)
    val hfe = parseValue(hfeText)
    val overdrive = parseValue(overdriveText)
    val result = if (ic != null && hfe != null && overdrive != null && drive != null && vbe != null) {
        bjtSwitch(ic, hfe, overdrive, drive, vbe, vceSat)
    } else null

    UnitField(stringResource(R.string.ec_vcc), vccText, { vccText = it }, "V")
    OptionSetting(
        title = stringResource(R.string.ec_load),
        icon = Icons.Default.Power,
        selected = byResistance,
        options = listOf(false to stringResource(R.string.ec_iout), true to stringResource(R.string.ec_rl)),
        onSelect = { byResistance = it }
    )
    RevealSection(!byResistance) { UnitField(stringResource(R.string.ec_iout), icText, { icText = it }, "A") }
    RevealSection(byResistance) { UnitField(stringResource(R.string.ec_rl), rlText, { rlText = it }, "Ω") }
    UnitField(stringResource(R.string.ec_drive), driveText, { driveText = it }, "V")
    UnitField(stringResource(R.string.ec_hfe), hfeText, { hfeText = it })
    UnitField(stringResource(R.string.ec_overdrive), overdriveText, { overdriveText = it })
    UnitField(stringResource(R.string.ec_vbe), vbeText, { vbeText = it }, "V")
    UnitField(stringResource(R.string.ec_vce_sat), vceSatText, { vceSatText = it }, "V")
    ESeriesSetting(series) { series = it }

    Results {
        if (result != null) {
            MainResult(stringResource(R.string.ec_base_resistor), formatValue(result.rb, "Ω"))
            // The standard value at or under it: a little more base current, still saturated
            ESeries.valuesIn(series, result.rb / 10, result.rb * 1.0001).lastOrNull()?.let {
                ResultRow(stringResource(R.string.ec_rb_standard, series.name), formatValue(it, "Ω"))
            }
            ResultRow(stringResource(R.string.ec_ic), formatValue(result.ic, "A"))
            ResultRow(stringResource(R.string.ec_ib), formatValue(result.ib, "A"))
            ResultRow(stringResource(R.string.ec_transistor_power), formatValue(result.transistorPower, "W"))
            ResultRow(stringResource(R.string.ec_resistor_power), formatValue(result.rbPower, "W"))
        }
    }
    Note(stringResource(R.string.ec_drive_too_low), visible = drive != null && vbe != null && drive <= vbe)
}

/** A voltage-divider-biased stage's operating point. */
@Composable
private fun BiasSection() {
    var vccText by rememberSaveable { mutableStateOf("12") }
    var r1Text by rememberSaveable { mutableStateOf("47 k") }
    var r2Text by rememberSaveable { mutableStateOf("10 k") }
    var rcText by rememberSaveable { mutableStateOf("2.2 k") }
    var reText by rememberSaveable { mutableStateOf("470") }
    var betaText by rememberSaveable { mutableStateOf("100") }
    var vbeText by rememberSaveable { mutableStateOf("0.7") }

    val vcc = parseValue(vccText)
    val r1 = parseValue(r1Text)
    val r2 = parseValue(r2Text)
    val rc = parseValue(rcText)
    val re = parseValue(reText)
    val beta = parseValue(betaText)
    val vbe = parseValue(vbeText)
    val q: QPoint? = if (vcc != null && r1 != null && r2 != null && rc != null && re != null && beta != null && vbe != null &&
        vcc > 0 && r1 > 0 && r2 > 0 && rc >= 0 && re >= 0 && rc + re > 0 && beta > 0 && vbe >= 0
    ) dividerBias(vcc, r1, r2, rc, re, beta, vbe) else null

    UnitField(stringResource(R.string.ec_vcc), vccText, { vccText = it }, "V")
    UnitField(stringResource(R.string.ec_r1), r1Text, { r1Text = it }, "Ω")
    UnitField(stringResource(R.string.ec_r2), r2Text, { r2Text = it }, "Ω")
    UnitField(stringResource(R.string.ec_rc_collector), rcText, { rcText = it }, "Ω")
    UnitField(stringResource(R.string.ec_re), reText, { reText = it }, "Ω")
    UnitField(stringResource(R.string.ec_beta), betaText, { betaText = it })
    UnitField(stringResource(R.string.ec_vbe), vbeText, { vbeText = it }, "V")

    Results {
        if (q != null) {
            MainResult(stringResource(R.string.ec_ic), formatValue(q.ic, "A"))
            MainResult(stringResource(R.string.ec_vce), formatValue(q.vce, "V"))
            ResultRow(stringResource(R.string.ec_vb), formatValue(q.vb, "V"))
            ResultRow(stringResource(R.string.ec_ve), formatValue(q.ve, "V"))
            ResultRow(stringResource(R.string.ec_vc), formatValue(q.vc, "V"))
            ResultRow(stringResource(R.string.ec_ib), formatValue(q.ib, "A"))
            ResultRow(
                stringResource(R.string.ec_region),
                stringResource(
                    when (q.region) {
                        Region.Cutoff -> R.string.ec_region_cutoff
                        Region.Active -> R.string.ec_region_active
                        Region.Saturation -> R.string.ec_region_saturation
                    }
                )
            )
        }
    }
    Note(stringResource(R.string.ec_saturated_note), visible = q?.region == Region.Saturation)
}
