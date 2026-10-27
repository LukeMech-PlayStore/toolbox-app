package org.lukemech.lmtoolbox.ui.pages.features.servers.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network as AndroidNetwork
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.common.Reach
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.QrCode
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.LinuxTask
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.linux.parts.Bore
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.InputField
import org.lukemech.lmtoolbox.ui.components.PackageTable
import org.lukemech.lmtoolbox.ui.components.PackageTableRow
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.RowColors

// The rows of the tools serving something from the phone (the Minecraft server, the folder share): where it's
// reached (its addresses, its tunnels as they're doing), the tunnels' parts and settings, and their dialogs.

/** Counts the phone's network changes (one coming or going, its addresses changing, a VPN's too) while it's shown. */
@Composable
internal fun networkChanges(context: Context): Int {
    var changes by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: AndroidNetwork) { changes++ }
            override fun onLost(network: AndroidNetwork) { changes++ }
            override fun onLinkPropertiesChanged(network: AndroidNetwork, linkProperties: LinkProperties) { changes++ }
        }
        // VPNs too: a request leaves them out unless told not to
        val request = NetworkRequest.Builder().removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build()
        runCatching { cm.registerNetworkCallback(request, callback) }
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return changes
}

/** How a tunnel's doing, as its row says it. */
internal sealed interface TunnelPhase {
    data object Off : TunnelPhase
    data object Connecting : TunnelPhase
    data class Connected(val text: String) : TunnelPhase
    data object Failed : TunnelPhase
}

/**
 * A tunnel's row: how it's doing, nothing to press (it starts and stops with the server: [offText] says so
 * while it's off); failed, why (as the tunnel said), in red. [icon] as its switch has it ([offIcon] while it
 * isn't on its way or connected).
 */
@Composable
internal fun TunnelStatus(
    title: String, phase: TunnelPhase, error: String?, icon: ImageVector, offIcon: ImageVector = icon,
    offText: String = stringResource(R.string.f_Srv_tunnel_with_server)
) {
    val failed = phase == TunnelPhase.Failed
    FeatureButton(
        title = title,
        subtitle = when (phase) {
            TunnelPhase.Off -> offText
            TunnelPhase.Connecting -> stringResource(R.string.f_Srv_tunnel_connecting)
            is TunnelPhase.Connected -> phase.text
            TunnelPhase.Failed -> listOfNotNull(stringResource(R.string.f_Srv_tunnel_failed), error?.takeIf { it.isNotBlank() }).joinToString(": ")
        },
        colors = if (failed) RowColors.alert else RowColors.default,
        icon = if (failed || phase == TunnelPhase.Off) offIcon else icon
    )
}

/** How long it's been since [since], e.g. "1 h 23 min", "5 min"; again each minute. */
@Composable
internal fun uptime(since: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30.seconds)
        }
    }
    val minutes = ((now - since) / 60_000).coerceAtLeast(0)
    return if (minutes >= 60) stringResource(R.string.f_Srv_uptime_hm, minutes / 60, minutes % 60)
    else stringResource(R.string.f_Srv_uptime_m, minutes)
}

/**
 * An address to type (or open), copied with a tap; [shareable] (a link, e.g. the folder share's): sent to
 * another app, or shown as a QR code ([QrDialog]) for another phone to open, by its buttons.
 */
@Composable
internal fun AddressButton(title: String, address: String, icon: ImageVector, shareable: Boolean = false) {
    val context = LocalContext.current
    var qr by remember { mutableStateOf(false) }
    // The address large, what it's for small above it; on one line, without a link's http:// and its last
    // slash (a browser takes it as well: copied, sent and in the QR code whole)
    FeatureButton(
        title = title,
        subtitle = address.removePrefix("http://").removeSuffix("/"),
        titleSubtitleSwap = true,
        subtitleOneLine = true,
        icon = icon,
        buttonContent = if (shareable) {
            {
                IconButton(onClick = { share(context, title, address) }) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.f_Srv_share), tint = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.f_Srv_copy), tint = MaterialTheme.colorScheme.primary) }
        },
        buttonContent2 = if (shareable) {
            {
                IconButton(onClick = { qr = true }) {
                    Icon(Icons.Default.QrCode2, contentDescription = stringResource(R.string.f_Srv_qr), tint = MaterialTheme.colorScheme.primary)
                }
            }
        } else null,
        buttonsInColumn = !shareable,
        onClick = { copy(context, address) }
    )
    if (qr) QrDialog(title, address) { qr = false }
}

/** [text] as a QR code (dark on white, its quiet zone round it, whatever the theme), large; it's copied with a tap too. */
@Composable
internal fun QrDialog(title: String, text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val code = remember(text) { runCatching { QrCode.encode(text) }.getOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                code?.let { c ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                    ) {
                        // Four modules of quiet zone each side, as readers want
                        val modules = c.size + 8
                        val cell = size.width / modules
                        for (y in 0 until c.size) for (x in 0 until c.size) {
                            if (c[x, y]) drawRect(Color.Black, Offset((x + 4) * cell, (y + 4) * cell), Size(cell + 0.5f, cell + 0.5f))
                        }
                    }
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.clickable { copy(context, text) })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } }
    )
}

/** [text] sent to another app (the share sheet), [title] what it is. */
private fun share(context: Context, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, title)
    runCatching { context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The Tailscale app, to connect the phone to its tailnet; its page on Play if it isn't installed. */
internal fun openTailscale(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(TAILSCALE)
        ?: Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$TAILSCALE".toUri())
    runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private const val TAILSCALE = "com.tailscale.ipn"

// A setter: primaryClip is a val in Kotlin (its getter is nullable, the setter isn't)
@Suppress("UsePropertyAccessSyntax")
internal fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(text, text))
    Toast.makeText(context, R.string.f_Srv_copied, Toast.LENGTH_SHORT).show()
}

/**
 * A tunnel's part's row (cloudflared's, bore's), as the Minecraft server's Java table has them: on the phone (its
 * version), or what getting it takes, each step as it installs for [source]'s tunnel.
 */
@Composable
internal fun PartPackage(source: AppFeature, part: String) {
    val context = LocalContext.current
    // Again once it's installed or removed
    val tasks by LinuxTasks.tasks.collectAsState()
    val removed by LinuxTasks.extensionsRemoved.collectAsState()
    val states by LinuxTasks.packages.collectAsState()
    val ext = remember(part) { LinuxParts.parts(context)[part] } ?: return
    val installing = tasks.firstOrNull { it.isActive && it.type == LinuxTask.Type.Extension && it.source == source }
        ?.takeIf { part in it.packages }
    val there = remember(tasks, removed) { LinuxParts.installedParts(context).firstOrNull { it.part.name == part }?.takeIf { it.unpacked } }
    // While it installs, as it was when it started (its download's size too, unpacking), each step as it goes: as the Java's table
    val row = when {
        installing != null -> PackageTableRow(ext, archive = part in installing.downloads, state = states[part])
        there != null -> PackageTableRow(ext, archive = true, unpacked = true, version = there.version)
        else -> PackageTableRow(ext, archive = !LinuxParts.isExtensionDownloaded(context, part))
    }
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { PackageTable(listOf(row)) }
}

/**
 * A text setting: its value under its title; a tap edits it in a dialog ([filter]: what's typed as it's kept,
 * e.g. a number's digits alone; [valid]: whether it can be saved).
 */
@Composable
internal fun TextSetting(
    title: String, value: String, icon: ImageVector, subtitle: String? = null, keyboard: KeyboardType = KeyboardType.Text,
    mark: RowColors? = null, filter: (String) -> String = { it }, valid: (String) -> Boolean = { true }, onSave: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    val colors = mark ?: RowColors.default
    FeatureButton(
        title = title,
        subtitle = value.ifEmpty { subtitle ?: "–" },
        icon = icon,
        colors = colors,
        onClick = { editing = true }
    )
    if (editing) EditDialog(title, value, subtitle, keyboard, filter = filter, valid = valid, onDismiss = { editing = false }) {
        editing = false
        onSave(it)
    }
}

@Composable
internal fun EditDialog(
    title: String, value: String, note: String?, keyboard: KeyboardType,
    secret: Boolean = false, filter: (String) -> String = { it }, valid: (String) -> Boolean = { true },
    onDismiss: () -> Unit, onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(filter(value)) }
    val ok = valid(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InputField(
                    value = text,
                    // As it's kept: a number's field never holds letters, pasted or typed on a keyboard
                    onValueChange = { text = filter(it) },
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
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = ok) { Text(stringResource(R.string.f_Srv_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

/**
 * A bore tunnel's: the bore server ([server]: bore.pub, or one's own; empty: bore.pub), [onServer] getting it as
 * typed (bore.pub typed: empty), and its secret ([bore]'s).
 */
@Composable
internal fun BoreServerSettings(bore: Bore, server: String, onServer: (String) -> Unit) {
    var hasSecret by remember(bore) { mutableStateOf(bore.hasSecret) }

    Column(verticalArrangement = RowArrangement) {
        TextSetting(stringResource(R.string.f_Srv_bore_server), server.ifEmpty { Bore.SERVER }, Icons.Default.Dns, subtitle = stringResource(R.string.f_Srv_bore_server_desc)) {
            // bore.pub typed: as nothing typed
            onServer(Reach.hostnameOf(it).takeIf { h -> h != Bore.SERVER }.orEmpty())
        }
        // One's own server's (bore server --secret): as the tunnel's token, hidden as it's typed
        Reveal(visible = server.isNotEmpty()) {
            var editing by remember { mutableStateOf(false) }
            FeatureButton(
                title = stringResource(R.string.f_Srv_bore_secret),
                subtitle = stringResource(if (hasSecret) R.string.f_Srv_saved else R.string.f_Srv_bore_secret_desc),
                icon = Icons.Default.Key,
                buttonContent = if (hasSecret) {
                    {
                        IconButton(onClick = {
                            bore.setSecret(null)
                            hasSecret = false
                        }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.f_Srv_token_remove), tint = MaterialTheme.colorScheme.error) }
                    }
                } else null,
                onClick = { editing = true }
            )
            if (editing) EditDialog(
                stringResource(R.string.f_Srv_bore_secret), "", stringResource(R.string.f_Srv_bore_secret_desc), KeyboardType.Password,
                secret = true, valid = { it.isNotBlank() }, onDismiss = { editing = false }
            ) {
                editing = false
                bore.setSecret(it)
                hasSecret = bore.hasSecret
            }
        }
    }
}
