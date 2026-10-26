package org.lukemech.lmtoolbox.features.servers.folder_share

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.net.BindException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.common.ServerTunnels
import org.lukemech.lmtoolbox.linux.InstallResult
import org.lukemech.lmtoolbox.linux.parts.Bore
import org.lukemech.lmtoolbox.linux.parts.Tunnel

/**
 * The folder shared, while it is: its server (FolderShareServer, kept going by [ShareService]), who's asked
 * for what lately ([Run.recent]), and its tunnels ([tunnels]: bore's, Cloudflare's quick one), which start and
 * stop with it as the Minecraft server's do. Stopped by hand, from its notification, or once nobody's asked for
 * anything a while (ShareSettings.idleMinutes).
 */
object FolderShare {
    /** Its tunnels' server, as ServerTunnels tells them apart (one share). */
    const val ID = "share"
    private const val RECENT = 30
    /** Who asked within it is connected, as the page counts them. */
    const val CONNECTED_MS = 120_000L
    /** Requests for the same file by the same one, this close: one row (a video's seeks). */
    private const val SAME_MS = 10_000L
    private val IDLE_CHECK = 15.seconds

    enum class Phase { Running, Stopped, Failed }

    data class Run(
        val phase: Phase,
        /** The folder's name, as its page and notification show it. */
        val name: String,
        val port: Int,
        val startedAt: Long = System.currentTimeMillis(),
        /** Why it stopped by itself (e.g. its port taken). */
        val error: String? = null,
        /** The latest requests, newest first (one row for many of the same file by the same one, see [SAME_MS]). */
        val recent: List<FolderShareServer.Access> = emptyList(),
        val requests: Int = 0,
        /** When each one asked last (an address; bore's tunnel tells none: by its Via), for who's connected. */
        val seen: Map<String, Long> = emptyMap(),
    ) {
        val isActive get() = phase == Phase.Running

        /** How many asked for something within [CONNECTED_MS] of [now]. */
        fun connected(now: Long = System.currentTimeMillis()) = seen.count { now - it.value < CONNECTED_MS }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<Run?>(null)
    val state: StateFlow<Run?> = _state.asStateFlow()

    /** Its tunnels: bore's to the share's port, Cloudflare's quick one to its address. */
    val tunnels = ServerTunnels(FolderShareFeature)

    /** The app, as the page or a start gave it (for what's done without one: the tool's data deleted). */
    @Volatile
    var app: Application? = null
        private set

    private var server: FolderShareServer? = null
    private var idleJob: Job? = null
    /** Requests being answered now (a long download is no idleness). */
    private val busy = AtomicInteger()
    @Volatile
    private var lastActivity = 0L

    fun attach(context: Context) {
        app = context.applicationContext as Application
    }

    /** The folder's name as its provider gives it (the tree's own document's), else the last part of its path. */
    fun folderName(context: Context, uri: Uri): String =
        runCatching { ShareTree(context.contentResolver, uri).root()?.name }.getOrNull()
            ?: runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').substringAfterLast('/') }.getOrNull().orEmpty()

    /** Shares the folder set (see ShareSettings), unless it's already shared or none is set. */
    fun start(context: Context) {
        if (_state.value?.isActive == true) return
        attach(context)
        val app = context.applicationContext
        val settings = ShareSettings.load(app)
        val folder = settings.folder ?: return
        val tree = ShareTree(app.contentResolver, folder)
        val name = tree.root()?.name
        if (name == null) {
            _state.value = Run(Phase.Failed, folderName(app, folder), settings.port, error = app.getString(R.string.f_Fs_folder_gone))
            return
        }
        val labels = Listing.Labels(
            lang = app.resources.configuration.locales[0].toLanguageTag(),
            home = name,
            empty = app.getString(R.string.f_Fs_web_empty),
            zip = app.getString(R.string.f_Fs_web_zip),
            download = app.getString(R.string.f_Fs_web_download),
            upload = app.getString(R.string.f_Fs_web_upload),
            uploading = app.getString(R.string.f_Fs_web_uploading, "{name}"),
            uploaded = app.getString(R.string.f_Fs_web_uploaded),
            uploadFailed = app.getString(R.string.f_Fs_web_upload_failed),
            footer = app.getString(R.string.f_Fs_web_footer, app.getString(R.string.app_name)),
        )
        val share = FolderShareServer(
            tree, settings.port, labels, settings = { ShareSettings.load(app) },
            onStarted = {
                busy.incrementAndGet()
                lastActivity = System.currentTimeMillis()
            },
            onServed = { access ->
                busy.decrementAndGet()
                lastActivity = System.currentTimeMillis()
                // Uploaded: the folder's listings and paths afresh
                if (access.method == "PUT") tree.changed()
                served(access)
            }
        )
        try {
            share.start()
        } catch (e: Exception) {
            val error = if (e is BindException) app.getString(R.string.f_Fs_port_taken, settings.port) else e.message ?: e.javaClass.simpleName
            _state.value = Run(Phase.Failed, name, settings.port, error = error)
            return
        }
        server = share
        busy.set(0)
        lastActivity = System.currentTimeMillis()
        _state.value = Run(Phase.Running, name, settings.port)
        ShareService.start(app)
        watchIdle(app)
        // Its tunnels turned on, each installed first if it's gone meanwhile (asked); not there after all, failed
        if (settings.bore) scope.launch {
            if (!tunnels.install(app, Bore.PART).ok) tunnels.boreFailed(ID, "bore isn't installed")
            else if (isRunning) startBore(app)
        }
        if (settings.cloudflare) scope.launch {
            if (!tunnels.install(app, Tunnel.PART).ok) tunnels.tunnelFailed(ID, "cloudflared isn't installed")
            else if (isRunning) startTunnel(app)
        }
    }

    /** Stops sharing (its tunnels with it); [error]: why, when it stops by itself. */
    fun stop(error: String? = null) {
        val run = _state.value?.takeIf { it.isActive } ?: return
        idleJob?.cancel()
        server?.stop()
        server = null
        tunnels.stopTunnel()
        tunnels.stopBore()
        _state.value = run.copy(phase = if (error != null) Phase.Failed else Phase.Stopped, error = error)
    }

    val isRunning get() = _state.value?.isActive == true

    /** Its request, among the latest: one row with the one before for the same file by the same one. */
    private fun served(access: FolderShareServer.Access) = _state.update { run ->
        run?.takeIf { it.isActive } ?: return@update run
        val last = run.recent.firstOrNull()
        val same = last != null && last.client == access.client && last.via == access.via && last.path == access.path &&
            last.method == access.method && access.at - last.at < SAME_MS
        val recent = if (same) listOf(access.copy(id = last.id, bytes = last.bytes + access.bytes)) + run.recent.drop(1)
        else (listOf(access.copy(id = run.requests + 1L)) + run.recent).take(RECENT)
        run.copy(recent = recent, requests = run.requests + 1, seen = run.seen + ((access.client ?: access.via.name) to access.at))
    }

    /** Stops it once nobody's asked for anything for its idle time (none set: never), a download under way not idle. */
    private fun watchIdle(app: Context) {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isActive && isRunning) {
                delay(IDLE_CHECK)
                val minutes = ShareSettings.load(app).idleMinutes
                if (minutes > 0 && busy.get() <= 0 && System.currentTimeMillis() - lastActivity > minutes * 60_000L) {
                    stop()
                    break
                }
            }
        }
    }

    /** The share's address on the phone, for its tunnels: 127.0.0.1 ("localhost" may be IPv6's first). */
    private fun local(port: Int) = "http://127.0.0.1:$port"

    private fun bore(app: Context) = Bore(
        FolderShareFeature, java.io.File(FolderShareFeature.dir(app), ".bore-secret"),
        label = "${_state.value?.name.orEmpty()} · Bore", group = _state.value?.name
    )

    /** Its bore tunnel, for the settings' secret too. */
    fun boreOf(context: Context) = bore(context.applicationContext)

    private fun startBore(app: Context) {
        val run = _state.value?.takeIf { it.isActive } ?: return
        val s = ShareSettings.load(app)
        if (!s.bore) return
        tunnels.startBore(app, ID, bore(app), run.port, s.boreServer.ifEmpty { Bore.SERVER }, s.borePort) { port ->
            // Its port, asked for again next time
            if (ShareSettings.load(app).borePort != port) ShareSettings.put(app, FolderShareFeature.S_BORE_PORT, port)
        }
    }

    private fun startTunnel(app: Context) {
        val run = _state.value?.takeIf { it.isActive } ?: return
        if (!ShareSettings.load(app).cloudflare) return
        val tunnel = Tunnel(FolderShareFeature, tokenFile = null, label = "${run.name} · Cloudflare", group = run.name)
        tunnels.startTunnel(app, ID, run.name, tunnel, localUrl = local(run.port))
    }

    /** Bore turned on: installed (asked first) and, while it's shared, started at once. */
    suspend fun boreOn(context: Context): InstallResult {
        val app = context.applicationContext
        return tunnels.install(app, Bore.PART).also { if (it.ok && isRunning) startBore(app) }
    }

    /** Bore turned off (or its server changed, then [restart]ed): stopped at once, uninstalled once no tool has it on. */
    fun boreOff(context: Context, restart: Boolean = false) {
        val app = context.applicationContext
        scope.launch {
            tunnels.stopBoreOf(ID)
            if (restart) startBore(app) else ServerTunnels.prune(app, Bore.PART)
        }
    }

    /** Cloudflare's quick tunnel turned on: installed (asked first) and, while it's shared, started at once. */
    suspend fun tunnelOn(context: Context): InstallResult {
        val app = context.applicationContext
        return tunnels.install(app, Tunnel.PART).also { if (it.ok && isRunning) startTunnel(app) }
    }

    fun tunnelOff(context: Context) {
        val app = context.applicationContext
        scope.launch {
            tunnels.stopTunnelOf(ID)
            ServerTunnels.prune(app, Tunnel.PART)
        }
    }
}
