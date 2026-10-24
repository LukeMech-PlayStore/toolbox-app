package org.lukemech.lmtoolbox.ui.pages.features.electronics.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.RowArrangement

/** A calculator's page: its fields, settings and results one under another, the [formulas] it uses behind its button. */
@Composable
fun CalculatorPage(title: String, formulas: List<Formula>, content: @Composable () -> Unit) =
    PageHeading(title = title, action = { FormulasButton(formulas) }) {
        Column(verticalArrangement = RowArrangement, modifier = Modifier.animateContentSize()) { content() }
    }
