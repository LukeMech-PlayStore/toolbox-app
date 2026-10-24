package org.lukemech.lmtoolbox.ui.helpers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.linux.InstallPrompt
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.ui.components.PackageTable
import org.lukemech.lmtoolbox.ui.components.PackageTableRow

/**
 * Asks [InstallPrompt]'s questions over the app: the packages a tool is to get, as the installation
 * page lists them (each with what it takes: downloaded, then unpacked), installed or not. Shown once in the app.
 */
@Composable
fun InstallPromptHost() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        InstallPrompt.hosts++
        onDispose { InstallPrompt.hosts-- }
    }
    val request by InstallPrompt.request.collectAsState()
    val r = request ?: return
    // Downloaded first (from Play), or just unpacked: what each takes, its row tells
    val rows = remember(r) { r.parts.map { PackageTableRow(it, archive = !LinuxParts.isExtensionDownloaded(context, it.name)) } }
    AlertDialog(
        onDismissRequest = { InstallPrompt.answer(r, false) },
        // As wide as the screen allows (but its margins): the table's sizes fit
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        // What it does, as the installation page's button: downloading (something is to be), else unpacking
        icon = { Icon(if (rows.any { it.archive }) Icons.Outlined.Download else Icons.Outlined.Unarchive, contentDescription = null) },
        title = { Text(stringResource(R.string.s_ext_confirm_title, stringResource(r.source.titleStringResId))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.s_ext_gate_text_install), style = MaterialTheme.typography.bodyMedium)
                PackageTable(rows, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { InstallPrompt.answer(r, true) }) { Text(stringResource(R.string.s_ext_install)) } },
        dismissButton = { TextButton(onClick = { InstallPrompt.answer(r, false) }) { Text(stringResource(android.R.string.cancel)) } }
    )
}
