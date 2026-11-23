package org.lukemech.lmtoolbox.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** The corners of the app's text fields. */
val FieldShape = RoundedCornerShape(12.dp)

/**
 * The app's text field: an outlined one with its corners ([FieldShape]), whose Done closes the keyboard unless
 * [keyboardActions] says what it does. Otherwise, as Material's OutlinedTextField.
 */
@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = FieldShape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        isError = isError,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions ?: KeyboardActions(onDone = { focusManager.clearFocus() }),
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors,
    )
}

/**
 * The app's field for a secret (a password, a token): one line, hidden as it's typed unless [hidden] is false (e.g.
 * shown with an eye in [trailingIcon]). [value] and [onValueChange] as [InputField]'s: what the caller keeps (e.g.
 * filtered) is what it shows. Done runs [onDone], else closes the keyboard.
 */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hidden: Boolean = true,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Password,
    onDone: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val state = rememberTextFieldState(value)
    val current by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    // The caller's value (a reset, a filtered one) into the field; what's typed out to it
    LaunchedEffect(value) { if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value) }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { if (it != current) onChange(it) }
    }
    OutlinedSecureTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        label = label?.let { l -> { l() } },
        trailingIcon = trailingIcon,
        isError = isError,
        textObfuscationMode = if (hidden) TextObfuscationMode.RevealLastTyped else TextObfuscationMode.Visible,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        onKeyboardAction = KeyboardActionHandler { onDone?.invoke() ?: focusManager.clearFocus() },
        shape = FieldShape,
    )
}
