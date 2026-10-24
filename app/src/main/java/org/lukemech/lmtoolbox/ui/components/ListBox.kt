package org.lukemech.lmtoolbox.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * How tall a list in a page (choices, a log, what a package holds, mods) gets, about seven and a half
 * rows: as tall as it is up to it, longer it scrolls in its own box ([listBox]).
 */
val LIST_MAX_HEIGHT = 360.dp

/** A list in a page, as tall as it is up to [LIST_MAX_HEIGHT], then scrolling by [scroll] in its own box, faded at its edges. */
@Composable
fun Modifier.listBox(scroll: ScrollState): Modifier = heightIn(max = LIST_MAX_HEIGHT).edgeFade(scroll).verticalScroll(scroll)
