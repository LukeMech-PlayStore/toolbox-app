package org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerRunner
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.Terminal

/** The console as the server prints it, and commands a tap away (typed ones under it). */
@Composable
internal fun ConsoleTab(run: ServerRunner.Run?, onCommand: (String) -> Unit) {
    val log by ServerRunner.log.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        Reveal(visible = log.isEmpty()) {
            Text(stringResource(R.string.f_Mc_console_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Reveal(visible = log.isNotEmpty()) { Terminal(log.joinToString("\n")) }
        Reveal(visible = run?.phase == ServerRunner.Phase.Running) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("list", "save-all", "time set day", "weather clear", "whitelist list").forEach { c ->
                    AssistChip(onClick = { onCommand(c) }, label = { Text("/$c") })
                }
            }
        }
        Text(stringResource(R.string.f_Mc_console_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
