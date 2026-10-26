package org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerProfile
import org.lukemech.lmtoolbox.features.servers.mc_server.Servers
import org.lukemech.lmtoolbox.features.servers.mc_server.Transfers
import org.lukemech.lmtoolbox.ui.components.ConfirmDialog
import org.lukemech.lmtoolbox.ui.components.ConfirmedActionButton
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.listBox

/** What a transfer does, in words. */
@Composable
internal fun transferText(kind: Transfers.Kind) = stringResource(
    when (kind) {
        Transfers.Kind.ExportWorld -> R.string.f_Mc_export_world
        Transfers.Kind.ExportServer -> R.string.f_Mc_export_server
        Transfers.Kind.ImportWorld -> R.string.f_Mc_import_world
        Transfers.Kind.AddAddons -> R.string.f_Mc_adding_addons
    }
)

/** How much of how much a transfer has done: jars, or bytes. */
@Composable
internal fun transferAmount(t: Transfers.Transfer): String {
    val context = LocalContext.current
    val total = t.total
    return if (t.kind == Transfers.Kind.AddAddons) "${t.bytes}/${total ?: "?"}"
    else Formatter.formatShortFileSize(context, t.bytes) + (total?.let { " / " + Formatter.formatShortFileSize(context, it) } ?: "")
}

/** Its world and the whole of it out as zips, a world in, its mods or plugins, and deleting it. */
@Composable
internal fun Files(profile: ServerProfile, running: Boolean, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transferState by Transfers.state.collectAsState()
    val transfer = transferState?.takeIf { it.serverId == profile.id }
    val busy = transferState?.isActive == true

    val exportWorld = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { Transfers.export(context, profile, Transfers.Kind.ExportWorld, it) }
    }
    val exportServer = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { Transfers.export(context, profile, Transfers.Kind.ExportServer, it) }
    }
    var confirmImport by remember { mutableStateOf(false) }
    val importWorld = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { Transfers.importWorld(context, profile, it) }
    }

    Column(verticalArrangement = RowArrangement) {
        // The last transfer of this server, done (one under way is the steps' bar; a failure, the bottom bar's red line)
        val done = transfer?.takeIf { !it.isActive && it.error == null && it.kind != Transfers.Kind.AddAddons }
        // The last one while it folds away
        val lastDone = remember { arrayOfNulls<Transfers.Kind>(1) }
        if (done != null) lastDone[0] = done.kind
        Reveal(visible = done != null) {
            lastDone[0]?.let { kind ->
                Text(
                    stringResource(R.string.f_Mc_transfer_done, transferText(kind)),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        FeatureButton(
            title = stringResource(R.string.f_Mc_export_world),
            subtitle = stringResource(R.string.f_Mc_export_world_desc),
            icon = Icons.Default.Landscape,
            onClick = { if (!busy) exportWorld.launch(Transfers.exportName(profile, Transfers.Kind.ExportWorld)) }
        )
        FeatureButton(
            title = stringResource(R.string.f_Mc_export_server),
            subtitle = stringResource(R.string.f_Mc_export_server_desc),
            icon = Icons.Default.FolderZip,
            onClick = { if (!busy) exportServer.launch(Transfers.exportName(profile, Transfers.Kind.ExportServer)) }
        )
        FeatureButton(
            title = stringResource(R.string.f_Mc_import_world),
            subtitle = stringResource(if (running) R.string.f_Mc_stop_first else R.string.f_Mc_import_world_desc),
            icon = Icons.Default.Upload,
            onClick = { if (!busy && !running) confirmImport = true }
        )
        if (confirmImport) {
            ConfirmDialog(
                Icons.Default.Upload, stringResource(R.string.f_Mc_import_world), stringResource(R.string.f_Mc_import_confirm),
                stringResource(R.string.f_Mc_import), onDismiss = { confirmImport = false }
            ) {
                confirmImport = false
                importWorld.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }
        }

        // Mods (Fabric, Forge, NeoForge) or plugins (Paper)
        if (profile.type.addons != null) {
            val mods = profile.type.hasMods
            Section(stringResource(if (mods) R.string.f_Mc_mods else R.string.f_Mc_plugins))
            var generation by remember { mutableIntStateOf(0) }
            val addons = remember(profile.id, generation) { Transfers.addons(context, profile) }
            val add = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                if (uris.isNotEmpty()) scope.launch {
                    val added = Transfers.addAddons(context, profile, uris)
                    if (added < uris.size) Toast.makeText(context, R.string.f_Mc_addons_not_jar, Toast.LENGTH_SHORT).show()
                    generation++
                }
            }
            Note(stringResource(if (mods) R.string.f_Mc_mods_desc else R.string.f_Mc_plugins_desc))
            // Many (a modpack's): in a box of their own
            Column(modifier = Modifier.listBox(rememberScrollState()), verticalArrangement = RowArrangement) { addons.forEach { file ->
                val on = !file.name.endsWith(".disabled")
                FeatureButton(
                    title = file.name.removeSuffix(".disabled").removeSuffix(".jar"),
                    subtitle = Formatter.formatShortFileSize(context, file.length()),
                    icon = Icons.Default.Extension,
                    buttonsInColumn = false,
                    buttonContent = {
                        Switch(checked = on, onCheckedChange = { Transfers.toggle(file); generation++ })
                    },
                    buttonContent2 = {
                        IconButton(onClick = { file.delete(); generation++ }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.f_Mc_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            } }
            FeatureButton(
                title = stringResource(if (mods) R.string.f_Mc_add_mods else R.string.f_Mc_add_plugins),
                icon = Icons.Default.Download,
                onClick = { if (!busy) add.launch(arrayOf("application/java-archive", "application/x-java-archive", "application/octet-stream", "application/zip")) }
            )
        }

        Section(stringResource(R.string.f_Mc_danger))
        // Running, it's stopped first (see Servers.delete)
        ConfirmedActionButton(
            title = stringResource(R.string.f_Mc_delete_server),
            subtitle = stringResource(R.string.f_Mc_delete_server_desc),
            icon = Icons.Default.Delete,
            confirmTitle = stringResource(R.string.f_Mc_delete_server_confirm, profile.name),
            confirmText = stringResource(if (running) R.string.f_Mc_delete_server_text_running else R.string.f_Mc_delete_server_text),
            confirmLabel = stringResource(R.string.f_Mc_delete)
        ) {
            Servers.delete(context, profile.id)
            onDeleted()
        }
    }
}

// Settings rows (the app's look: a card per setting), marked when changed while the server runs
