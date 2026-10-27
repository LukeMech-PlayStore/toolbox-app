package org.lukemech.lmtoolbox.ui.pages.features.electronics.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.ToolSheet
import org.lukemech.lmtoolbox.features.reference.common.res
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.pages.features.reference.common.ReferencePage

/**
 * A calculator's page: its fields, settings and results one under another, the [formulas] it uses behind its button.
 * With [tables] (its values worked out ahead, e.g. a PT100's every 10 °C; what it's used with, a battery's sizes),
 * they're pages next to the calculator's, picked as a reference's are.
 */
@Composable
fun CalculatorPage(title: String, formulas: List<Formula>, tables: List<TableSheet> = emptyList(), content: @Composable () -> Unit) {
    val fields = @Composable { Column(verticalArrangement = RowArrangement, modifier = Modifier.animateContentSize()) { content() } }
    if (tables.isEmpty()) return PageHeading(title = title, action = { FormulasButton(formulas) }) { fields() }
    // The same sheets every recomposition, else each would be a new page to fade to; the fields are the latest
    val current by rememberUpdatedState(fields)
    val sheets = remember(tables) { listOf(ToolSheet(res(R.string.rf_calculate)) { current() }) + tables }
    ReferencePage(title, sheets, icon = Icons.Default.Calculate, action = { FormulasButton(formulas) })
}
