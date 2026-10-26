package org.lukemech.lmtoolbox.ui.pages.features.electronics.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.UNIT_PREFIXES
import org.lukemech.lmtoolbox.features.electronics.common.fieldText
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.features.electronics.common.splitPrefix
import org.lukemech.lmtoolbox.ui.components.InputField

/**
 * A value field with its [label] above (as the app's other forms): the number is typed, its
 * [unit]'s prefix (kΩ, μF, mA...) picked at its end, as [value] keeps them ("4.7 k", see [parseValue]).
 * A [computed] one (see [LinkedField]) is filled in and marked as such; [onFocus] when it's tapped.
 */
@Composable
fun UnitField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String = "",
    computed: Boolean = false,
    onFocus: (() -> Unit)? = null,
) {
    val prefixes = UNIT_PREFIXES[unit].orEmpty()
    val (number, prefix) = splitPrefix(value, prefixes)
    fun join(n: String, p: String) = if (p.isEmpty()) n else "$n $p"
    // A prefix its unit doesn't take, left by a switch ("4.7 k" in henries): the value as one it does. Only a
    // trailing letter after a space, so an exponent being typed ("1e", "1e5") stays
    LaunchedEffect(number, unit) {
        if (Regex("""\s\p{L}+$""").containsMatchIn(number)) onValueChange(parseValue(value)?.let { fieldText(it, unit) }.orEmpty())
    }

    ValueField(
        label = label,
        value = number,
        onValueChange = { onValueChange(join(it, prefix)) },
        keyboardType = KeyboardType.Decimal,
        suffix = unit.takeIf { it.isNotEmpty() && prefixes.isEmpty() },
        computed = computed,
        onFocus = onFocus,
        trailing = if (prefixes.isEmpty()) null else {
            { UnitPicker("$prefix$unit", prefixes.map { "$it$unit" }) { picked -> onValueChange(join(number, picked.removeSuffix(unit))) } }
        }
    )
}

/**
 * A value field whose unit is one of [units] (e.g. mm² or AWG), the [unit] it's in picked at its
 * end as a [UnitField]'s prefix is.
 */
@Composable
fun UnitChoiceField(label: String, value: String, onValueChange: (String) -> Unit, unit: String, units: List<String>, onUnitChange: (String) -> Unit) =
    ValueField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        keyboardType = KeyboardType.Decimal,
        trailing = { UnitPicker(unit, units, onUnitChange) }
    )

/** A field's unit at its end ([current]), a tap listing [choices] to pick from. */
@Composable
private fun UnitPicker(current: String, choices: List<String>, onPick: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { picking = true }) {
            Text(current, style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice) },
                    onClick = {
                        onPick(choice)
                        picking = false
                    }
                )
            }
        }
    }
}

/**
 * A field for a code with letters in it (e.g. an SMD marking, "4R7"), its [label] above; [keyboardType] for one
 * of digits alone (a binary number).
 */
@Composable
fun CodeField(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Ascii, onValueChange: (String) -> Unit) =
    ValueField(label, value, { onValueChange(it.uppercase()) }, keyboardType, capitalization = KeyboardCapitalization.Characters)

@Composable
private fun ValueField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    suffix: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    trailing: (@Composable () -> Unit)? = null,
    computed: Boolean = false,
    onFocus: (() -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    // A little more room above and below than rows have: its name and box stand apart from the rows and
    // results around them
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (computed) {
                Text(" · " + stringResource(R.string.ec_computed), style = MaterialTheme.typography.labelLarge, color = primary)
            }
        }
        InputField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization, imeAction = ImeAction.Done),
            singleLine = true,
            suffix = suffix?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            trailingIcon = trailing,
            // A computed value: tinted, borderless, in the accent color
            colors = if (!computed) OutlinedTextFieldDefaults.colors() else OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = primary.copy(alpha = 0.10f),
                unfocusedBorderColor = Color.Transparent,
                unfocusedTextColor = primary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocus?.invoke() }
        )
    }
}
