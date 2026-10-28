package org.lukemech.lmtoolbox.features.reference.standard_values

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.runtime.Composable
import kotlin.math.pow
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.ESeries
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** Standard resistor and capacitor values: the E-series, E6 to E96, decade by decade (from ESeries). */
object StandardValuesFeature : ReferenceFeature() {

    override val route = "standard_values"
    override val titleStringResId: Int = R.string.fp_standard_values
    override val keywordsResId: Int = R.string.fk_standard_values
    override val descriptionResId: Int = R.string.fd_standard_values
    override val icon @Composable get() = Icons.Default.FormatListNumbered

    // A row a mantissa, a column a decade: 1 Ω (or pF) to 1 M
    private val DECADES = listOf("", "×10", "×100", "×1 k", "×10 k", "×100 k", "×1 M")

    override val sheets by lazy {
        listOf(ESeries.E6, ESeries.E12, ESeries.E24, ESeries.E48, ESeries.E96).map { series ->
            TableSheet(
                raw("${series.name} (${series.tolerance})"),
                columns = DECADES.map { raw(it.ifEmpty { series.name }) },
                rows = series.values.map { mantissa ->
                    DECADES.indices.map { exp -> raw(formatValue(mantissa * 10.0.pow(exp), "").trim()) }
                },
                note = res(R.string.rf_eseries_note)
            )
        }
    }
}
