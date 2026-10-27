package org.lukemech.lmtoolbox.features.reference.ip_code

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.ToolSheet
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res
import org.lukemech.lmtoolbox.ui.pages.features.reference.ip_code.IpCodeDecoder

/** The IP code (IEC 60529): what each of its two digits says, picked or read from a table. */
object IpCodeFeature : ReferenceFeature() {

    override val route = "ip_code"
    override val titleStringResId: Int = R.string.fp_ip_code
    override val keywordsResId: Int = R.string.fk_ip_code
    override val descriptionResId: Int = R.string.fd_ip_code
    override val icon @Composable get() = Icons.Default.WaterDrop

    /** The first digit: against solid objects (and touch); X where it wasn't tested. */
    val SOLIDS = listOf(
        "X" to R.string.rf_ip_x,
        "0" to R.string.rf_ip_solid_0,
        "1" to R.string.rf_ip_solid_1,
        "2" to R.string.rf_ip_solid_2,
        "3" to R.string.rf_ip_solid_3,
        "4" to R.string.rf_ip_solid_4,
        "5" to R.string.rf_ip_solid_5,
        "6" to R.string.rf_ip_solid_6,
    )

    /** The second digit: against water. */
    val WATER = listOf(
        "X" to R.string.rf_ip_x,
        "0" to R.string.rf_ip_water_0,
        "1" to R.string.rf_ip_water_1,
        "2" to R.string.rf_ip_water_2,
        "3" to R.string.rf_ip_water_3,
        "4" to R.string.rf_ip_water_4,
        "5" to R.string.rf_ip_water_5,
        "6" to R.string.rf_ip_water_6,
        "7" to R.string.rf_ip_water_7,
        "8" to R.string.rf_ip_water_8,
        "9" to R.string.rf_ip_water_9,
    )

    override val sheets by lazy {
        listOf(
            ToolSheet(res(R.string.rf_decode)) { IpCodeDecoder() },
            TableSheet(
                res(R.string.rf_ip_first),
                columns = listOf(res(R.string.rf_digit), res(R.string.rf_protection)),
                rows = SOLIDS.drop(1).map { (digit, meaning) -> listOf(raw(digit), res(meaning)) }
            ),
            TableSheet(
                res(R.string.rf_ip_second),
                columns = listOf(res(R.string.rf_digit), res(R.string.rf_protection)),
                rows = WATER.drop(1).map { (digit, meaning) -> listOf(raw(digit), res(meaning)) },
                note = res(R.string.rf_ip_note)
            ),
        )
    }
}
