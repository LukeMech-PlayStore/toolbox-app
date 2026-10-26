package org.lukemech.lmtoolbox.features.reference.batteries

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.runtime.Composable
import java.util.Locale
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** Battery sizes: cylindrical cells (AA, 18650...) and coin and button cells (CR2032, LR44...). */
object BatteriesFeature : ReferenceFeature() {

    override val route = "batteries"
    override val titleStringResId: Int = R.string.fp_batteries
    override val keywordsResId: Int = R.string.fk_batteries
    override val descriptionResId: Int = R.string.fd_batteries
    override val icon @Composable get() = Icons.Default.Battery5Bar

    /** Name, IEC 60086's (alkaline's, or lithium's) name, size in mm, voltage by chemistry. */
    private val CYLINDRICAL = listOf(
        listOf("AAAA", "LR61", "8.3 × 42.5", "1.5 V"),
        listOf("AAA", "LR03", "10.5 × 44.5", "1.5 V (NiMH 1.2 V)"),
        listOf("AA", "LR6", "14.5 × 50.5", "1.5 V (NiMH 1.2 V)"),
        listOf("C", "LR14", "26.2 × 50.0", "1.5 V (NiMH 1.2 V)"),
        listOf("D", "LR20", "34.2 × 61.5", "1.5 V (NiMH 1.2 V)"),
        listOf("N", "LR1", "12.0 × 30.2", "1.5 V"),
        listOf("9V", "6LR61", "48.5 × 26.5 × 17.5", "9 V"),
        listOf("23A", "8LR932", "10.3 × 28.5", "12 V"),
        listOf("CR2", "CR15H270", "15.6 × 27.0", "3 V (Li-MnO₂)"),
        listOf("CR123A", "CR17345", "17.0 × 34.5", "3 V (Li-MnO₂)"),
        listOf("14500", "—", "14 × 50", "3.6–3.7 V (Li-ion)"),
        listOf("16340", "—", "16 × 34", "3.6–3.7 V (Li-ion)"),
        listOf("18650", "—", "18 × 65", "3.6–3.7 V (Li-ion)"),
        listOf("21700", "—", "21 × 70", "3.6–3.7 V (Li-ion)"),
        listOf("26650", "—", "26 × 65", "3.6–3.7 V (Li-ion)"),
    )

    /** Lithium coin cells, their size from their code, and the usual capacity of those that have one (mAh). */
    private val COINS = listOf(
        "CR1216" to null, "CR1220" to 40, "CR1225" to null, "CR1616" to null, "CR1620" to null, "CR1632" to null,
        "CR2016" to 90, "CR2025" to 160, "CR2032" to 225, "CR2320" to null, "CR2330" to null, "CR2430" to null,
        "CR2450" to 600, "CR2477" to null,
    )

    /** CR's digits: the diameter in mm, then the height in tenths of a mm (CR2032: 20 × 3.2). */
    private fun coinSize(code: String): String {
        val digits = code.drop(2)
        return String.format(Locale.US, "%d × %.1f", digits.take(2).toInt(), digits.drop(2).toInt() / 10.0)
    }

    /** Button cells, which their names don't size: name, other names, size, voltage. */
    private val BUTTONS = listOf(
        listOf("LR44", "AG13, A76", "11.6 × 5.4", "1.5 V"),
        listOf("SR44", "357, SR44SW", "11.6 × 5.4", "1.55 V"),
        listOf("LR41", "AG3, 192", "7.9 × 3.6", "1.5 V"),
        listOf("SR626SW", "377", "6.8 × 2.6", "1.55 V"),
    )

    override val sheets by lazy {
        listOf(
            TableSheet(
                res(R.string.rf_cylindrical),
                columns = listOf(res(R.string.rf_size_name), raw("IEC"), res(R.string.rf_battery_size), res(R.string.rf_battery_voltage)),
                rows = CYLINDRICAL.map { row -> row.map(::raw) },
                note = res(R.string.rf_cylindrical_note)
            ),
            TableSheet(
                res(R.string.rf_coin_cells),
                columns = listOf(res(R.string.rf_size_name), res(R.string.rf_battery_size), res(R.string.rf_battery_voltage), res(R.string.rf_capacity_typical)),
                rows = COINS.map { (code, capacity) ->
                    listOf(raw(code), raw(coinSize(code)), raw("3 V"), raw(capacity?.let { "≈ $it mAh" } ?: "—"))
                } + BUTTONS.map { (name, other, size, volts) -> listOf(raw("$name ($other)"), raw(size), raw(volts), raw("—")) },
                note = res(R.string.rf_coin_note)
            ),
        )
    }
}
