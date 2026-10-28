package org.lukemech.lmtoolbox.features.reference.logic_gates

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res

/** Logic gates: their truth tables, worked out here, and each one's expression and IEC symbol. */
object LogicGatesFeature : ReferenceFeature() {

    override val route = "logic_gates"
    override val titleStringResId: Int = R.string.fp_logic_gates
    override val keywordsResId: Int = R.string.fk_logic_gates
    override val descriptionResId: Int = R.string.fd_logic_gates
    override val icon @Composable get() = Icons.Default.AccountTree

    /** A gate: its name, what it does with A and B, its expression, its IEC 60617 symbol, and when it gives 1. */
    private class Gate(val name: String, val output: (Boolean, Boolean) -> Boolean, val expression: String, val symbol: String, val when1: Int)

    // ○ marks a negated output, as the symbol's circle
    private val GATES = listOf(
        Gate("AND", { a, b -> a && b }, "A · B", "&", R.string.rf_gate_and),
        Gate("OR", { a, b -> a || b }, "A + B", "≥1", R.string.rf_gate_or),
        Gate("NAND", { a, b -> !(a && b) }, "¬(A · B)", "& ○", R.string.rf_gate_nand),
        Gate("NOR", { a, b -> !(a || b) }, "¬(A + B)", "≥1 ○", R.string.rf_gate_nor),
        Gate("XOR", { a, b -> a != b }, "A ⊕ B", "=1", R.string.rf_gate_xor),
        Gate("XNOR", { a, b -> a == b }, "¬(A ⊕ B)", "=1 ○", R.string.rf_gate_xnor),
    )

    private fun bit(value: Boolean) = raw(if (value) "1" else "0")

    override val sheets by lazy {
        val inputs = listOf(false to false, false to true, true to false, true to true)
        listOf(
            TableSheet(
                res(R.string.rf_truth_table),
                columns = listOf(raw("A"), raw("B")) + GATES.map { raw(it.name) } + raw("NOT A"),
                rows = inputs.map { (a, b) -> listOf(bit(a), bit(b)) + GATES.map { bit(it.output(a, b)) } + bit(!a) }
            ),
            TableSheet(
                res(R.string.rf_gates),
                columns = listOf(res(R.string.rf_gate), res(R.string.rf_expression), raw("IEC 60617"), res(R.string.rf_gives_one)),
                rows = GATES.map { listOf(raw(it.name), raw(it.expression), raw(it.symbol), res(it.when1)) } +
                    listOf(listOf(raw("NOT"), raw("¬A"), raw("1 ○"), res(R.string.rf_gate_not))),
                note = res(R.string.rf_gates_note)
            ),
        )
    }
}
