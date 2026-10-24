package org.lukemech.lmtoolbox.ui.components

import android.content.Context
import android.os.StatFs
import android.os.storage.StorageManager
import android.text.format.Formatter
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureDelivery
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.LinuxTask
import org.lukemech.lmtoolbox.linux.LinuxTasks

/**
 * [feature]'s page (see [AppFeature.uiClass]), behind [ExtensionGate]. Should its module's code still be
 * out of reach (installed, but not loaded), the page stays empty and the log says so.
 */
@Composable
fun ModulePage(feature: AppFeature, arguments: Map<String, String?>?) = ExtensionGate(feature) {
    val tasks by LinuxTasks.tasks.collectAsState()
    // Again once its module is installed (a task finishing)
    val ui = remember(tasks) { FeatureDelivery.ui(feature) }
    if (ui != null) return@ExtensionGate ui.Content(arguments)
    val task = tasks.firstOrNull { it.type == LinuxTask.Type.Extension && it.source == feature }
    PageHeading(stringResource(feature.titleStringResId), verticalArrangement = Arrangement.Center) {
        if (task?.state == LinuxTask.State.Failed) {
            task.message?.let { EmptyText(it) }
        } else if (task?.isActive != true) {
            LaunchedEffect(Unit) { Log.e("ModulePage", "${feature.uiClass}: its module's pages aren't loaded") }
        }
    }
}

/**
 * A feature page running on programs of its own (its Linux subsystem extensions, each a Play
 * module): until they're installed and unpacked, how much they take, what they are and a button
 * getting them; then [content].
 */
@Composable
fun ExtensionGate(feature: AppFeature, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // A part of it isn't built for this device's ABI (e.g. no Java for x86): nothing to install
    if (!LinuxParts.isAvailable(context, feature)) {
        PageHeading(stringResource(feature.titleStringResId), verticalArrangement = Arrangement.Center) {
            EmptyText(stringResource(R.string.s_ext_unavailable))
        }
        return
    }
    val tasks by LinuxTasks.tasks.collectAsState()
    // Again whenever a task changes, e.g. once they're unpacked
    val missingNow = remember(tasks) { feature.linuxExtensions.takeIf { it.isNotEmpty() }?.let { LinuxParts.missingExtensions(context, it) }.orEmpty() }
    if (missingNow.isEmpty()) {
        content()
        return
    }
    val task = tasks.firstOrNull { it.type == LinuxTask.Type.Extension && it.source == feature }
    // While they install, the packages stay listed as they were when it started (each with how it's doing),
    // the page left and opened again too
    val busy = task?.isActive == true
    val missing = if (busy) remember(task.id) { task.packages.mapNotNull { LinuxParts.parts(context)[it] } } else missingNow
    val states by LinuxTasks.packages.collectAsState()
    // Downloaded first (from Play), or just unpacked (e.g. after the core was unpacked again); as it was when
    // the installation started, while it runs
    val toDownloadNow = remember(missingNow) { missingNow.filterNot { LinuxParts.isExtensionDownloaded(context, it.name) }.map { it.name }.toSet() }
    val toDownload = if (busy) task.downloads else toDownloadNow
    val download = toDownload.isNotEmpty()
    // What they take, as their rows tell: downloaded, their archives (kept with them), then unpacking
    // each (already downloaded: just what it unpacks to)
    val archives = missing.filter { it.name in toDownload }.sumOf { it.archiveSize }
    val size = Formatter.formatShortFileSize(context, archives + missing.sumOf { if (it.name in toDownload) it.unpackSize else it.size })
    val free = Formatter.formatShortFileSize(context, freeSpace(context))

    // As every tool's first page: what it takes in the middle (scrolling), its button at the bottom
    StepWithButtons(
        content = {
            PageHeading(stringResource(feature.titleStringResId), verticalArrangement = Arrangement.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(96.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                feature.icon, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // How much, first: in all (downloaded and unpacked), then what of it is downloaded, and what's free
                    Text(size, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    // Of it, what's downloaded (if anything is)
                    if (download) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.s_ext_gate_download, Formatter.formatShortFileSize(context, archives)),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.s_ext_gate_free, free),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(if (download) R.string.s_ext_gate_text_install else R.string.s_ext_gate_text),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Each package: what it holds (as the subsystem's build listed it), its size and, while
                    // installing, how it's doing
                    PackageTable(
                        missing.map { PackageTableRow(it, archive = it.name in toDownload, state = if (busy) states[it.name] else null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.s_ext_dialog_remove),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        buttons = {
            // A failure shows in the bottom bar (with its log a tap away); its progress there too, and by package above.
            // While it runs, as what it's at: downloading, then unpacking
            val downloading = if (busy) task.stage == R.string.s_tasks_downloading else download
            BigButton(
                if (downloading) Icons.Outlined.Download else Icons.Outlined.Unarchive,
                when {
                    busy -> task.title(context)
                    download -> stringResource(R.string.s_ext_install)
                    else -> stringResource(R.string.s_ext_unpack)
                },
                enabled = !busy
            ) {
                LinuxTasks.unpack(context, feature)
            }
        }
    )
}

/** Under a feature's title in its settings while its programs aren't installed (or unpacked): that, and their size. Just to know. */
@Composable
fun ExtensionStatus(feature: AppFeature) {
    val context = LocalContext.current
    val tasks by LinuxTasks.tasks.collectAsState()
    val missing = remember(tasks) { feature.linuxExtensions.takeIf { it.isNotEmpty() }?.let { LinuxParts.missingExtensions(context, it) }.orEmpty() }
    val toDownload = remember(missing) { missing.filterNot { LinuxParts.isExtensionDownloaded(context, it.name) } }
    val download = toDownload.isNotEmpty()
    // As the installation page: downloaded, their archives too, then unpacking each (already downloaded: what it unpacks to)
    val size = missing.sumOf { it.size } + toDownload.sumOf { it.archiveSize + it.extractedSize }
    val text = stringResource(
        if (download) R.string.s_ext_status_install else R.string.s_ext_status,
        Formatter.formatShortFileSize(context, size)
    )
    // Installed, it folds away (as it last read)
    val last = remember { arrayOf("") }
    if (missing.isNotEmpty()) last[0] = text
    Reveal(visible = missing.isNotEmpty()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                last[0],
                // Centred on the icon: without the line's extra space above and below
                style = MaterialTheme.typography.labelMedium.copy(
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Space the tools may take: what's free, and what the system would clear of apps' caches for them. */
private fun freeSpace(context: Context): Long {
    val storage = context.getSystemService(StorageManager::class.java)
    return runCatching { storage.getAllocatableBytes(storage.getUuidForPath(context.filesDir)) }
        .getOrElse { StatFs(context.filesDir.path).availableBytes }
}
