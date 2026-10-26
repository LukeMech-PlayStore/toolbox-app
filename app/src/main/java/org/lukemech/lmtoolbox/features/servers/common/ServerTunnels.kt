package org.lukemech.lmtoolbox.features.servers.common

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureRegistry
import org.lukemech.lmtoolbox.linux.InstallResult
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.linux.parts.Bore
import org.lukemech.lmtoolbox.linux.parts.Tunnel

/**
 * The tunnels a tool's server is reached through from anywhere, Cloudflare's ([Tunnel]) and bore's ([Bore]),
 * started and stopped with it: one of its servers' at a time, as [ServerTunnel.serverId] tells. Shared by the
 * tools serving something from the phone (the Minecraft server, the folder share): [source] is the tool, as
 * the bottom bar and its failures show it; [log] gets each line the tunnels print.
 *
 * Its parts are installed when a server's tunnel is turned on ([install]) and uninstalled once no tool has one
 * on ([prune]): each tool tells which it uses (AppFeature.usesExtraExtension).
 */
class ServerTunnels(private val source: AppFeature, private val log: (String) -> Unit = {}) {
    /** The Cloudflare tunnel of a server ([serverId]) as it's doing. */
    data class ServerTunnel(val serverId: String, val state: Tunnel.State) {
        val isActive get() = state.isActive
    }

    /** The bore tunnel of a server ([serverId]) as it's doing. */
    data class ServerBore(val serverId: String, val state: Bore.State) {
        val isActive get() = state.isActive
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tunnel = MutableStateFlow<ServerTunnel?>(null)
    /** The tunnel of the server running (or that ran last), as cloudflared's API tells. */
    val tunnel: StateFlow<ServerTunnel?> = _tunnel.asStateFlow()

    private val _bore = MutableStateFlow<ServerBore?>(null)
    /** The bore tunnel of the server running (or that ran last), as bore's API tells. */
    val bore: StateFlow<ServerBore?> = _bore.asStateFlow()

    /** The tunnels under way (one each, with their server), their states passed on to [tunnel] and [bore]. */
    private var currentTunnel: Tunnel? = null
    private var tunnelJob: Job? = null
    private var currentBore: Bore? = null
    private var boreJob: Job? = null

    init {
        synchronized(all) { all += this }
    }

    /** Whether server [serverId]'s Cloudflare tunnel is on its way up or connected. */
    fun tunnelRuns(serverId: String) = _tunnel.value?.let { it.serverId == serverId && it.isActive } == true

    /** Whether server [serverId]'s bore tunnel is on its way up or connected. */
    fun boreRuns(serverId: String) = _bore.value?.let { it.serverId == serverId && it.isActive } == true

    /**
     * Server [serverId]'s Cloudflare tunnel ([tunnel], once cloudflared is there): the one set up in Cloudflare's
     * dashboard (its token in), routing what its public hostname gets to the server; or with [localUrl], a quick
     * tunnel to that (an address of its own on trycloudflare.com, nothing to set up). Failed, told in the one
     * place errors are, the bottom bar ([label]: what it's for, e.g. the server's name).
     */
    fun startTunnel(context: Context, serverId: String, label: String, tunnel: Tunnel, localUrl: String? = null) {
        if (_tunnel.value?.isActive == true) return
        // There (see install), with its token for the dashboard's
        if (!Tunnel.isInstalled(context) || (localUrl == null && !tunnel.hasToken)) return
        currentTunnel = tunnel
        tunnelJob?.cancel()
        tunnelJob = scope.launch {
            tunnel.state.collect { state ->
                state ?: return@collect
                // Failed (not stopped): told in the one place errors are, the bottom bar
                if (state.phase == Tunnel.Phase.Failed && _tunnel.value?.state?.phase != Tunnel.Phase.Failed) {
                    LinuxTasks.reportFailure(source, label, "Cloudflare tunnel: ${state.error ?: "failed"}")
                }
                _tunnel.value = ServerTunnel(serverId, state)
            }
        }
        val onLine = { line: String -> log("[cloudflared] $line") }
        if (localUrl == null) tunnel.start(context, onLine) else tunnel.startQuick(context, localUrl, onLine)
    }

    fun stopTunnel() {
        currentTunnel?.stop()
    }

    /**
     * Server [serverId]'s bore tunnel ([bore], once bore is there), forwarding [localPort] from [server]: the port
     * it asks for ([port], the one it had last; 0: any) is passed to [onConnected] once it has one, to ask for it
     * again next time.
     */
    fun startBore(
        context: Context, serverId: String, bore: Bore, localPort: Int, server: String, port: Int,
        onConnected: (port: Int) -> Unit = {}
    ) {
        if (_bore.value?.isActive == true) return
        if (!Bore.isInstalled(context)) return
        val app = context.applicationContext
        currentBore = bore
        boreJob?.cancel()
        boreJob = scope.launch {
            bore.state.collect { state ->
                state ?: return@collect
                // Failed: told in its status row alone (the server runs on without it), not as the server's failed task
                if (state.phase == Bore.Phase.Connected) onConnected(state.port)
                _bore.value = ServerBore(serverId, state)
            }
        }
        bore.start(app, localPort, server, port) { line -> log("[bore] $line") }
    }

    fun stopBore() {
        currentBore?.stop()
    }

    /** Stops server [serverId]'s Cloudflare tunnel if it runs, until it has. */
    suspend fun stopTunnelOf(serverId: String) {
        if (!tunnelRuns(serverId)) return
        stopTunnel()
        _tunnel.first { it?.isActive != true }
    }

    /** Stops server [serverId]'s bore tunnel if it runs, until it has. */
    suspend fun stopBoreOf(serverId: String) {
        if (!boreRuns(serverId)) return
        stopBore()
        _bore.first { it?.isActive != true }
    }

    /** Shown as failed (e.g. its part couldn't be installed), for server [serverId]: [error] says why. */
    fun tunnelFailed(serverId: String, error: String) {
        _tunnel.value = ServerTunnel(serverId, Tunnel.State(Tunnel.Phase.Failed, error = error))
    }

    fun boreFailed(serverId: String, error: String) {
        _bore.value = ServerBore(serverId, Bore.State(Bore.Phase.Failed, error = error))
    }

    /**
     * Installs a tunnel's [part] (Tunnel.PART, Bore.PART), one turned on: asked first (InstallPrompt), then its
     * table on the page tells how it goes; once accepted, it goes on whatever the caller does.
     */
    suspend fun install(context: Context, part: String): InstallResult {
        val app = context.applicationContext
        if (LinuxParts.isExtensionInstalled(app, part)) return InstallResult.Installed
        return LinuxTasks.installParts(app, source, listOf(part))
    }

    companion object {
        /** Every tool's, for [prune]. */
        private val all = mutableListOf<ServerTunnels>()

        /**
         * Uninstalls a tunnel's [part] once no tool has a tunnel on it turned on (see AppFeature.usesExtraExtension)
         * and none runs.
         */
        fun prune(context: Context, part: String) {
            val app = context.applicationContext
            val running = synchronized(all) { all.toList() }.any {
                when (part) {
                    Tunnel.PART -> it._tunnel.value?.isActive == true
                    Bore.PART -> it._bore.value?.isActive == true
                    else -> false
                }
            }
            if (running || FeatureRegistry.features.any { it.usesExtraExtension(app, part) }) return
            if (LinuxParts.isExtensionUnpacked(app, part) || LinuxParts.isExtensionDownloaded(app, part)) LinuxTasks.removeParts(app, listOf(part))
        }
    }
}
