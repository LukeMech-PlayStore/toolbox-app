package org.lukemech.lmtoolbox.features.electronics.pt100

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.runtime.Composable
import java.util.Locale
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.pt100.Pt100Page

/** PT100: a platinum sensor's resistance and temperature, and its table. */
object Pt100Feature : AppFeature {

    override val route = "pt100"
    override val titleStringResId: Int = R.string.fp_pt100
    override val keywordsResId: Int = R.string.fk_pt100
    override val descriptionResId: Int = R.string.fd_pt100
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.DeviceThermostat

    /** The PT100, PT500 and PT1000's resistance every 10 °C over IEC 60751's range (the calculator's maths). */
    val table by lazy {
        TableSheet(
            res(R.string.rf_table),
            columns = listOf(raw("°C"), raw("PT100 (Ω)"), raw("PT500 (Ω)"), raw("PT1000 (Ω)")),
            rows = (-200..850 step 10).map { t ->
                listOf(raw(t.toString())) + listOf(100.0, 500.0, 1000.0).map { r0 ->
                    raw(String.format(Locale.US, "%.2f", resistanceAt(t.toDouble(), r0)))
                }
            },
            note = res(R.string.rf_pt100_note)
        )
    }

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        Pt100Page()
    }
}
