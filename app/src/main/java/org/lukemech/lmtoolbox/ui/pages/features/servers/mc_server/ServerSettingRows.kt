package org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import org.lukemech.lmtoolbox.ui.components.InputField
import org.lukemech.lmtoolbox.ui.components.RowColors

/** A setting's colors: as any, or marked as changed while the server runs (it takes it on a restart). */
@Composable
internal fun mark(changed: Boolean) = if (changed) RowColors.changed else RowColors.default

/** On or off, as a SwitchButton, marked by [mark]. */
@Composable
internal fun Toggle(title: String, icon: ImageVector, checked: Boolean, mark: RowColors, subtitle: String? = null, onChange: (Boolean) -> Unit) =
    SwitchButton(title, icon, checked, subtitle = subtitle, colors = mark, onCheckedChange = onChange)

/** A text setting: its value under its title; a tap edits it in a dialog. */
@Composable
internal fun TextSetting(
    title: String, value: String, icon: ImageVector, subtitle: String? = null, keyboard: KeyboardType = KeyboardType.Text,
    mark: RowColors? = null, onSave: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    val colors = mark ?: mark(false)
    FeatureButton(
        title = title,
        subtitle = value.ifEmpty { subtitle ?: "–" },
        icon = icon,
        colors = colors,
        onClick = { editing = true }
    )
    if (editing) EditDialog(title, value, subtitle, keyboard, onDismiss = { editing = false }) {
        editing = false
        onSave(it)
    }
}

@Composable
internal fun NumberSetting(title: String, value: String, icon: ImageVector, range: IntRange, mark: RowColors, onSave: (String) -> Unit) =
    TextSetting(title, value, icon, keyboard = KeyboardType.Number, mark = mark) { v ->
        v.trim().toIntOrNull()?.coerceIn(range)?.let { onSave(it.toString()) }
    }

/** A number in [range] on a slider, written once it's let go. */
@Composable
internal fun SliderSetting(title: String, icon: ImageVector, value: Int, range: IntRange, mark: RowColors, onSave: (Int) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = mark.background,
        contentColor = mark.content,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(modifier = Modifier.width(16.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(current.toInt().toString(), color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = current,
                onValueChange = { current = it },
                onValueChangeFinished = { onSave(current.toInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = range.last - range.first - 1
            )
        }
    }
}

/** One of server.properties as it's written, edited in place (saved as it's left); [changed]: while it runs. */
@Composable
internal fun PropertyField(key: String, value: String, changed: Boolean, onSave: (String) -> Unit) {
    var text by remember(key, value) { mutableStateOf(value) }
    InputField(
        value = text,
        onValueChange = { text = it },
        label = { Text(key) },
        singleLine = true,
        isError = false,
        colors = if (changed) OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
            unfocusedLabelColor = MaterialTheme.colorScheme.tertiary,
        ) else OutlinedTextFieldDefaults.colors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (text != value) onSave(text) }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusLeft { if (text != value) onSave(text) }
    )
}

/** Calls [block] when focus leaves it (after having had it). */
private fun Modifier.onFocusLeft(block: () -> Unit): Modifier = composed {
    var had by remember { mutableStateOf(false) }
    onFocusChanged {
        if (it.isFocused) had = true
        else if (had) {
            had = false
            block()
        }
    }
}

@Composable
internal fun EditDialog(
    title: String, value: String, note: String?, keyboard: KeyboardType,
    secret: Boolean = false, valid: (String) -> Boolean = { true }, onDismiss: () -> Unit, onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(value) }
    val ok = valid(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InputField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (ok) onSave(text) }),
                    // Hidden as it's typed: a secret (the tunnel's token)
                    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth()
                )
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = ok) { Text(stringResource(R.string.f_Mc_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}
