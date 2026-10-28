package org.lukemech.lmtoolbox.features.reference.resistivity

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.runtime.Composable
import java.math.BigDecimal
import java.math.MathContext
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatNumber
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** The resistivity of metals and resistance alloys at 20 °C, and how it changes with temperature. */
object ResistivityFeature : ReferenceFeature() {

    override val route = "resistivity"
    override val titleStringResId: Int = R.string.fp_resistivity
    override val keywordsResId: Int = R.string.fk_resistivity
    override val descriptionResId: Int = R.string.fd_resistivity
    override val icon @Composable get() = Icons.Default.Science

    /** A material: its name, its resistivity (Ω·m) and its temperature coefficient (1/K), as handbooks have them. */
    private class Material(val name: Int, val rho: Double, val alpha: String)

    private val MATERIALS = listOf(
        Material(R.string.rf_mat_silver, 1.59e-8, "0.0038"),
        Material(R.string.rf_mat_copper, 1.68e-8, "0.00404"),
        Material(R.string.rf_mat_copper_annealed, 1.72e-8, "0.00393"),
        Material(R.string.rf_mat_gold, 2.44e-8, "0.0034"),
        Material(R.string.rf_mat_aluminium, 2.65e-8, "0.0039"),
        Material(R.string.rf_mat_tungsten, 5.60e-8, "0.0045"),
        Material(R.string.rf_mat_zinc, 5.90e-8, "0.0037"),
        Material(R.string.rf_mat_nickel, 6.99e-8, "0.006"),
        Material(R.string.rf_mat_iron, 9.70e-8, "0.005"),
        Material(R.string.rf_mat_platinum, 1.06e-7, "0.00392"),
        Material(R.string.rf_mat_tin, 1.09e-7, "0.0045"),
        Material(R.string.rf_mat_lead, 2.20e-7, "0.0039"),
        Material(R.string.rf_mat_titanium, 4.20e-7, "0.0038"),
        Material(R.string.rf_mat_manganin, 4.82e-7, "0.000002"),
        Material(R.string.rf_mat_constantan, 4.90e-7, "0.000008"),
        Material(R.string.rf_mat_stainless, 6.90e-7, "0.00094"),
        Material(R.string.rf_mat_mercury, 9.80e-7, "0.0009"),
        Material(R.string.rf_mat_nichrome, 1.10e-6, "0.0004"),
    )

    override val sheets by lazy {
        listOf(
            TableSheet(
                raw("20 °C"),
                columns = listOf(res(R.string.rf_material), raw("ρ (Ω·m)"), raw("ρ (Ω·mm²/m)"), raw("α (1/K)")),
                rows = MATERIALS.map { m ->
                    listOf(
                        res(m.name),
                        raw(formatNumber(m.rho, 3)),
                        raw(BigDecimal(m.rho * 1e6).round(MathContext(3)).stripTrailingZeros().toPlainString()),
                        raw(m.alpha),
                    )
                },
                note = res(R.string.rf_resistivity_note)
            )
        )
    }
}
