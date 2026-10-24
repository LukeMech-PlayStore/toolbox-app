package org.lukemech.lmtoolbox.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R

/**
 * Trash can for a [PageHeading]'s action, emptying its list; after asking ([confirmTitle],
 * [confirmText], with [confirmIcon]: what's cleared) if those are given.
 */
@Composable
fun ClearAction(
    confirmTitle: String? = null,
    confirmText: String? = null,
    confirmIcon: ImageVector = Icons.Default.Delete,
    onClick: () -> Unit
) {
    var confirming by remember { mutableStateOf(false) }
    IconButton(onClick = { if (confirmTitle != null) confirming = true else onClick() }) {
        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear), tint = MaterialTheme.colorScheme.error)
    }
    if (confirming && confirmTitle != null) {
        ConfirmDialog(
            confirmIcon, confirmTitle, confirmText.orEmpty(), stringResource(R.string.clear),
            onDismiss = { confirming = false }
        ) {
            confirming = false
            onClick()
        }
    }
}

/** A step's main button, like the QR generator's "Create". */
@Composable
fun BigButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A step's second button, above its [BigButton]: e.g. back to what's open. */
@Composable
fun SecondaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One of a row of equal buttons, its [icon] over its [label], e.g. what to do with a made file. */
@Composable
fun RowScope.ActionButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
        modifier = Modifier.weight(1f)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
