package org.lukemech.lmtoolbox.features.reference.smd_packages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/**
 * SMD chip sizes: the imperial code (hundredths of an inch) and the metric one (tenths of a millimetre) of each,
 * its size and a thick-film chip resistor's usual rating. "0603" is both an imperial and a metric size: searching
 * for it finds the two.
 */
object SmdPackagesFeature : ReferenceFeature() {

    override val route = "smd_packages"
    override val titleStringResId: Int = R.string.fp_smd_packages
    override val keywordsResId: Int = R.string.fk_smd_packages
    override val descriptionResId: Int = R.string.fd_smd_packages
    override val icon @Composable get() = Icons.Default.Memory

    override val sheets by lazy {
        listOf(
            TableSheet(
                res(R.string.rf_chip_sizes),
                columns = listOf(res(R.string.rf_imperial), res(R.string.rf_metric), res(R.string.rf_size_mm), res(R.string.rf_size_in), res(R.string.rf_power_typical)),
                rows = listOf(
                    listOf("01005", "0402", "0.4 × 0.2", "0.016 × 0.008", "1/32 W"),
                    listOf("0201", "0603", "0.6 × 0.3", "0.024 × 0.012", "1/20 W"),
                    listOf("0402", "1005", "1.0 × 0.5", "0.04 × 0.02", "1/16 W"),
                    listOf("0603", "1608", "1.6 × 0.8", "0.06 × 0.03", "1/10 W"),
                    listOf("0805", "2012", "2.0 × 1.25", "0.08 × 0.05", "1/8 W"),
                    listOf("1206", "3216", "3.2 × 1.6", "0.12 × 0.06", "1/4 W"),
                    listOf("1210", "3225", "3.2 × 2.5", "0.12 × 0.10", "1/2 W"),
                    listOf("1812", "4532", "4.5 × 3.2", "0.18 × 0.12", "—"),
                    listOf("2010", "5025", "5.0 × 2.5", "0.20 × 0.10", "3/4 W"),
                    listOf("2512", "6332", "6.3 × 3.2", "0.25 × 0.12", "1 W"),
                ).map { row -> row.map(::raw) },
                note = res(R.string.rf_smd_note)
            )
        )
    }
}
