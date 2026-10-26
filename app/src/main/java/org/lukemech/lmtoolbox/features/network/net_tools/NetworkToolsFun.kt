package org.lukemech.lmtoolbox.features.network.net_tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.ResourceTile
import org.lukemech.lmtoolbox.ui.pages.features.network.net_tools.NetworkToolsPage

/**
 * Network tools: ping, traceroute, the LAN's devices and a host's ports are Android's own (its ping, Java's
 * sockets, NsdManager), nothing to download; iperf3 comes in a part of the subsystem ([Iperf.PART]), installed
 * when its tab first runs it.
 */
object NetworkToolsFeature : AppFeature {
    override val route = "net_tools"
    override val titleStringResId: Int = R.string.fp_NetTools
    override val keywordsResId: Int = R.string.fk_NetTools
    override val descriptionResId: Int = R.string.fd_NetTools
    override val category = FeatureCategory.Network
    override val icon @Composable get() = Icons.Default.NetworkCheck

    /** The tab opened last. */
    const val S_TAB = "NetTools_tab"
    // What was typed last, each tab's own (see NetworkToolsPage)
    const val S_PING = "NetTools_ping"
    const val S_TRACE = "NetTools_trace"
    const val S_PORTS = "NetTools_ports"
    const val S_IPERF = "NetTools_iperf"
    /** Android 17's local network permission asked for once (Android shows its dialog until it's denied twice). */
    const val S_LOCAL_ASKED = "NetTools_localAsked"

    override val preferenceKeys = listOf(S_TAB, S_PING, S_TRACE, S_PORTS, S_IPERF, S_LOCAL_ASKED)

    override val extraExtensions = listOf(Iperf.PART)

    // iperf3's traffic
    override val resourceTiles = listOf(ResourceTile.Network, ResourceTile.Cpu)

    @Composable
    override fun Content(arguments: Map<String, String?>?) = NetworkToolsPage()

    /** Whether Android asks before the app reaches the local network (its devices, mDNS): 17 does, for an app targeting it. */
    val asksForLocalNetwork get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN

    /** Whether the local network is the app's to reach: always before Android 17, else as the user allowed. */
    fun hasLocalNetwork(context: Context) = !asksForLocalNetwork ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
}
