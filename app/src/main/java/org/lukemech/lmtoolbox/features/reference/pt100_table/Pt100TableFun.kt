package org.lukemech.lmtoolbox.features.reference.pt100_table

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.runtime.Composable
import java.util.Locale
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.pt100.resistanceAt
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** The PT100, PT500 and PT1000's resistance every 10 °C over IEC 60751's range (the PT100 calculator's maths). */
object Pt100TableFeature : ReferenceFeature() {

    override val route = "pt100_table"
    override val titleStringResId: Int = R.string.fp_pt100_table
    override val keywordsResId: Int = R.string.fk_pt100_table
    override val descriptionResId: Int = R.string.fd_pt100_table
    override val icon @Composable get() = Icons.Default.DeviceThermostat

    override val sheets by lazy {
        listOf(
            TableSheet(
                raw("IEC 60751"),
                columns = listOf(raw("°C"), raw("PT100 (Ω)"), raw("PT500 (Ω)"), raw("PT1000 (Ω)")),
                rows = (-200..850 step 10).map { t ->
                    listOf(raw(t.toString())) + listOf(100.0, 500.0, 1000.0).map { r0 ->
                        raw(String.format(Locale.US, "%.2f", resistanceAt(t.toDouble(), r0)))
                    }
                },
                note = res(R.string.rf_pt100_note)
            )
        )
    }
}
