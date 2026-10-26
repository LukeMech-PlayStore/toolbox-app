package org.lukemech.lmtoolbox.linux.parts

import org.lukemech.lmtoolbox.linux.InstallResult
import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.helpers.AppFeature

/**
 * A Cloudflare Tunnel (the cloudflared part), for a server on the phone that players reach from
 * anywhere: cloudflared runs a tunnel set up in Cloudflare's dashboard (its token in [tokenFile]),
 * which routes its public hostnames to what's on the phone (e.g. tcp://localhost:25565). Its own
 * routes are the dashboard's; here it's connected or not ([state]).
 *
 * A tool on it lists [PART] in its extraExtensions and installs it when it's turned on ([install]);
 * [source] is its title, as its tasks show it.
 */
class Tunnel(
    private val source: AppFeature,
    /** cloudflared reads the token from there: never in a command line, nor a task's log. */
    val tokenFile: File,
    /** What the bottom bar shows of it while it runs (a service, see LinuxTasks.runService), e.g. "My server · tunnel". */
    private val label: String = "cloudflared",
    /** The service it's part of, as its label (e.g. its server's name): one with it in the bottom bar. */
    private val group: String? = null,
) {
    enum class Phase { Connecting, Connected, Stopped, Failed }

    data class State(
        val phase: Phase = Phase.Connecting,
        /** Its run's (see LinuxTasks.runService), for stopping it. */
        val taskId: Long? = null,
        /** Connections to Cloudflare's edge (it keeps up to four, in nearby data centers). */
        val connections: Int = 0,
        /** Why it couldn't connect, as cloudflared said (e.g. the token's not valid). */
        val error: String? = null,
    ) {
        val isActive get() = phase == Phase.Connecting || phase == Phase.Connected
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State?>(null)
    /** Null until it's started. */
    val state: StateFlow<State?> = _state.asStateFlow()

    @Volatile
    private var stopping = false

    val hasToken get() = tokenFile.let { it.exists() && it.length() > 0 }

    /** Connects (unless it is, or there's no token); [onLine] gets each line cloudflared prints. */
    fun start(context: Context, onLine: (String) -> Unit = {}) {
        if (_state.value?.isActive == true || !hasToken) return
        val app = context.applicationContext
        stopping = false
        _state.value = State()
        scope.launch {
            var lastError: String? = null
            // Until it's stopped: a service of its tool's, not a task
            val result = LinuxTasks.runService(
                app, command(), source, label, group = group,
                onStart = { id -> _state.update { it?.copy(taskId = id) } },
                onLine = { line ->
                    onLine(line)
                    when {
                        "Registered tunnel connection" in line ->
                            _state.update { it?.copy(phase = Phase.Connected, connections = it.connections + 1, error = null) }
                        "Unregistered tunnel connection" in line || "Connection terminated" in line ->
                            _state.update { it?.copy(connections = (it.connections - 1).coerceAtLeast(0)) }
                        " ERR " in line -> lastError = line.substringAfter(" ERR ").trim()
                    }
                }
            )
            _state.update {
                it?.copy(
                    phase = if (stopping) Phase.Stopped else Phase.Failed,
                    connections = 0,
                    error = if (stopping) null else lastError ?: result.stderr.lineSequence().lastOrNull { l -> l.isNotBlank() }
                )
            }
        }
    }

    /** Disconnects (cloudflared ends its connections as it's stopped). */
    fun stop() {
        val s = _state.value?.takeIf { it.isActive } ?: return
        stopping = true
        s.taskId?.let(LinuxTasks::cancel)
    }

    private fun command() = listOf("cloudflared", "tunnel", "--no-autoupdate", "run", "--token-file", tokenFile.absolutePath)

    /** Saves [token] to [tokenFile], readable by the app alone (null: removes it). */
    fun setToken(token: String?) {
        if (token.isNullOrBlank()) {
            tokenFile.delete()
            return
        }
        tokenFile.parentFile?.mkdirs()
        tokenFile.writeText(token.trim())
        tokenFile.setReadable(false, false)
        tokenFile.setReadable(true, true)
    }

    companion object {
        /** The part (and its Play module), for a tool's extraExtensions: installed when the tool turns it on ([install]). */
        const val PART = "cloudflared"

        fun isInstalled(context: Context) = LinuxParts.isExtensionInstalled(context, PART)

        /** Installs it for the tool behind [source] (asked first; a task in the bottom bar, its table telling how it goes). */
        suspend fun install(context: Context, source: AppFeature): InstallResult = LinuxTasks.installParts(context, source, listOf(PART))

        /**
         * The token in [text] as pasted: the token itself, or one of the installation commands Cloudflare's
         * dashboard shows for a tunnel ("cloudflared service install <token>", "… run --token <token>");
         * null if there's none (a token is a long base64 text).
         */
        fun tokenIn(text: String): String? {
            val words = text.trim().split(Regex("\\s+"))
            val afterOption = words.indexOf("--token").takeIf { it >= 0 }?.let { words.getOrNull(it + 1) }
            val candidate = afterOption ?: words.lastOrNull()
            return candidate?.trim('"', '\'')?.takeIf { it.length >= 40 && TOKEN.matches(it) }
        }

        private val TOKEN = Regex("[A-Za-z0-9+/=_-]+")
    }
}
