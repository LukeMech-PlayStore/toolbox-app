package org.lukemech.lmtoolbox.ui.pages.features.servers.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.common.Reach
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.REVEAL_MS
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RowArrangement

/**
 * Where a tool serving from the phone is reached, alike for each (the Minecraft server, the folder share): on this
 * network, then from anywhere through its [tunnels] (none turned on, [tunnelsOn] false: a row saying so, to their
 * settings with [onNoTunnel]), then the tailnet's (the Tailscale app's). [address]: a host (an IP, a name) as the
 * tool's clients take it, its port with it. The addresses are looked up again as the phone's networks change and
 * as [refresh] does (e.g. the server starting).
 */
@Composable
internal fun ReachSection(
    address: (host: String) -> String,
    tunnelsOn: Boolean,
    onNoTunnel: (() -> Unit)? = null,
    refresh: Any? = null,
    tunnels: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val networks = networkChanges(context)
    val lan = remember(refresh, networks) { Reach.lanAddresses(context) }
    val tailnet = remember(refresh, networks) { Reach.tailscaleAddresses(context) }
    // MagicDNS's names, as they're looked up (the addresses until then, or without it)
    val tailnetNames by produceState(emptyMap(), tailnet) {
        value = withContext(Dispatchers.IO) { tailnet.mapNotNull { ip -> Reach.tailscaleName(ip)?.let { ip to it } }.toMap() }
    }

    Column(verticalArrangement = RowArrangement) {
        // Fading into each other as the networks change
        Crossfade(lan, animationSpec = tween(REVEAL_MS), label = "lan") { addresses ->
            Column(verticalArrangement = RowArrangement) {
                if (addresses.isEmpty()) {
                    FeatureButton(title = stringResource(R.string.f_Srv_lan), subtitle = stringResource(R.string.f_Srv_lan_none), icon = Icons.Default.Wifi)
                } else {
                    addresses.forEach { ip -> AddressButton(stringResource(R.string.f_Srv_lan), address(ip), Icons.Default.Wifi, shareable = true) }
                }
            }
        }
        tunnels()
        Reveal(visible = !tunnelsOn) {
            FeatureButton(
                title = stringResource(R.string.f_Srv_internet),
                subtitle = stringResource(R.string.f_Srv_internet_none),
                icon = Icons.Default.Public,
                icon2 = onNoTunnel?.let { Icons.AutoMirrored.Default.KeyboardArrowRight },
                onClick = { onNoTunnel?.invoke() }
            )
        }
        // After the tunnels: the tailnet's (the Tailscale app's, also from anywhere)
        Crossfade(tailnet, animationSpec = tween(REVEAL_MS), label = "tailnet") { addresses ->
            Column(verticalArrangement = RowArrangement) {
                if (addresses.isEmpty()) {
                    // Not connected: to the Tailscale app (or its page on Play)
                    FeatureButton(
                        title = stringResource(R.string.f_Srv_tailscale),
                        subtitle = stringResource(R.string.f_Srv_tailscale_none),
                        icon = Icons.Default.VpnLock,
                        icon2 = Icons.AutoMirrored.Default.KeyboardArrowRight,
                        onClick = { openTailscale(context) }
                    )
                } else {
                    addresses.forEach { ip ->
                        // Its MagicDNS name once it's looked up (with MagicDNS on in the tailnet), and its address, which always works
                        Reveal(visible = tailnetNames[ip] != null) {
                            tailnetNames[ip]?.let { AddressButton(stringResource(R.string.f_Srv_tailscale), address(it), Icons.Default.VpnLock, shareable = true) }
                        }
                        AddressButton(stringResource(R.string.f_Srv_tailscale_ip), address(ip), Icons.Default.VpnLock, shareable = true)
                    }
                    Note(stringResource(R.string.f_Srv_tailscale_desc))
                }
            }
        }
    }
}
