package org.lukemech.lmtoolbox.features.servers.mc_server

import org.lukemech.lmtoolbox.linux.InstallResult
import android.app.Application
import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.lukemech.lmtoolbox.features.servers.common.ServerTunnels
import org.lukemech.lmtoolbox.linux.parts.Bore
import org.lukemech.lmtoolbox.linux.parts.Tunnel
import org.lukemech.lmtoolbox.helpers.deleteTree
import org.lukemech.lmtoolbox.linux.parts.Java
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.LinuxTasks

/**
 * The running server (one at a time): Java in the subsystem as a service of the tool's (not a task:
 * see LinuxTasks.runService; kept going by [ServerService]), its console as it prints it ([log]),
 * what it says of itself and what it takes (memory, CPU); commands go to it over RCON. Its Cloudflare
 * tunnel ([tunnel]) and its bore tunnel ([bore]) run next to it (see ServerTunnels).
 */
object ServerRunner {
    private const val TAG = "ServerRunner"
    private const val LOG_LINES = 500
    /** How long a Java left running after its run has to go once killed (see [killLeftover]). */
    private val KILL_WAIT = 10.seconds
    private const val RCON_PORT = 25575
    private val PLAYERS_EVERY = 20.seconds
    private const val SEED_TRIES = 10
    private val SEED_EVERY = 3.seconds
    /** How long a server has to save its world and quit once told to stop, before it's killed. */
    private val STOP_TIMEOUT = 90.seconds
    /** Its memory and Java aren't properties: their keys among [Run.startValues]. */
    const val MEMORY = "#memory"
    const val JAVA = "#java"
    /**
     * The properties a running server takes without a restart: the console command that sets each
     * (null: none needed, as enforce-whitelist with the whitelist's own).
     */
    private val LIVE: Map<String, (String) -> String?> = mapOf(
        "white-list" to { v -> if (v.toBoolean()) "whitelist on" else "whitelist off" },
        "enforce-whitelist" to { _ -> null },
        "difficulty" to { v -> "difficulty $v" },
        "gamemode" to { v -> "defaultgamemode $v" },
    )
    /**
     * Minecraft's defaults of the properties the app sets: what one not in the file runs with (a new
     * server's before its first start, or one a version dropped: pvp, a game rule from 1.21.9).
     */
    private val DEFAULTS = mapOf(
        "motd" to "A Minecraft Server", "max-players" to "20", "gamemode" to "survival", "difficulty" to "easy",
        "online-mode" to "true", "white-list" to "false", "enforce-whitelist" to "false", "pvp" to "true",
        "allow-flight" to "false", "hardcore" to "false", "view-distance" to "10", "simulation-distance" to "10",
        "server-port" to "25565", "level-seed" to "",
    )

    enum class Phase { Starting, Running, Stopping, Stopped, Crashed }

    data class Run(
        val serverId: String,
        val name: String,
        val phase: Phase = Phase.Starting,
        /** Its run's (see LinuxTasks.runService), for stopping it. */
        val taskId: Long? = null,
        val startedAt: Long = System.currentTimeMillis(),
        /** When it said it's ready ("Done"). */
        val readyAt: Long? = null,
        val players: List<String> = emptyList(),
        val online: Int? = null,
        val maxPlayers: Int? = null,
        /** Why it stopped by itself, as it said (e.g. its port taken). */
        val error: String? = null,
        /** Its settings as it started with them (server.properties and [MEMORY]): changed since, they wait for a restart. */
        val startValues: Map<String, String> = emptyMap(),
        /** Those changed since it started ([changed]). */
        val pending: Set<String> = emptySet(),
        internal val rconPort: Int = RCON_PORT,
        internal val rconPassword: String = "",
    ) {
        val isActive get() = phase == Phase.Starting || phase == Phase.Running || phase == Phase.Stopping
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<Run?>(null)
    val state: StateFlow<Run?> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    /** The server's console (and the tunnel's), its last lines. */
    val log: StateFlow<List<String>> = _log.asStateFlow()

    /** The servers' tunnels (one server's at a time), their lines in its console. */
    private val tunnels = ServerTunnels(MinecraftServerFeature) { line -> addLog(line) }

    /** The tunnel of the server running (or that ran last), as cloudflared's API tells. */
    val tunnel: StateFlow<ServerTunnels.ServerTunnel?> = tunnels.tunnel

    /** The bore tunnel of the server running (or that ran last), as bore's API tells. */
    val bore: StateFlow<ServerTunnels.ServerBore?> = tunnels.bore

    private var playersJob: Job? = null
    /** The app (the Application, not a Context that may be an activity), for what the server tells of itself (its seed). */
    private var app: Application? = null
    /** The run under way (installing its Java, then Java itself), to stop it before Java has started too. */
    private var runJob: Job? = null
    /** The server whose files go once it has stopped ([kill]): what it still wrote while dying goes with them. */
    @Volatile
    private var deleteAfter: String? = null

    /** A server's tunnel, its token next to the server's files (see Servers.tokenFile). */
    fun tunnelOf(context: Context, profile: ServerProfile) = Tunnel(
        MinecraftServerFeature, Servers.tokenFile(context.applicationContext, profile.id),
        label = "${profile.name} · Cloudflare",
        // One with its server in the bottom bar
        group = profile.name
    )

    /** A server's bore tunnel, its secret (its own bore server's) next to the server's files (see Servers.boreSecretFile). */
    fun boreOf(context: Context, profile: ServerProfile) = Bore(
        MinecraftServerFeature, Servers.boreSecretFile(context.applicationContext, profile.id),
        label = "${profile.name} · Bore",
        group = profile.name
    )

    fun isRunning(id: String) = _state.value?.let { it.serverId == id && it.isActive } == true

    /** Whether any server runs (another can't start meanwhile, nor can this one's files change under it). */
    val anyRunning get() = _state.value?.isActive == true

    /**
     * Its settings the app needs: RCON on (its console in the app), on the phone alone, with a password
     * of its own. Returns the port and password.
     */
    fun prepare(context: Context, id: String): Pair<Int, String> {
        val props = Servers.properties(context, id)
        val password = props.get("rcon.password")?.ifBlank { null } ?: Servers.newPassword()
        val port = props.get("rcon.port")?.toIntOrNull() ?: RCON_PORT
        props.set("enable-rcon", "true")
            .set("rcon.password", password)
            .set("rcon.port", port.toString())
            // Its answers to the app aren't for the players
            .set("broadcast-rcon-to-ops", "false")
            .save()
        return port to password
    }

    fun start(context: Context, profile: ServerProfile) {
        if (anyRunning) return
        val app = context.applicationContext as Application
        this.app = app
        val dir = Servers.dir(app, profile.id)
        val (port, password) = prepare(app, profile.id)
        // Its Java: the one its version is made for (Mojang's metadata, see Java.forNeeds)
        val jdk = Java.forNeeds(app, profile.needs)
        val startValues = Servers.properties(app, profile.id).all.toMap() +
            (MEMORY to profile.memoryMb.toString()) + (JAVA to (jdk?.part ?: ""))
        val pidFile = Servers.pidFile(app, profile.id).apply { delete() }
        _log.value = emptyList()
        synchronized(liveQueue) { liveQueue.clear() }
        _state.value = Run(profile.id, profile.name, rconPort = port, rconPassword = password, startValues = startValues)
        ServerService.start(app)
        // The tunnels turned on for it (Cloudflare's once its token is in), each installed first if it's gone
        // meanwhile (asked, as its Java is); not there after all, it shows as failed
        if (profile.tunnelEnabled && tunnelOf(app, profile).hasToken) scope.launch {
            if (!installTunnel(app).ok) tunnels.tunnelFailed(profile.id, "cloudflared isn't installed")
            else if (runs(profile.id)) startTunnel(app, profile)
        }
        if (profile.boreEnabled) scope.launch {
            if (!installBore(app).ok) tunnels.boreFailed(profile.id, "bore isn't installed")
            else if (runs(profile.id)) startBore(app, profile)
        }

        deleteAfter = null
        runJob = scope.launch {
            val java = when {
                jdk == null -> InstallResult.Failed
                Java.isInstalled(app, jdk) -> InstallResult.Installed
                else -> installJava(app, jdk)
            }
            if (!java.ok || jdk == null) {
                stopTunnel()
                stopBore()
                // Not wanted: stopped (nothing to tell); failed: Java's installation is a task, the bottom bar tells already
                _state.update {
                    if (java == InstallResult.Declined) it?.copy(phase = Phase.Stopped)
                    else it?.copy(phase = Phase.Crashed, error = "Java couldn't be installed")
                }
                return@launch
            }
            var lastError: String? = null
            val result = Java.runService(
                // Paper without its console: commands go by RCON, and old ones' console (1.7.10's jline) reads its
                // stdin as endless empty lines, each an "Unknown command" (a million a minute, the seed unanswered)
                app, MinecraftServerFeature, jdk,
                profile.launch + listOf("--noconsole").takeIf { profile.type == ServerType.Paper && "--noconsole" !in profile.launch }.orEmpty(),
                label = profile.name,
                dir = dir,
                heap = Java.Heap(profile.memoryMb, initialMb = 1024),
                // On a newer Java than its Minecraft's (1.17's 16: 17, see Java.forNeeds), Paper 1.17 refuses to
                // start unless told not to mind (tested: it runs)
                options = listOf("-DPaper.IgnoreJavaVersion=true").takeIf { profile.type == ServerType.Paper && jdk.version > profile.needs }.orEmpty(),
                pidFile = pidFile,
                onStart = { id -> _state.update { it?.copy(taskId = id) } },
                onLine = { raw ->
                    val line = clean(raw)
                    // The app's own RCON connections (its console, the players' polling) aren't the user's to read
                    if (!RCON_CLIENT.containsMatchIn(line)) addLog(line)
                    if (ERROR.containsMatchIn(line)) lastError = line
                    onServerLine(line)
                }
            )
            playersJob?.cancel()
            stopTunnel()
            stopBore()
            // Its Java gone with it, before it counts as stopped: PRoot stopped by a signal (the server killed) may
            // leave its Java running, its port held (the next start's "FAILED TO BIND")
            killLeftover(app, pidFile)
            pidFile.delete()
            // Its files deleted meanwhile (the server, or the tool's data): what it wrote as it went too
            if (deleteAfter == profile.id) {
                deleteAfter = null
                Servers.dir(app, profile.id).deleteTree()
            }
            _state.update { run ->
                run ?: return@update null
                val ended = run.copy(pending = emptySet())
                when {
                    run.phase == Phase.Stopping -> ended.copy(phase = Phase.Stopped)
                    // Exits by itself after "stop" typed in its console too
                    result.isSuccess && run.readyAt != null -> ended.copy(phase = Phase.Stopped)
                    else -> ended.copy(
                        phase = Phase.Crashed,
                        error = run.error ?: lastError ?: result.stderr.lineSequence().lastOrNull { it.isNotBlank() }
                            ?: "Exit code ${result.exitCode}"
                    )
                }
            }
            // Crashed: told in the one place errors are, the bottom bar (its console's last lines as the log)
            _state.value?.takeIf { it.serverId == profile.id && it.phase == Phase.Crashed }?.let { run ->
                LinuxTasks.reportFailure(MinecraftServerFeature, profile.name, run.error ?: "", _log.value.takeLast(100).joinToString("\n"))
            }
            // A Java no server runs on anymore (its own changed meanwhile, or it's gone); the tunnel's, turned off meanwhile
            pruneJava(app)
            pruneTunnel(app)
            pruneBore(app)
        }
    }

    /**
     * Stops the running server at once, its world unsaved (its files are to go: the server deleted, or
     * the tool's data); with [delete], its folder goes once it has (what it wrote meanwhile with it).
     */
    fun kill(delete: Boolean) {
        val run = _state.value?.takeIf { it.isActive } ?: return
        if (delete) deleteAfter = run.serverId
        _state.update { it?.copy(phase = Phase.Stopping) }
        stopTunnel()
        stopBore()
        run.taskId?.let(LinuxTasks::cancel)
        // Not started yet (its Java installing): the run itself, and what its end would do
        if (run.taskId == null) {
            val job = runJob
            job?.cancel()
            scope.launch {
                job?.join()
                _state.update { if (it?.isActive == true) it.copy(phase = Phase.Stopped) else it }
                val app = app ?: return@launch
                if (deleteAfter == run.serverId) {
                    deleteAfter = null
                    Servers.dir(app, run.serverId).deleteTree()
                }
                pruneJava(app)
            }
        }
    }

    /**
     * Kills the server's Java if it's still there once its run has ended: its process id in [pidFile] (the
     * host's, PRoot keeps them), when that's still one of the app's but not the app itself (named after it).
     * Waits until it's gone, a few seconds at most.
     */
    private suspend fun killLeftover(app: Context, pidFile: File) {
        val pid = runCatching { pidFile.readText().trim().toInt() }.getOrNull()?.takeIf { it != Process.myPid() } ?: return
        val proc = File("/proc/$pid")
        val cmdline = runCatching { File(proc, "cmdline").readText() }.getOrNull() ?: return
        if (cmdline.startsWith(app.packageName)) return
        Log.w(TAG, "Its Java ($pid) outlived its run: killed")
        Process.sendSignal(pid, Process.SIGNAL_KILL)
        withTimeoutOrNull(KILL_WAIT) { while (proc.exists()) delay(100.milliseconds) }
    }

    /**
     * Uninstalls the Java versions no server runs on (the one its version is made for; the running one's,
     * and the one a server being made installs, too).
     */
    fun pruneJava(context: Context) {
        val app = context.applicationContext
        val used = Servers.list(app).mapNotNull { p -> Java.forNeeds(app, p.needs)?.part }.toMutableSet()
        _state.value?.takeIf { it.isActive }?.startValues?.get(JAVA)?.let { used += it }
        Installer.state.value?.takeIf { it.isActive }?.let { used += it.draft.jdk.part }
        val unused = Java.JDKS.map { it.part }.filter { it !in used && (LinuxParts.isExtensionUnpacked(app, it) || LinuxParts.isExtensionDownloaded(app, it)) }
        if (unused.isNotEmpty()) LinuxTasks.removeParts(app, unused)
    }

    /** Installs cloudflared, a server's tunnel turned on: asked first (InstallPrompt), then its table on the page tells how it goes. */
    suspend fun installTunnel(context: Context): InstallResult = tunnels.install(context, Tunnel.PART)

    /**
     * Uninstalls cloudflared when no server has its tunnel turned on (nor another tool its own, see
     * MinecraftServerFeature.usesExtraExtension), and none runs.
     */
    fun pruneTunnel(context: Context) = ServerTunnels.prune(context, Tunnel.PART)

    /** Installs bore, a server's bore tunnel turned on: asked first (InstallPrompt), then its table on the page tells how it goes. */
    suspend fun installBore(context: Context): InstallResult = tunnels.install(context, Bore.PART)

    /** Uninstalls bore when no server has its bore tunnel turned on (nor another tool its own), and none runs. */
    fun pruneBore(context: Context) = ServerTunnels.prune(context, Bore.PART)

    /** Its Java, as it starts (a task in the bottom bar, and its table tells how it's doing); whether it's there now. */
    private suspend fun installJava(app: Context, jdk: Java.Jdk): InstallResult = Java.install(app, MinecraftServerFeature, jdk)

    /** Stops the server, then starts it again (e.g. with settings changed meanwhile). */
    fun restart(context: Context, id: String) {
        val app = context.applicationContext
        if (!isRunning(id)) return
        stop()
        scope.launch {
            _state.first { it?.isActive != true }
            // On the main thread, as a tap starts it: after the service has seen it stop (see ServerService)
            withContext(Dispatchers.Main) { Servers.load(app, id)?.let { start(app, it) } }
        }
    }

    /**
     * A setting of server [id] changed to [value] ([MEMORY] for its memory): while it runs, it waits
     * for a restart ([Run.pending]), unless it's back to what it started with.
     */
    fun changed(id: String, key: String, value: String) {
        val live = LIVE[key]
        val run = _state.value?.takeIf { it.serverId == id }
        if (live != null && run?.phase == Phase.Starting) {
            // Not listening yet: told once it's ready (see flushLive), not waiting for a restart
            synchronized(liveQueue) { liveQueue[key] = value }
            _state.update { r -> if (r?.serverId == id) r.copy(pending = r.pending - key) else r }
        } else if (live != null && run?.phase == Phase.Running) {
            // Told it as it runs: nothing waits
            scope.launch {
                val told = live(value)?.let { console(it).isSuccess } ?: true
                if (told) _state.update { run ->
                    if (run?.serverId != id) run else run.copy(startValues = run.startValues + (key to value), pending = run.pending - key)
                } else pending(id, key, value)
            }
        } else {
            pending(id, key, value)
        }
    }

    /**
     * Drops what waits for a restart of running server [id]: each setting back to what it started with
     * (server.properties' as written; its memory and Java, the profile's), nothing waiting anymore.
     */
    fun discard(context: Context, id: String) {
        val run = _state.value?.takeIf { it.serverId == id && it.isActive && it.pending.isNotEmpty() } ?: return
        val app = context.applicationContext
        val props = Servers.properties(app, id)
        var profile = Servers.load(app, id) ?: return
        run.pending.forEach { key ->
            val value = run.startValues[key] ?: DEFAULTS[key]
            when (key) {
                MEMORY -> value?.toIntOrNull()?.let { profile = profile.copy(memoryMb = it) }
                else -> value?.let { props.set(key, it) }
            }
        }
        props.save()
        Servers.save(app, profile)
        _state.update { r -> if (r?.serverId == id) r.copy(pending = emptySet()) else r }
    }

    private fun pending(id: String, key: String, value: String) = _state.update { run ->
        if (run == null || run.serverId != id || !run.isActive) run
        else run.copy(pending = if ((run.startValues[key] ?: DEFAULTS[key]) == value) run.pending - key else run.pending + key)
    }

    /** The live settings ([LIVE]) changed while it was starting, by key: told it once it's ready. */
    private val liveQueue = mutableMapOf<String, String>()

    /**
     * Tells a server just ready the live settings changed as it started, written again too (starting,
     * it writes server.properties as it read it, maybe over them).
     */
    private fun flushLive(app: Context, id: String) {
        val queued = synchronized(liveQueue) { liveQueue.toMap().also { liveQueue.clear() } }
        if (queued.isEmpty()) return
        val props = Servers.properties(app, id)
        queued.forEach { (key, value) -> props.set(key, value) }
        props.save()
        scope.launch {
            queued.forEach { (key, value) -> LIVE[key]?.invoke(value)?.let { console(it) } }
            _state.update { run -> if (run?.serverId != id) run else run.copy(startValues = run.startValues + queued, pending = run.pending - queued.keys) }
        }
    }

    /**
     * Its settings as it runs with them, once it's ready: server.properties as it wrote it starting
     * (a new server's first, or keys it added), those changed meanwhile kept as they were.
     */
    private fun settle(app: Context, id: String) = _state.update { run ->
        if (run == null || run.serverId != id) return@update run
        val written = Servers.properties(app, id).all.toMap()
        run.copy(startValues = written.filterKeys { it !in run.pending } + run.startValues.filterKeys { it in run.pending || it !in written })
    }

    /** What the server says of itself, line by line. */
    private fun onServerLine(line: String) {
        when {
            DONE.containsMatchIn(line) -> {
                _state.update { it?.copy(phase = if (it.phase == Phase.Starting) Phase.Running else it.phase, readyAt = System.currentTimeMillis()) }
                app?.let { a ->
                    _state.value?.serverId?.let {
                        settle(a, it)
                        flushLive(a, it)
                    }
                }
                startPlayerPolling()
                scope.launch { rememberSeed() }
            }
            "FAILED TO BIND" in line -> _state.update { it?.copy(error = line) }
            "UnsupportedClassVersionError" in line -> _state.update { it?.copy(error = line) }
            JOIN_LEAVE.containsMatchIn(line) -> scope.launch { refreshPlayers() }
        }
    }

    private fun startPlayerPolling() {
        playersJob?.cancel()
        playersJob = scope.launch {
            while (isActive && _state.value?.phase == Phase.Running) {
                refreshPlayers()
                delay(PLAYERS_EVERY)
            }
        }
    }

    /** Its world's seed, as its "seed" says ("Seed: [-123456789]"), for its settings (see [Servers.seed]). */
    private suspend fun rememberSeed() {
        val id = _state.value?.serverId ?: return
        // Asked again a while: RCON may not listen yet when it's done (1.7.10 starts it after, in a thread of its own)
        repeat(SEED_TRIES) { attempt ->
            if (attempt > 0) delay(SEED_EVERY)
            if (_state.value?.let { it.serverId == id && it.isActive } != true) return
            val seed = rcon("seed")?.let { SEED.find(clean(it))?.groupValues?.get(1) }
            if (seed != null) {
                app?.let { Servers.setSeed(it, id, seed) }
                return
            }
        }
    }

    /** Who's on, as its "list" says ("There are 1 of a max of 20 players online: Steve"). */
    private suspend fun refreshPlayers() {
        val answer = rcon("list") ?: return
        val m = PLAYERS.find(clean(answer)) ?: return
        val names = m.groupValues[3].split(',').map { it.trim() }.filter { it.isNotEmpty() }
        _state.update { it?.copy(online = m.groupValues[1].toIntOrNull(), maxPlayers = m.groupValues[2].toIntOrNull(), players = names) }
    }

    /** Runs [command] in the server (without the slash): its answer, or null when it can't be reached. */
    suspend fun rcon(command: String): String? = tryRcon(command).getOrNull()

    /** [rcon]'s answer, or why there's none. */
    private suspend fun tryRcon(command: String): Result<String> {
        val run = _state.value?.takeIf { it.isActive } ?: return Result.failure(IOException("the server isn't running"))
        return withContext(Dispatchers.IO) {
            runCatching { Rcon(run.rconPort, run.rconPassword).command(command) }
        }
    }

    /** [command] typed in the app's console: shown in it with the server's answer; a failure says why it wasn't sent. */
    suspend fun console(command: String): Result<Unit> {
        val cmd = command.trim().removePrefix("/")
        if (cmd.isEmpty()) return Result.failure(IllegalArgumentException("no command"))
        addLog("> $cmd")
        val answer = tryRcon(cmd).getOrElse { return Result.failure(it) }
        clean(answer).lines().filter { it.isNotBlank() }.forEach(::addLog)
        if (cmd == "stop") _state.update { it?.copy(phase = Phase.Stopping) }
        return Result.success(Unit)
    }

    /** Saves the world and quits; killed if it doesn't within [STOP_TIMEOUT] (or can't be told to). */
    fun stop() {
        val run = _state.value?.takeIf { it.isActive } ?: return
        _state.update { it?.copy(phase = Phase.Stopping) }
        scope.launch {
            val told = runCatching { Rcon(run.rconPort, run.rconPassword).command("stop") }.isSuccess
            val stopped = told && withTimeoutOrNull(STOP_TIMEOUT) { _state.first { it?.isActive != true } } != null
            if (!stopped) _state.value?.taskId?.let(LinuxTasks::cancel)
        }
    }

    /**
     * The server's Cloudflare tunnel (cloudflared's API, [Tunnel]), routing what the tunnel's public
     * hostname gets to the server, as set up in Cloudflare's dashboard; its lines in the console.
     */
    fun startTunnel(context: Context, profile: ServerProfile) {
        // Turned on for it (and there, with its token: see ServerTunnels.startTunnel)
        if (!profile.tunnelEnabled) return
        tunnels.startTunnel(context, profile.id, profile.name, tunnelOf(context, profile))
    }

    fun stopTunnel() = tunnels.stopTunnel()

    /**
     * The server's bore tunnel ([Bore]): its port on the bore server (the one it had last, if it's free),
     * kept for next time; its lines in the console.
     */
    fun startBore(context: Context, profile: ServerProfile) {
        // Turned on for it (and there: see ServerTunnels.startBore)
        if (!profile.boreEnabled || tunnels.bore.value?.isActive == true) return
        val app = context.applicationContext
        tunnels.startBore(
            app, profile.id, profile.name, boreOf(app, profile), port(app, profile.id),
            profile.boreServer.ifEmpty { Bore.SERVER }, profile.borePort
        ) { port ->
            // Its port, asked for again next time
            Servers.load(app, profile.id)?.takeIf { it.borePort != port }?.let { Servers.save(app, it.copy(borePort = port)) }
        }
    }

    fun stopBore() = tunnels.stopBore()

    /**
     * The port server [id] listens on: while it runs, the one it started with (a new one waits for its
     * restart, see [changed]), else server.properties'.
     */
    fun port(context: Context, id: String): Int =
        (_state.value?.takeIf { it.serverId == id && it.isActive }?.startValues?.get("server-port")
            ?: Servers.properties(context.applicationContext, id).get("server-port"))?.toIntOrNull() ?: 25565

    /** Whether server [id] is still on its way up or running (its tunnels started only then). */
    private fun runs(id: String) = _state.value?.let { it.serverId == id && it.isActive } == true

    /**
     * Server [id]'s Cloudflare tunnel turned on: installed (asked first, see installTunnel) and, while the
     * server runs, started at once (with its token in), not on its restart.
     */
    suspend fun tunnelOn(context: Context, id: String): InstallResult {
        val app = context.applicationContext
        return installTunnel(app).also { if (it.ok && runs(id)) Servers.load(app, id)?.let { p -> startTunnel(app, p) } }
    }

    /** Server [id]'s tunnel token changed (a new one in): while the server runs, its tunnel runs again with it (the old one's stopped first). */
    suspend fun tunnelToken(context: Context, id: String) {
        tunnels.stopTunnelOf(id)
        if (Servers.load(context.applicationContext, id)?.tunnelEnabled == true) tunnelOn(context, id)
    }

    /** Server [id]'s Cloudflare tunnel turned off (or its token removed): stopped at once, then uninstalled once no server has it on. */
    fun tunnelOff(context: Context, id: String) {
        val app = context.applicationContext
        scope.launch {
            tunnels.stopTunnelOf(id)
            pruneTunnel(app)
        }
    }

    /** Server [id]'s bore tunnel turned on: installed (asked first, see installBore) and, while the server runs, started at once. */
    suspend fun boreOn(context: Context, id: String): InstallResult {
        val app = context.applicationContext
        return installBore(app).also { if (it.ok && runs(id)) Servers.load(app, id)?.let { p -> startBore(app, p) } }
    }

    /** Server [id]'s bore tunnel turned off: stopped at once, then uninstalled once no server has it on. */
    fun boreOff(context: Context, id: String) {
        val app = context.applicationContext
        scope.launch {
            tunnels.stopBoreOf(id)
            pruneBore(app)
        }
    }

    private fun addLog(line: String) = _log.update { (it + line).takeLast(LOG_LINES) }

    /** Without terminal colors (Paper's) and Minecraft's own formatting codes (§a...). */
    private fun clean(s: String) = s.replace(ANSI, "").replace(FORMATTING, "")

    private val ANSI = Regex("""\u001B\[[0-9;?]*[ -/]*[@-~]""")
    private val FORMATTING = Regex("""§.""")
    private val DONE = Regex("""Done \([\d.,]+s\)!""")
    /** "Thread RCON Client /127.0.0.1 started", "... shutting down": each of the app's RCON connections. */
    private val RCON_CLIENT = Regex("""Thread RCON Client\b""")
    private val JOIN_LEAVE = Regex(""" (joined|left) the game""")
    private val PLAYERS = Regex("""There are (\d+) (?:of a max of|/) ?(\d+) players online:?(.*)""")
    private val ERROR = Regex("""\b(ERROR|FATAL|Exception|Error:)""")
    /** "Seed: [-113...]"; old versions' without the brackets (1.7.10: "Seed: -113..."). */
    private val SEED = Regex("""Seed: \[?(-?\d+)""")
}
