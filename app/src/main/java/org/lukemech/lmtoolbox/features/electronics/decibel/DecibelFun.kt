package org.lukemech.lmtoolbox.features.electronics.decibel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import kotlin.math.pow
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatNumber
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.decibel.DecibelPage

/** Decibels: and the power or voltage ratios they stand for, and their table. */
object DecibelFeature : AppFeature {

    override val route = "decibel"
    override val titleStringResId: Int = R.string.fp_decibel
    override val keywordsResId: Int = R.string.fk_decibel
    override val descriptionResId: Int = R.string.fd_decibel
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.AutoMirrored.Default.VolumeUp

    private val LEVELS = listOf(-60, -50, -40, -30, -20, -10, -6, -3, -2, -1, 0, 1, 2, 3, 6, 10, 20, 30, 40, 50, 60)

    /** Decibels as power and voltage ratios, and as power over a milliwatt (dBm). */
    val table by lazy {
        TableSheet(
            res(R.string.rf_table),
            columns = listOf(raw("dB"), res(R.string.rf_power_ratio), res(R.string.rf_voltage_ratio), raw("dBm")),
            // P/P0 = 10^(dB/10), U/U0 = 10^(dB/20); dBm over 1 mW
            rows = LEVELS.map { db ->
                listOf(
                    raw(if (db > 0) "+$db" else db.toString()),
                    raw(formatNumber(10.0.pow(db / 10.0), 4)),
                    raw(formatNumber(10.0.pow(db / 20.0), 4)),
                    raw(formatValue(1e-3 * 10.0.pow(db / 10.0), "W")),
                )
            }
        )
    }

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        DecibelPage()
    }
}
