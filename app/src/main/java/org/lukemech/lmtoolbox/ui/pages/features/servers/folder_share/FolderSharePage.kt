package org.lukemech.lmtoolbox.ui.pages.features.servers.folder_share

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.folder_share.FolderShare
import org.lukemech.lmtoolbox.features.servers.folder_share.FolderShareFeature
import org.lukemech.lmtoolbox.features.servers.folder_share.FolderShareServer
import org.lukemech.lmtoolbox.features.servers.folder_share.ShareSettings
import org.lukemech.lmtoolbox.helpers.ResourceScope
import org.lukemech.lmtoolbox.linux.InstallResult
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.parts.Bore
import org.lukemech.lmtoolbox.linux.parts.Tunnel
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.ErrorText
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.InfoTile
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.REVEAL_MS
import org.lukemech.lmtoolbox.ui.components.ResourceTiles
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.RowColors
import org.lukemech.lmtoolbox.ui.components.SectionTitle
import org.lukemech.lmtoolbox.ui.components.StepWithButtons
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.AddressButton
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.BoreServerSettings
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.EditDialog
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.PartPackage
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.ReachSection
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.TextSetting
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.TunnelPhase
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.TunnelStatus
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.uptime
import org.lukemech.lmtoolbox.features.servers.folder_share.FolderShareFeature as F

/** How many of the latest requests its page shows. */
private const val SHOWN = 6

/**
 * The folder share's page: how it's doing (who's connected, what they asked for lately), where it's opened
 * (the network's address, the tunnels', the tailnet's: each copied, sent or shown as a QR code), the folder and
 * how it's shared; sharing it, or stopping, at the bottom.
 */
@Composable
fun FolderSharePage() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { FolderShare.attach(context) }
    var settings by remember { mutableStateOf(ShareSettings.load(context)) }
    fun set(key: String, value: Any) {
        ShareSettings.put(context, key, value)
        settings = ShareSettings.load(context)
    }
    val state by FolderShare.state.collectAsState()
    val run = state
    val running = run?.isActive == true
    val folder = rememberSharedFolder(settings, run?.phase) { settings = ShareSettings.load(context) }

    StepWithButtons(
        content = {
            PageHeading(stringResource(F.titleStringResId)) {
                Column(verticalArrangement = RowArrangement) {
                    StatusCard(run, folder.name)
                    // What it takes while it's shared (the app's network), how long it's been, who's connected
                    val lastRun = remember { arrayOfNulls<FolderShare.Run>(1) }
                    if (running) lastRun[0] = run
                    Reveal(visible = running) {
                        lastRun[0]?.let { r -> Stats(r) }
                    }
                    Reveal(visible = running) {
                        lastRun[0]?.let { r -> Recent(r.recent) }
                    }

                    Addresses(run?.takeIf { it.isActive }?.port ?: settings.port, settings)

                    ShareSetup(settings, folder, running, ::set)

                    Section(stringResource(R.string.f_Srv_internet))
                    BoreSection(settings, ::set)
                    CloudflareSection(settings, ::set)
                    Note(stringResource(R.string.f_Fs_internet_note), visible = (settings.bore || settings.cloudflare) && !settings.protected)

                    Spacer(modifier = Modifier.height(4.dp))
                    Note(stringResource(R.string.f_Fs_battery_note))
                }
            }
        },
        buttons = {
            ErrorText(run?.takeIf { it.phase == FolderShare.Phase.Failed }?.error)
            if (running) {
                BigButton(Icons.Default.Stop, stringResource(R.string.f_Fs_stop), enabled = true) { FolderShare.stop() }
            } else {
                BigButton(Icons.Default.PlayArrow, stringResource(R.string.f_Fs_start), enabled = settings.folder != null && folder.allowed) {
                    FolderShare.start(context)
                }
            }
        }
    )
}

/** Its settings in the tool's settings page: what to share and how, as its page has them (the tunnels stay there). */
@Composable
fun FolderShareSettings() {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(ShareSettings.load(context)) }
    val run by FolderShare.state.collectAsState()
    val folder = rememberSharedFolder(settings, run?.phase) { settings = ShareSettings.load(context) }
    Column(verticalArrangement = RowArrangement) {
        ShareSetup(settings, folder, running = run?.isActive == true) { key, value ->
            ShareSettings.put(context, key, value)
            settings = ShareSettings.load(context)
        }
    }
}

/** The folder shared as it's shown: its name as its provider has it, whether it's still allowed, and its picker. */
private class SharedFolder(val name: String?, val allowed: Boolean, val pick: () -> Unit)

/** [settings]' folder, allowed or not (taken back, or deleted) as of the share's [phase]; [onPicked] once another's set. */
@Composable
private fun rememberSharedFolder(settings: ShareSettings, phase: FolderShare.Phase?, onPicked: () -> Unit): SharedFolder {
    val context = LocalContext.current
    val folder = settings.folder
    val allowed = remember(folder, phase) { folder != null && ShareSettings.isAllowed(context, folder) }
    val name by produceState<String?>(null, folder, allowed) {
        value = folder?.takeIf { allowed }?.let { withContext(Dispatchers.IO) { FolderShare.folderName(context, it) } }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        ShareSettings.setFolder(context, uri)
        onPicked()
    }
    return SharedFolder(name, allowed) { pick.launch(folder) }
}

/** What's shared (the folder, its port) and how (uploads, a password, stopping when idle): its page's and its settings'. */
@Composable
private fun ShareSetup(settings: ShareSettings, folder: SharedFolder, running: Boolean, set: (String, Any) -> Unit) {
    FolderRow(folder.name, settings.folder != null && !folder.allowed, running, folder.pick)
    PortRow(settings.port, running) { set(F.S_PORT, it) }
    SwitchButton(stringResource(R.string.f_Fs_uploads), Icons.Default.CloudUpload, settings.uploads, subtitle = stringResource(R.string.f_Fs_uploads_desc)) {
        set(F.S_UPLOADS, it)
    }
    PasswordSettings(settings, set)
    IdleSetting(settings.idleMinutes) { set(F.S_IDLE, it) }
}

@Composable
private fun Section(title: String) {
    SectionTitle(title, modifier = Modifier.padding(top = 8.dp))
}

/** How it's doing: shared or not (why it stopped, if by an error), the folder and port. */
@Composable
private fun StatusCard(run: FolderShare.Run?, folderName: String?) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PhaseDot(run?.phase, 18.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    stringResource(
                        when (run?.phase) {
                            FolderShare.Phase.Running -> R.string.f_Fs_phase_running
                            FolderShare.Phase.Failed -> R.string.f_Fs_phase_failed
                            else -> R.string.f_Fs_phase_stopped
                        }
                    ),
                    style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f)
                )
            }
            val name = run?.takeIf { it.isActive }?.name ?: folderName
            name?.let {
                Text(
                    it + (run?.takeIf { r -> r.isActive }?.let { r -> " · ${r.port}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A dot in the color of how it's doing: green shared, red stopped by an error, gray not shared. */
@Composable
private fun PhaseDot(phase: FolderShare.Phase?, size: Dp) {
    val color = when (phase) {
        FolderShare.Phase.Running -> Color(0xFF2E9E4F)
        FolderShare.Phase.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Icon(Icons.Default.Circle, contentDescription = null, tint = color, modifier = Modifier.size(size))
}

/** The app's network (all of it: Android counts it by app), how long it's been shared, who's connected and how many requests. */
@Composable
private fun Stats(run: FolderShare.Run) {
    // Who's connected changes as time goes too (counted within a while of their last request)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(run.seen) {
        now = System.currentTimeMillis()
        kotlinx.coroutines.delay(FolderShare.CONNECTED_MS.milliseconds)
        now = System.currentTimeMillis()
    }
    ResourceTiles(
        FolderShareFeature, Modifier.padding(horizontal = 16.dp), ResourceScope.App,
        extra = listOf(
            InfoTile(stringResource(R.string.f_Srv_stat_uptime), uptime(run.startedAt), Icons.Default.Schedule),
            InfoTile(stringResource(R.string.f_Fs_stat_connected), "${run.connected(now)}", Icons.Default.Group),
            InfoTile(stringResource(R.string.f_Fs_stat_requests), "${run.requests}", Icons.Default.SwapVert),
        )
    )
}

/**
 * The latest requests, newest on top: each unfolding as it comes, folding away as newer ones push it out (a
 * file asked for again and again, a video seeking, stays one row).
 */
@Composable
private fun Recent(recent: List<FolderShareServer.Access>) {
    val context = LocalContext.current
    Column(verticalArrangement = RowArrangement) {
        Section(stringResource(R.string.f_Fs_recent))
        Note(stringResource(R.string.f_Fs_recent_none), visible = recent.isEmpty())
        val top = recent.take(SHOWN)
        val shown = top.map { it.id }.toSet()
        // Every row seen, so one pushed out folds away rather than vanishing; the long gone forgotten
        val ids = remember { mutableStateListOf<Long>() }
        val last = remember { mutableMapOf<Long, FolderShareServer.Access>() }
        top.forEach { last[it.id] = it }
        top.reversed().forEach { if (it.id !in ids) ids.add(0, it.id) }
        while (ids.size > SHOWN * 3) last.remove(ids.removeAt(ids.lastIndex))
        ids.forEach { id ->
            key(id) {
                Reveal(visible = id in shown) {
                    last[id]?.let { access ->
                        val who = listOfNotNull(
                            access.client,
                            when (access.via) {
                                FolderShareServer.Via.Bore -> stringResource(R.string.f_Fs_via_bore)
                                FolderShareServer.Via.Cloudflare -> stringResource(R.string.f_Fs_via_cloudflare)
                                else -> null
                            },
                            "${access.method} ${access.status}",
                            Formatter.formatShortFileSize(context, access.bytes).takeIf { access.bytes > 0 },
                        ).joinToString(" · ")
                        FeatureButton(
                            title = access.path,
                            subtitle = who,
                            colors = if (access.status >= 400) RowColors.alert else RowColors.default,
                            icon = when (access.via) {
                                FolderShareServer.Via.Network -> Icons.Default.Wifi
                                FolderShareServer.Via.Tailscale -> Icons.Default.VpnLock
                                FolderShareServer.Via.Bore -> Icons.Default.SettingsEthernet
                                FolderShareServer.Via.Cloudflare -> Icons.Default.Cloud
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Where it's opened: the phone's network (read again as its networks change), the tunnels once they're
 * connected (else how they're doing), the tailnet; each a link to copy, send or show as a QR code.
 */
@Composable
private fun Addresses(port: Int, settings: ShareSettings) {
    val tunnelState by FolderShare.tunnels.tunnel.collectAsState()
    val tunnel = tunnelState?.takeIf { it.serverId == FolderShare.ID }?.state
    val boreState by FolderShare.tunnels.bore.collectAsState()
    val bore = boreState?.takeIf { it.serverId == FolderShare.ID }?.state

    Column(verticalArrangement = RowArrangement) {
        Section(stringResource(R.string.f_Fs_open_at))
        ReachSection(address = { "http://$it:$port/" }, tunnelsOn = settings.bore || settings.cloudflare, refresh = port) {
            // The tunnels, starting with the share by themselves: their address once connected, else how they're doing
            Column(verticalArrangement = RowArrangement) {
                Reveal(visible = settings.bore) {
                    val address = bore?.address?.takeIf { bore.phase == Bore.Phase.Connected }
                    Crossfade(address, animationSpec = tween(REVEAL_MS), label = "bore") { a ->
                        if (a != null) AddressButton(stringResource(R.string.f_Srv_through_bore), "http://$a/", Icons.Default.Public, shareable = true)
                        else TunnelStatus(
                            stringResource(R.string.f_Srv_bore), tunnelPhase(bore?.phase?.name), bore?.error, Icons.Default.SettingsEthernet,
                            offText = stringResource(R.string.f_Fs_tunnel_with_share)
                        )
                    }
                }
                Reveal(visible = settings.cloudflare) {
                    val url = tunnel?.url?.takeIf { tunnel.phase == Tunnel.Phase.Connected }
                    Crossfade(url, animationSpec = tween(REVEAL_MS), label = "cloudflare") { u ->
                        if (u != null) AddressButton(stringResource(R.string.f_Srv_through_cloudflare), "$u/", Icons.Default.Public, shareable = true)
                        else TunnelStatus(
                            stringResource(R.string.f_Srv_tunnel), tunnelPhase(tunnel?.phase?.name), tunnel?.error,
                            Icons.Default.Cloud, Icons.Default.CloudOff, offText = stringResource(R.string.f_Fs_tunnel_with_share)
                        )
                    }
                }
            }
        }
        Note(stringResource(R.string.f_Fs_open_at_desc))
    }
}

/** A tunnel's phase (Bore's or Cloudflare's, by name: theirs are alike) as its row tells it; connected without its address yet, on its way still. */
private fun tunnelPhase(phase: String?): TunnelPhase = when (phase) {
    "Connecting" -> TunnelPhase.Connecting
    "Connected" -> TunnelPhase.Connecting
    "Failed" -> TunnelPhase.Failed
    else -> TunnelPhase.Off
}

/** The folder shared: picked with the system's picker (again if it's gone), not while it's shared. */
@Composable
private fun FolderRow(name: String?, gone: Boolean, running: Boolean, onPick: () -> Unit) {
    FeatureButton(
        title = stringResource(R.string.f_Fs_folder),
        subtitle = when {
            gone -> stringResource(R.string.f_Fs_folder_gone)
            running -> listOfNotNull(name, stringResource(R.string.f_Fs_stop_first)).joinToString(" · ")
            else -> name ?: stringResource(R.string.f_Fs_folder_pick)
        },
        icon = Icons.Default.Folder,
        icon2 = if (running) null else Icons.AutoMirrored.Default.KeyboardArrowRight,
        colors = when {
            gone -> RowColors.alert
            running -> RowColors.default.disabled()
            else -> RowColors.default
        },
        onClick = { if (!running) onPick() }
    )
}

/** Its port: digits alone as it's typed, 1024 to 65535; not while it's shared (it listens on the one it started with). */
@Composable
private fun PortRow(port: Int, running: Boolean, onSave: (Int) -> Unit) {
    Crossfade(running, animationSpec = tween(REVEAL_MS), label = "port") { locked ->
        if (locked) {
            FeatureButton(
                title = stringResource(R.string.f_Fs_port),
                subtitle = "$port · " + stringResource(R.string.f_Fs_stop_first),
                icon = Icons.Default.Dns,
                colors = RowColors.default.disabled()
            )
        } else {
            TextSetting(
                stringResource(R.string.f_Fs_port), port.toString(), Icons.Default.Dns, subtitle = stringResource(R.string.f_Fs_port_desc),
                keyboard = KeyboardType.Number,
                filter = { t -> t.filter(Char::isDigit).take(5) },
                valid = { t -> t.toIntOrNull()?.let { it in ShareSettings.PORTS } == true }
            ) { t -> t.toIntOrNull()?.takeIf { it in ShareSettings.PORTS }?.let(onSave) }
        }
    }
}

/** A name and password asked for (HTTP Basic): turned on, both unfold; with no password set, it says it's open still. */
@Composable
private fun PasswordSettings(settings: ShareSettings, set: (String, Any) -> Unit) {
    Column(verticalArrangement = RowArrangement) {
        SwitchButton(stringResource(R.string.f_Fs_auth), Icons.Default.Lock, settings.auth, subtitle = stringResource(R.string.f_Fs_auth_desc)) {
            set(F.S_AUTH, it)
        }
        RevealSection(settings.auth) {
            TextSetting(stringResource(R.string.f_Fs_user), settings.user, Icons.Default.Person, subtitle = stringResource(R.string.f_Fs_user_desc)) {
                set(F.S_USER, it.trim())
            }
            var editing by remember { mutableStateOf(false) }
            FeatureButton(
                title = stringResource(R.string.f_Fs_password),
                subtitle = stringResource(if (settings.password.isNotEmpty()) R.string.f_Srv_saved else R.string.f_Fs_password_unset),
                icon = Icons.Default.Key,
                colors = if (settings.password.isEmpty()) RowColors.alert else RowColors.default,
                onClick = { editing = true }
            )
            if (editing) EditDialog(
                stringResource(R.string.f_Fs_password), "", null, KeyboardType.Password,
                secret = true, valid = { it.isNotEmpty() }, onDismiss = { editing = false }
            ) {
                editing = false
                set(F.S_PASSWORD, it)
            }
            Note(stringResource(R.string.f_Fs_auth_unset), visible = settings.password.isEmpty())
        }
    }
}

/** Stopped once nobody's asked for anything for a while, or never. */
@Composable
private fun IdleSetting(minutes: Int, onSelect: (Int) -> Unit) {
    val options = ShareSettings.IDLE_CHOICES.map { m ->
        m to when {
            m == 0 -> stringResource(R.string.f_Fs_idle_never)
            m % 60 == 0 -> stringResource(R.string.f_Fs_idle_hours, m / 60)
            else -> stringResource(R.string.f_Fs_idle_minutes, m)
        }
    }
    OptionSetting(stringResource(R.string.f_Fs_idle), Icons.Default.Timer, minutes, options, onSelect = onSelect)
}

/**
 * Bore's tunnel, turned on or off: on, bore is installed (asked first; its row telling how it goes) and its
 * settings unfold, and it starts if the folder's shared; off, they fold away, its tunnel stopped (bore
 * uninstalled once no tool has it on).
 */
@Composable
private fun BoreSection(settings: ShareSettings, set: (String, Any) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val available = remember { LinuxParts.isAvailable(context, listOf(Bore.PART)) }
    Column(verticalArrangement = RowArrangement) {
        SwitchButton(
            stringResource(R.string.f_Srv_bore_enable), Icons.Default.SettingsEthernet, settings.bore, enabled = available
        ) { on ->
            set(F.S_BORE, on)
            if (on) scope.launch { if (FolderShare.boreOn(context) == InstallResult.Declined) set(F.S_BORE, false) }
            else FolderShare.boreOff(context)
        }
        RevealSection(settings.bore) {
            PartPackage(FolderShareFeature, Bore.PART)
            val bore = remember { FolderShare.boreOf(context) }
            BoreServerSettings(bore, settings.boreServer) { server ->
                set(F.S_BORE_SERVER, server)
                // Another server: another port, and its tunnel there now
                set(F.S_BORE_PORT, 0)
                if (FolderShare.isRunning) FolderShare.boreOff(context, restart = true)
            }
        }
        Note(stringResource(R.string.f_Fs_bore_desc))
    }
}

/** Cloudflare's quick tunnel, turned on or off: as Bore's, nothing to set up. */
@Composable
private fun CloudflareSection(settings: ShareSettings, set: (String, Any) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val available = remember { LinuxParts.isAvailable(context, listOf(Tunnel.PART)) }
    Column(verticalArrangement = RowArrangement) {
        SwitchButton(
            stringResource(R.string.f_Srv_tunnel), Icons.Default.Cloud, settings.cloudflare, enabled = available
        ) { on ->
            set(F.S_CLOUDFLARE, on)
            if (on) scope.launch { if (FolderShare.tunnelOn(context) == InstallResult.Declined) set(F.S_CLOUDFLARE, false) }
            else FolderShare.tunnelOff(context)
        }
        RevealSection(settings.cloudflare) {
            PartPackage(FolderShareFeature, Tunnel.PART)
        }
        Note(stringResource(R.string.f_Fs_cloudflare_desc))
    }
}
