package org.lukemech.lmtoolbox.linux.parts

import org.lukemech.lmtoolbox.linux.InstallResult
import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.helpers.AppFeature

/**
 * A Cloudflare Tunnel (the cloudflared part), for a server on the phone that players reach from
 * anywhere: cloudflared runs a tunnel set up in Cloudflare's dashboard (its token in [tokenFile]),
 * which routes its public hostnames to what's on the phone (e.g. tcp://localhost:25565). Its own
 * routes are the dashboard's; here it's connected or not ([state]). Or a quick tunnel ([startQuick]):
 * no account, no token, an address of its own on trycloudflare.com for a web server on the phone.
 *
 * A tool on it lists [PART] in its extraExtensions and installs it when it's turned on ([install]);
 * [source] is its title, as its tasks show it.
 */
class Tunnel(
    source: AppFeature,
    /** cloudflared reads the token from there: never in a command line, nor a task's log. Null: quick tunnels alone. */
    val tokenFile: File?,
    label: String = "cloudflared",
    group: String? = null,
) : TunnelPart<Tunnel.State>(source, label, group) {
    data class State(
        override val phase: ConnectionPhase = ConnectionPhase.Connecting,
        override val taskId: Long? = null,
        /** Connections to Cloudflare's edge (it keeps up to four, in nearby data centers). */
        val connections: Int = 0,
        /** A quick tunnel's address, once cloudflared has it: e.g. https://some-random-words.trycloudflare.com. */
        val url: String? = null,
        /** Why it couldn't connect, as cloudflared said (e.g. the token's not valid). */
        override val error: String? = null,
    ) : TunnelState

    val hasToken get() = hasSecret(tokenFile)

    /** Connects (unless it is, or there's no token); [onLine] gets each line cloudflared prints. */
    fun start(context: Context, onLine: (String) -> Unit = {}) {
        if (!hasToken) return
        run(context, listOf("cloudflared", "tunnel", "--no-autoupdate", "run", "--token-file", checkNotNull(tokenFile).absolutePath), onLine)
    }

    /**
     * Connects a quick tunnel to [localUrl] (e.g. http://127.0.0.1:8080), unless it's connected: its address
     * ([State.url]) is a new one each time. For HTTP alone (not a game's TCP), with no account.
     */
    fun startQuick(context: Context, localUrl: String, onLine: (String) -> Unit = {}) =
        run(context, listOf("cloudflared", "tunnel", "--no-autoupdate", "--url", localUrl), onLine)

    private fun run(context: Context, command: List<String>, onLine: (String) -> Unit) {
        if (mutableState.value?.isActive == true) return
        val app = context.applicationContext
        stopping = false
        mutableState.value = State()
        scope.launch {
            var lastError: String? = null
            // Until it's stopped: a service of its tool's, not a task
            val result = runService(
                app, command,
                onStart = { id -> mutableState.update { it?.copy(taskId = id) } },
                onLine = { line ->
                    onLine(line)
                    QUICK_URL.find(line)?.let { m -> mutableState.update { it?.copy(url = m.value) } }
                    when {
                        "Registered tunnel connection" in line ->
                            mutableState.update { it?.copy(phase = ConnectionPhase.Connected, connections = it.connections + 1, error = null) }
                        "Unregistered tunnel connection" in line || "Connection terminated" in line ->
                            mutableState.update { it?.copy(connections = (it.connections - 1).coerceAtLeast(0)) }
                        " ERR " in line -> lastError = line.substringAfter(" ERR ").trim()
                    }
                }
            )
            mutableState.update {
                it?.copy(
                    phase = if (stopping) ConnectionPhase.Stopped else ConnectionPhase.Failed,
                    connections = 0,
                    url = null,
                    error = if (stopping) null else lastError ?: result.stderr.lineSequence().lastOrNull { l -> l.isNotBlank() }
                )
            }
        }
    }

    /** Saves [token] to [tokenFile], readable by the app alone (null: removes it). */
    fun setToken(token: String?) {
        saveSecret(checkNotNull(tokenFile) { "A quick tunnel has no token" }, token)
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
        /** "INF |  https://some-random-words.trycloudflare.com  |", in the box cloudflared draws around it (not its API's, api.trycloudflare.com, in its errors). */
        private val QUICK_URL = Regex("""https://(?!api\.)[a-z0-9-]+\.trycloudflare\.com""")
    }
}
