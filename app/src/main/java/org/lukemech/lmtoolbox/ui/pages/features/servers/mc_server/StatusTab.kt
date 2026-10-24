package org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.mc_server.MinecraftServerFeature
import org.lukemech.lmtoolbox.features.servers.mc_server.Network
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerProfile
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerRunner
import org.lukemech.lmtoolbox.features.servers.mc_server.Servers
import org.lukemech.lmtoolbox.helpers.ResourceScope
import org.lukemech.lmtoolbox.linux.parts.Tunnel
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.InfoTile
import org.lukemech.lmtoolbox.ui.components.REVEAL_MS
import org.lukemech.lmtoolbox.ui.components.ResourceTiles
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RowArrangement

/** How it's doing and what it takes, who's on, and the addresses to join it at. */
@Composable
internal fun StatusTab(profile: ServerProfile, run: ServerRunner.Run?, onTunnelSettings: () -> Unit) {
    val context = LocalContext.current
    val tunnelState by ServerRunner.tunnel.collectAsState()
    val tunnel = tunnelState?.takeIf { it.serverId == profile.id }?.state
    val port = remember(profile.id, run?.phase) { Servers.properties(context, profile.id).get("server-port")?.toIntOrNull() ?: 25565 }
    val lan = remember(run?.phase) { Network.lanAddresses(context) }
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
                        InfoTile(stringResource(R.string.f_Mc_stat_uptime), uptime(r.readyAt ?: r.startedAt), Icons.Default.Schedule),
                        InfoTile(
                            stringResource(R.string.f_Mc_stat_players),
                            r.online?.let { o -> "$o" + (r.maxPlayers?.let { "/$it" } ?: "") } ?: "–", Icons.Default.Group
                        ),
                    )
                )
            }
        }

        val players = run?.takeIf { it.phase == ServerRunner.Phase.Running }?.players.orEmpty()
        val lastPlayers = remember { arrayOf(emptyList<String>()) }
        if (players.isNotEmpty()) lastPlayers[0] = players
        Reveal(visible = players.isNotEmpty()) {
            FeatureButton(title = lastPlayers[0].joinToString(", "), icon = Icons.Default.Group)
        }

        Section(stringResource(R.string.f_Mc_join))
        val portSuffix = if (port == 25565) "" else ":$port"
        if (lan.isEmpty()) {
            FeatureButton(title = stringResource(R.string.f_Mc_lan), subtitle = stringResource(R.string.f_Mc_lan_none), icon = Icons.Default.Wifi)
        } else {
            lan.forEach { ip -> AddressButton(stringResource(R.string.f_Mc_lan), "$ip$portSuffix", Icons.Default.Wifi) }
        }

        val hostname = profile.tunnelHostname
        if (profile.tunnelEnabled && hasToken && hostname.isNotEmpty()) {
            AddressButton(stringResource(R.string.f_Mc_internet), hostname, Icons.Default.Public)
            val (tunnelText, tunnelIcon) = when (tunnel?.phase) {
                Tunnel.Phase.Connected -> stringResource(R.string.f_Mc_tunnel_connected, tunnel.connections) to Icons.Default.Cloud
                Tunnel.Phase.Connecting -> stringResource(R.string.f_Mc_tunnel_connecting) to Icons.Default.Cloud
                Tunnel.Phase.Failed -> stringResource(R.string.f_Mc_tunnel_failed) to Icons.Default.CloudOff
                else -> stringResource(R.string.f_Mc_tunnel_off) to Icons.Default.CloudOff
            }
            FeatureButton(
                title = stringResource(R.string.f_Mc_tunnel),
                // Why it failed: the bottom bar tells (see ServerRunner.startTunnel)
                subtitle = tunnelText,
                icon = tunnelIcon,
                buttonContent = {
                    // Stopping it, or starting it while the server runs: one fading into the other
                    val stop = if (tunnel?.isActive == true) true else if (run?.isActive == true) false else null
                    Crossfade(stop, animationSpec = tween(REVEAL_MS), label = "tunnelButton") { s ->
                        when (s) {
                            true -> TextButton(onClick = { ServerRunner.stopTunnel() }) { Text(stringResource(R.string.f_Mc_stop)) }
                            false -> TextButton(onClick = { ServerRunner.startTunnel(context, profile) }) { Text(stringResource(R.string.f_Mc_start)) }
                            null -> {}
                        }
                    }
                }
            )
            Note(stringResource(R.string.f_Mc_internet_desc))
        } else {
            // Not set up: to its settings
            FeatureButton(
                title = stringResource(R.string.f_Mc_internet),
                subtitle = stringResource(R.string.f_Mc_internet_none),
                icon = Icons.Default.Public,
                icon2 = Icons.AutoMirrored.Default.KeyboardArrowRight,
                onClick = onTunnelSettings
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Note(stringResource(R.string.f_Mc_battery_note))
    }
}

/** How long it's been since [since], e.g. "1 h 23 min", "5 min"; again each minute. */
@Composable
private fun uptime(since: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30.seconds)
        }
    }
    val minutes = ((now - since) / 60_000).coerceAtLeast(0)
    return if (minutes >= 60) stringResource(R.string.f_Mc_uptime_hm, minutes / 60, minutes % 60)
    else stringResource(R.string.f_Mc_uptime_m, minutes)
}

/** An address players type, copied with a tap. */
@Composable
private fun AddressButton(title: String, address: String, icon: ImageVector) {
    val context = LocalContext.current
    FeatureButton(
        title = address,
        subtitle = title,
        titleSubtitleSwap = true,
        icon = icon,
        buttonContent = {
            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.f_Mc_copy), tint = MaterialTheme.colorScheme.primary)
        },
        onClick = { copy(context, address) }
    )
}

// A setter: primaryClip is a val in Kotlin (its getter is nullable, the setter isn't)
@Suppress("UsePropertyAccessSyntax")
internal fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(text, text))
    Toast.makeText(context, R.string.f_Mc_copied, Toast.LENGTH_SHORT).show()
}
