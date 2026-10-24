package org.lukemech.lmtoolbox.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A row's colors ([FeatureButton], [OptionSetting], [SwitchButton]): what's on it ([content]) and under it
 * ([background]). The app's own colors are named here, so a row is marked the same everywhere.
 */
@Immutable
data class RowColors(val content: Color, val background: Color) {
    /** Grayed out: what can't be used now. */
    fun disabled() = copy(content = content.copy(alpha = 0.38f))

    companion object {
        /** A plain row. */
        val default: RowColors
            @Composable get() = RowColors(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))

        /** Something that can't be undone (e.g. deleting a tool's data): the error color, faintly under it too. */
        val danger: RowColors
            @Composable get() = tinted(MaterialTheme.colorScheme.error)

        /** Something wrong with what it shows (a failed task, outdated rates): its text in the error color. */
        val alert: RowColors
            @Composable get() = default.copy(content = MaterialTheme.colorScheme.error)

        /** A setting changed from its default (e.g. a server's options). */
        val changed: RowColors
            @Composable get() = RowColors(MaterialTheme.colorScheme.onTertiaryContainer, MaterialTheme.colorScheme.tertiaryContainer)

        /** What stands out, to be acted on (e.g. an update to have). */
        val highlighted: RowColors
            @Composable get() = RowColors(MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)

        /** [color] on a faint background of itself, e.g. a bar asking for something on top of a page. */
        fun tinted(color: Color) = RowColors(color, color.copy(alpha = 0.1f))
    }
}
