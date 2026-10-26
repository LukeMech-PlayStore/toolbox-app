package org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.mc_server.MinecraftServerFeature
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerProfile
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerRunner
import org.lukemech.lmtoolbox.helpers.ResourceScope
import org.lukemech.lmtoolbox.linux.parts.Bore
import org.lukemech.lmtoolbox.linux.parts.Tunnel
import org.lukemech.lmtoolbox.ui.components.ConfirmDialog
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.InfoTile
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.REVEAL_MS
import org.lukemech.lmtoolbox.ui.components.ResourceTiles
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.withCode
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.features.servers.common.Reach
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.AddressButton
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.TunnelPhase
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.TunnelStatus
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.networkChanges
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.openTailscale
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.uptime

/** How it's doing and what it takes, who's on, and the addresses to join it at (the network's, the tunnels', the tailnet's). */
@Composable
internal fun StatusTab(profile: ServerProfile, run: ServerRunner.Run?, onTunnelSettings: () -> Unit) {
    val context = LocalContext.current
    val tunnelState by ServerRunner.tunnel.collectAsState()
    val tunnel = tunnelState?.takeIf { it.serverId == profile.id }?.state
    val boreState by ServerRunner.bore.collectAsState()
    val bore = boreState?.takeIf { it.serverId == profile.id }?.state
    // The one it runs on, not one changed meanwhile (that's for its restart)
    val port = remember(profile.id, run?.phase, run?.startValues) { ServerRunner.port(context, profile.id) }
    // Again as the phone's networks change (Wi-Fi, Tailscale connecting), not just as it starts or stops
    val networks = networkChanges(context)
    val lan = remember(run?.phase, networks) { Reach.lanAddresses(context) }
    val tailnet = remember(run?.phase, networks) { Reach.tailscaleAddresses(context) }
    // MagicDNS's names, as they're looked up (the addresses until then, or without it)
    val tailnetNames by produceState(emptyMap(), tailnet) {
        value = withContext(Dispatchers.IO) { tailnet.mapNotNull { ip -> Reach.tailscaleName(ip)?.let { ip to it } }.toMap() }
    }
    val hasToken = remember(profile.id, run?.phase) { ServerRunner.tunnelOf(context, profile).hasToken }

    Column(verticalArrangement = RowArrangement) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhaseDot(run, 18.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(phaseText(run), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                }
                Text(
                    profile.description + " · " + memoryText(profile.memoryMb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // What it takes, while it runs (its run's processes: PRoot, Java), how long it's been up and who's on;
        // the last run's while it folds away
        val lastRun = remember { arrayOfNulls<ServerRunner.Run>(1) }
        if (run?.isActive == true) lastRun[0] = run
        Reveal(visible = run?.isActive == true) {
            lastRun[0]?.let { r ->
                ResourceTiles(
                    MinecraftServerFeature, Modifier.padding(horizontal = 16.dp), ResourceScope.Task(r.taskId),
                    extra = listOf(
                        InfoTile(stringResource(R.string.f_Srv_stat_uptime), uptime(r.readyAt ?: r.startedAt), Icons.Default.Schedule),
                        InfoTile(
                            stringResource(R.string.f_Mc_stat_players),
                            r.online?.let { o -> "$o" + (r.maxPlayers?.let { "/$it" } ?: "") } ?: "–", Icons.Default.Group
                        ),
                    )
                )
            }
        }

        // Each one's row, unfolding as they join and folding away as they leave (kicked or banned too)
        val players = run?.takeIf { it.phase == ServerRunner.Phase.Running }?.players.orEmpty()
        val seen = remember(profile.id) { mutableStateListOf<String>() }
        players.forEach { if (it !in seen) seen += it }
        seen.forEach { name -> Reveal(visible = name in players) { PlayerRow(name) } }

        Section(stringResource(R.string.f_Mc_join))
        val portSuffix = if (port == 25565) "" else ":$port"
        // Fading into each other as the networks change
        Crossfade(lan, animationSpec = tween(REVEAL_MS), label = "lan") { addresses ->
            Column(verticalArrangement = RowArrangement) {
                if (addresses.isEmpty()) {
                    FeatureButton(title = stringResource(R.string.f_Srv_lan), subtitle = stringResource(R.string.f_Srv_lan_none), icon = Icons.Default.Wifi)
                } else {
                    addresses.forEach { ip -> AddressButton(stringResource(R.string.f_Srv_lan), "$ip$portSuffix", Icons.Default.Wifi) }
                }
            }
        }
        if (profile.boreEnabled) {
            // Starts with the server by itself: its address once it's connected (bore.pub's port: the same as last
            // time while it's free), else how it's doing; why it failed in red (the bottom bar tells too)
            val address = bore?.address?.takeIf { bore.phase == Bore.Phase.Connected }
            Crossfade(address, animationSpec = tween(REVEAL_MS), label = "bore") { a ->
                if (a != null) AddressButton(stringResource(R.string.f_Srv_through_bore), a, Icons.Default.Public)
                else TunnelStatus(
                    stringResource(R.string.f_Srv_bore),
                    when (bore?.phase) {
                        Bore.Phase.Connecting -> TunnelPhase.Connecting
                        Bore.Phase.Failed -> TunnelPhase.Failed
                        else -> TunnelPhase.Off
                    },
                    bore?.error,
                    Icons.Default.SettingsEthernet
                )
            }
            Note(stringResource(R.string.f_Mc_internet_bore_desc))
        }

        val hostname = profile.tunnelHostname
        if (profile.tunnelEnabled && hasToken && hostname.isNotEmpty()) {
            AddressButton(stringResource(R.string.f_Srv_through_cloudflare), hostname, Icons.Default.Public)
            TunnelStatus(
                stringResource(R.string.f_Srv_tunnel),
                when (tunnel?.phase) {
                    Tunnel.Phase.Connected -> TunnelPhase.Connected(stringResource(R.string.f_Mc_tunnel_connected, tunnel.connections))
                    Tunnel.Phase.Connecting -> TunnelPhase.Connecting
                    Tunnel.Phase.Failed -> TunnelPhase.Failed
                    else -> TunnelPhase.Off
                },
                tunnel?.error,
                Icons.Default.Cloud, Icons.Default.CloudOff
            )
            Note(stringResource(R.string.f_Mc_internet_desc))
        } else if (!profile.boreEnabled) {
            // Neither set up: to their settings
            FeatureButton(
                title = stringResource(R.string.f_Mc_internet),
                subtitle = stringResource(R.string.f_Mc_internet_none),
                icon = Icons.Default.Public,
                icon2 = Icons.AutoMirrored.Default.KeyboardArrowRight,
                onClick = onTunnelSettings
            )
        }

        // After the tunnels: the tailnet's (the Tailscale app's, also from anywhere)
        Crossfade(tailnet, animationSpec = tween(REVEAL_MS), label = "tailnet") { addresses ->
            Column(verticalArrangement = RowArrangement) {
                if (addresses.isEmpty()) {
                    // Not connected: to the Tailscale app (or its page on Play)
                    FeatureButton(
                        title = stringResource(R.string.f_Srv_tailscale),
                        subtitle = stringResource(R.string.f_Mc_tailscale_none),
                        icon = Icons.Default.VpnLock,
                        icon2 = Icons.AutoMirrored.Default.KeyboardArrowRight,
                        onClick = { openTailscale(context) }
                    )
                } else {
                    addresses.forEach { ip ->
                        // Its MagicDNS name once it's looked up (with MagicDNS on in the tailnet), and its address, which always works
                        Reveal(visible = tailnetNames[ip] != null) {
                            tailnetNames[ip]?.let { AddressButton(stringResource(R.string.f_Srv_tailscale), "$it$portSuffix", Icons.Default.VpnLock) }
                        }
                        AddressButton(stringResource(R.string.f_Srv_tailscale_ip), "$ip$portSuffix", Icons.Default.VpnLock)
                    }
                    Note(stringResource(R.string.f_Mc_tailscale_desc))
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Note(stringResource(R.string.f_Mc_battery_note))
    }
}

/**
 * A player who's on: banning them (asked first, with how to undo it) or kicking them out, at the far right;
 * either as typed in the console (its answer there too).
 */
@Composable
private fun PlayerRow(name: String) {
    val scope = rememberCoroutineScope()
    var confirmBan by remember { mutableStateOf(false) }
    fun command(cmd: String) = scope.launch {
        ServerRunner.console(cmd).onFailure { LinuxTasks.reportFailure(MinecraftServerFeature, name, "Couldn't $cmd: ${it.message}") }
    }
    FeatureButton(
        title = name,
        icon = Icons.Default.Person,
        buttonContent = {
            IconButton(onClick = { confirmBan = true }) {
                Icon(Icons.Default.Block, contentDescription = stringResource(R.string.f_Mc_ban), tint = MaterialTheme.colorScheme.error)
            }
        },
        buttonContent2 = {
            IconButton(onClick = { command("kick $name") }) {
                Icon(Icons.AutoMirrored.Default.Logout, contentDescription = stringResource(R.string.f_Mc_kick), tint = MaterialTheme.colorScheme.primary)
            }
        },
        buttonsInColumn = false
    )
    if (confirmBan) ConfirmDialog(
        // The command that undoes it in a terminal's font, as it's typed
        Icons.Default.Block, stringResource(R.string.f_Mc_ban_title, name), withCode(stringResource(R.string.f_Mc_ban_confirm, name, "pardon $name"), "pardon $name"),
        stringResource(R.string.f_Mc_ban), onDismiss = { confirmBan = false }
    ) {
        confirmBan = false
        command("ban $name")
    }
}
