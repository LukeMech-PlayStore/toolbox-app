package org.lukemech.lmtoolbox.ui.components

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign

/** Asks before something that can't be undone: [confirmLabel] (in the error color) does it, Cancel doesn't. */
@Composable
fun ConfirmDialog(
    icon: ImageVector,
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) = ConfirmDialog(icon, title, AnnotatedString(text), confirmLabel, onDismiss, onConfirm)

/** As the plain one, its [text] styled in parts (e.g. a command in a terminal's font, see [withCode]). */
@Composable
fun ConfirmDialog(
    icon: ImageVector,
    title: String,
    text: AnnotatedString,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

/** [text] with [code] in it (e.g. a command to type) in a terminal's font. */
fun withCode(text: String, code: String): AnnotatedString = buildAnnotatedString {
    append(text)
    val at = text.indexOf(code)
    if (at >= 0) addStyle(SpanStyle(fontFamily = FontFamily.Monospace), at, at + code.length)
}

/**
 * Says why [permissions] are needed ([title], [message]), then asks for them ([allowLabel]): Android's own dialog
 * while it shows one ([askable]: the permission exists here and isn't granted; Android stops showing it after
 * denials, the first ask kept in [askedKey]), else [settings], where they're turned on by hand. [laterLabel]
 * ([onLater]) lets it be. Whoever shows it checks the permission again on return.
 */
@Composable
fun PermissionDialog(
    icon: ImageVector,
    title: String,
    message: String,
    allowLabel: String,
    laterLabel: String,
    permissions: Array<String>,
    askedKey: String,
    settings: Intent,
    onDismiss: () -> Unit,
    askable: Boolean = true,
    onLater: () -> Unit = onDismiss,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                val canAsk = askable && (!PreferenceHelper.get(context, askedKey, false) ||
                    activity?.shouldShowRequestPermissionRationale(permissions.first()) == true)
                if (canAsk) {
                    PreferenceHelper.put(context, askedKey, true)
                    launcher.launch(permissions)
                } else {
                    context.startActivity(settings)
                }
            }) { Text(allowLabel) }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text(laterLabel) }
        }
    )
}
