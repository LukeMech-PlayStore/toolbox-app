package org.lukemech.lmtoolbox.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Red button for what can't be undone (deleting, resetting): asks first, with [confirmTitle] and
 * [confirmText], then does [onConfirm] when [confirmLabel] is tapped.
 */
@Composable
fun ConfirmedActionButton(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    confirmTitle: String,
    confirmText: String,
    confirmLabel: String,
    onConfirm: () -> Unit
) {
    var confirming by remember { mutableStateOf(false) }

    FeatureButton(
        title = title,
        subtitle = subtitle,
        icon = icon,
        colors = RowColors.danger,
        onClick = { confirming = true }
    )

    if (confirming) {
        ConfirmDialog(icon, confirmTitle, confirmText, confirmLabel, onDismiss = { confirming = false }) {
            confirming = false
            onConfirm()
        }
    }
}

/**
 * Setting picking one of [options] (value to label): shows the selected label; a tap opens the choices
 * in a box below (a [ChoiceList]: searched once they're many, each under its [groupOf]'s heading, if it has one).
 * [colors] as a [FeatureButton]'s (e.g. to mark it). Not
 * [enabled]: grayed out, it doesn't open (an open one folds). [loading]: its choices are on their way,
 * grayed out as well, a spinner where its arrow goes (as the currency converter's lists). [trailing]
 * before its arrow, e.g. a value the choice gives.
 */
@Composable
fun <T> OptionSetting(
    title: String,
    icon: ImageVector,
    selected: T,
    options: List<Pair<T, String>>,
    colors: RowColors = RowColors.default,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    groupOf: ((T) -> String?)? = null,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val usable = enabled && !loading
    if (!usable) expanded = false

    FeatureButton(
        title = title,
        subtitle = options.firstOrNull { it.first == selected }?.second,
        icon = icon,
        icon2 = if (!usable || trailing != null) null else if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        colors = if (usable) colors else colors.disabled(),
        buttonContent = if (loading) {
            { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary) }
        } else if (trailing != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    trailing()
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = colors.content)
                }
            }
        } else null,
        onClick = { if (usable) expanded = !expanded },
        expanded = expanded,
        popupContent = {
            // A long list (e.g. Minecraft's versions) scrolls in a box of its own, opened at the selected
            // choice: the whole of it in the page was pages long, and its rows past about 8190 px down
            // (Minecraft's from 1.8.9 on) took no taps. Lazy: with snapshots Minecraft's are some 800, every one
            // composed took seconds to open
            val choices = remember(options, groupOf) { options.map { (value, label) -> Choice(value, label, groupOf?.invoke(value)) } }
            ChoiceList(choices, selected) {
                onSelect(it)
                expanded = false
            }
        }
    )
}

/**
 * A setting that's on or off, the whole row switching it; [colors] as a
 * [FeatureButton]'s. Not [enabled]: grayed out (as an [OptionSetting]), it doesn't switch.
 */
@Composable
fun SwitchButton(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    colors: RowColors = RowColors.default,
    onCheckedChange: (Boolean) -> Unit
) = FeatureButton(
    title = title,
    subtitle = subtitle,
    icon = icon,
    colors = if (enabled) colors else colors.disabled(),
    // The row alone toggles it: the switch a part of it, not a second, unnamed control of its own
    buttonContent = { Switch(checked = checked, enabled = enabled, onCheckedChange = null) },
    onClick = { if (enabled) onCheckedChange(!checked) }
)

/** Settings folded under a row (e.g. a tool's defaults): [content] shows once it's opened. */
@Composable
fun ExpandableButton(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    FeatureButton(
        title = title,
        icon = icon,
        icon2 = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        onClick = { open = !open },
        expanded = open,
        popupContent = content
    )
}
