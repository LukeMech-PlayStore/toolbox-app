package org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.mc_server.MinecraftServerFeature
import org.lukemech.lmtoolbox.features.servers.mc_server.Network
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerProfile
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerRunner
import org.lukemech.lmtoolbox.features.servers.mc_server.Servers
import org.lukemech.lmtoolbox.linux.InstallResult
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.linux.parts.Tunnel
import org.lukemech.lmtoolbox.ui.components.ExpandableButton
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.features.servers.common.Reach
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.EditDialog
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.PartPackage
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.TextSetting

/**
 * Cloudflare's tunnel, turned on or off: on, cloudflared is installed (its row as the Java's table has
 * them, telling how it installs) and its settings unfold ([TunnelSettings]), and it starts if the server
 * runs (with its token in); off, they fold away, its tunnel stopped (cloudflared uninstalled once no
 * server has it on, see ServerRunner.pruneTunnel).
 */
@Composable
internal fun TunnelSection(profile: ServerProfile, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember(profile.id) { mutableStateOf(profile.tunnelEnabled) }
    fun save(on: Boolean) {
        enabled = on
        Servers.load(context, profile.id)?.let { Servers.save(context, it.copy(tunnelEnabled = on)) }
        onChanged()
    }
    Column(verticalArrangement = RowArrangement) {
        SwitchButton(stringResource(R.string.f_Srv_tunnel), Icons.Default.Cloud, enabled) { on ->
            save(on)
            // At once, the server running or not: on, installed (asked first; not wanted, it's off again) and
            // started if it runs (with its token in); off, stopped and uninstalled
            if (on) scope.launch { if (ServerRunner.tunnelOn(context, profile.id) == InstallResult.Declined) save(false) }
            else ServerRunner.tunnelOff(context, profile.id)
        }
        RevealSection(enabled) {
            PartPackage(MinecraftServerFeature, Tunnel.PART)
            TunnelSettings(profile, onChanged)
        }
        // What it's for: under its switch, its settings unfolding above it
        Note(stringResource(R.string.f_Mc_tunnel_desc))
    }
}

/** The Cloudflare tunnel's: its token, the address players type; checking the domain, and how to set it up. */
@Composable
private fun TunnelSettings(profile: ServerProfile, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tunnel = remember(profile.id) { ServerRunner.tunnelOf(context, profile) }
    var hasToken by remember(profile.id) { mutableStateOf(tunnel.hasToken) }
    var hostname by remember(profile.id) { mutableStateOf(profile.tunnelHostname) }
    var check by remember { mutableStateOf<Network.DomainCheck?>(null) }
    var checking by remember { mutableStateOf(false) }
    fun save(change: (ServerProfile) -> ServerProfile) {
        Servers.load(context, profile.id)?.let { Servers.save(context, change(it)) }
        onChanged()
    }

    Column(verticalArrangement = RowArrangement) {
        // As the address under it: a card, edited in a dialog (hidden as it's typed); saved, it can be removed
        var editingToken by remember { mutableStateOf(false) }
        FeatureButton(
            title = stringResource(R.string.f_Mc_token),
            subtitle = stringResource(if (hasToken) R.string.f_Srv_saved else R.string.f_Mc_token_desc),
            icon = Icons.Default.Key,
            buttonContent = if (hasToken) {
                {
                    IconButton(onClick = {
                        tunnel.setToken(null)
                        hasToken = false
                        if (ServerRunner.tunnel.value?.let { it.serverId == profile.id && it.isActive } == true) ServerRunner.stopTunnel()
                    }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.f_Srv_token_remove), tint = MaterialTheme.colorScheme.error) }
                }
            } else null,
            onClick = { editingToken = true }
        )
        // The token, or the whole installation command the dashboard shows
        if (editingToken) EditDialog(
            stringResource(R.string.f_Mc_token), "", stringResource(R.string.f_Mc_token_desc), KeyboardType.Password,
            secret = true, valid = { Tunnel.tokenIn(it) != null }, onDismiss = { editingToken = false }
        ) {
            editingToken = false
            tunnel.setToken(Tunnel.tokenIn(it))
            hasToken = tunnel.hasToken
            // The server running: its tunnel runs with it now
            scope.launch { ServerRunner.tunnelToken(context, profile.id) }
        }
        // The address players type and its check: once there's a token (no tunnel to reach before)
        RevealSection(hasToken) {
            TextSetting(stringResource(R.string.f_Mc_hostname), hostname, Icons.Default.Public, subtitle = stringResource(R.string.f_Mc_hostname_desc)) {
                hostname = Reach.hostnameOf(it)
                check = null
                save { p -> p.copy(tunnelHostname = hostname) }
            }
            // Whether players' Modflared finds the tunnel at that address
            FeatureButton(
                title = stringResource(R.string.f_Mc_check_domain),
                subtitle = when (val c = check) {
                    null -> if (checking) stringResource(R.string.f_Mc_checking) else stringResource(R.string.f_Mc_check_domain_desc)
                    Network.DomainCheck.NotFound -> stringResource(R.string.f_Mc_check_not_found, hostname)
                    Network.DomainCheck.NoTxt -> stringResource(R.string.f_Mc_check_no_txt)
                    is Network.DomainCheck.Ready -> c.route?.let { stringResource(R.string.f_Mc_check_route, it) } ?: stringResource(R.string.f_Mc_check_ready)
                    // Couldn't be checked: the bottom bar tells (see the check below)
                    is Network.DomainCheck.Failed -> stringResource(R.string.f_Mc_check_domain_desc)
                },
                icon = Icons.Default.Dns,
                onClick = {
                    if (hostname.isNotEmpty() && !checking) {
                        checking = true
                        check = null
                        scope.launch {
                            check = Network.checkDomain(hostname)
                            (check as? Network.DomainCheck.Failed)?.let {
                                LinuxTasks.reportFailure(MinecraftServerFeature, hostname, "Couldn't check the domain: ${it.message}")
                            }
                            checking = false
                        }
                    }
                }
            )
        }
        ExpandableButton(stringResource(R.string.f_Mc_tunnel_guide), Icons.AutoMirrored.Default.HelpOutline) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf(
                    R.string.f_Mc_guide_1, R.string.f_Mc_guide_2, R.string.f_Mc_guide_3,
                    R.string.f_Mc_guide_4, R.string.f_Mc_guide_5, R.string.f_Mc_guide_6
                ).forEachIndexed { i, step ->
                    Row {
                        Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                        Text(stringResource(step), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(R.string.f_Mc_guide_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
