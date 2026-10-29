package org.lukemech.lmtoolbox.linux.parts

import org.lukemech.lmtoolbox.linux.InstallResult
import android.content.Context
import java.io.File
import java.net.InetAddress
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.R

/**
 * A bore tunnel (the bore part), for a server on the phone that players reach from anywhere with
 * nothing to set up: bore connects to a bore server ([SERVER], or one of the user's own, with its
 * secret in [secretFile]), which gives it a port of its own; players join at <server>:<port>.
 * It asks for the port it had last ([State.port]): the same address while nobody else has it.
 *
 * A tool on it lists [PART] in its extraExtensions and installs it when it's turned on ([install]);
 * [source] is its title, as its tasks show it.
 */
class Bore(
    source: AppFeature,
    /** A bore server's secret (its own server's, `bore server --secret`), read by bore from there: never in a command line, nor a task's log. */
    val secretFile: File,
    label: String = "bore",
    group: String? = null,
) : TunnelPart<Bore.State>(source, label, group) {
    data class State(
        override val phase: ConnectionPhase = ConnectionPhase.Connecting,
        override val taskId: Long? = null,
        /** Where players join, once it's connected: e.g. bore.pub:41234. */
        val address: String? = null,
        /** The bore server's port it has (or had last). */
        val port: Int = 0,
        /** Why it couldn't connect, as bore said (e.g. the server's unreachable). */
        override val error: String? = null,
    ) : TunnelState

    val hasSecret get() = hasSecret(secretFile)

    /**
     * Connects [localPort] to [server] (unless it is), asking for [port] (0: any); [onLine] gets each
     * line bore prints. Lost once connected, it connects again; the port taken meanwhile, it takes any.
     */
    fun start(context: Context, localPort: Int, server: String = SERVER, port: Int = 0, onLine: (String) -> Unit = {}) {
        if (mutableState.value?.isActive == true) return
        val app = context.applicationContext
        stopping = false
        mutableState.value = State(port = port)
        scope.launch {
            var want = port
            var lastError: String? = null
            while (!stopping) {
                var connected = false
                val result = runService(
                    app, command(localPort, server, want),
                    onStart = { id -> mutableState.update { it?.copy(taskId = id) } },
                    onLine = { raw ->
                        val line = raw.replace(ANSI, "")
                        // Why it ended ("Error: ..."): told once, as its state's error (see the end), not in the log
                        if (!line.startsWith("Error: ")) onLine(line)
                        LISTENING.find(line)?.let { m ->
                            connected = true
                            want = m.groupValues[2].toInt()
                            mutableState.update { it?.copy(phase = ConnectionPhase.Connected, address = "${m.groupValues[1]}:$want", port = want, error = null) }
                        }
                        if (" ERROR " in line || line.startsWith("Error: ")) lastError = line.substringAfter("ERROR ").substringAfter("Error: ").trim()
                    }
                )
                if (stopping) break
                lastError = lastError ?: result.stderr.lineSequence().lastOrNull { it.isNotBlank() }
                when {
                    // Lost (the network changed, the server restarted): again, on the same port
                    connected -> {
                        mutableState.update { it?.copy(phase = ConnectionPhase.Connecting, address = null) }
                        delay(RETRY)
                    }
                    // Its port someone else's now: any other
                    want != 0 -> want = 0
                    else -> break
                }
            }
            // bore says only "could not connect": the DNS's doing (a blocker's answer) told as such
            if (!stopping && lastError?.startsWith("could not connect") == true && dnsBlocks(server)) lastError = app.getString(R.string.f_Srv_bore_dns_blocked, server)
            mutableState.update {
                it?.copy(
                    phase = if (stopping) ConnectionPhase.Stopped else ConnectionPhase.Failed,
                    address = null,
                    error = if (stopping) null else lastError
                )
            }
        }
    }

    private fun command(localPort: Int, server: String, port: Int): List<String> {
        // 127.0.0.1: "localhost" may be IPv6's first, the server listening on IPv4's
        val bore = listOf("bore", "local", "$localPort", "--local-host", "127.0.0.1", "--to", server, "--port", "$port")
        // Its secret from its file, in its environment (BORE_SECRET); bore.pub has none (a secret would fail its handshake)
        return if (!hasSecret || server == SERVER) bore
        else listOf("sh", "-c", $$"BORE_SECRET=$(cat \"$0\") && export BORE_SECRET && exec \"$@\"", secretFile.absolutePath) + bore
    }

    /** Saves [secret] to [secretFile], readable by the app alone (null: removes it). */
    fun setSecret(secret: String?) = saveSecret(secretFile, secret)

    /**
     * Whether a DNS blocker answers for [server] (Private DNS, e.g. AdGuard's, or a Pi-hole): with nowhere
     * (0.0.0.0, loopback), its block page or itself instead of the server. bore asks Android as this does,
     * so it got the same answer.
     */
    private fun dnsBlocks(server: String): Boolean {
        val addresses = runCatching { InetAddress.getAllByName(server).toList() }.getOrNull() ?: return false
        return addresses.any { a ->
            a.isAnyLocalAddress || a.isLoopbackAddress || a.hostAddress in BLOCK_PAGES ||
                // bore.pub on the LAN: a Pi-hole answering with itself (one's own server may well be there)
                (server == SERVER && (a.isSiteLocalAddress || a.isLinkLocalAddress))
        }
    }

    companion object {
        /** The part (and its Play module), for a tool's extraExtensions: installed when the tool turns it on ([install]). */
        const val PART = "bore"
        /** bore's public server, free, with no account (as it goes: nobody promises it's up). */
        const val SERVER = "bore.pub"

        fun isInstalled(context: Context) = LinuxParts.isExtensionInstalled(context, PART)

        /** Installs it for the tool behind [source] (asked first; a task in the bottom bar, its table telling how it goes). */
        suspend fun install(context: Context, source: AppFeature): InstallResult = LinuxTasks.installParts(context, source, listOf(PART))

        private val RETRY = 5.seconds
        private val ANSI = Regex("""\u001B\[[0-9;?]*[ -/]*[@-~]""")
        /** "INFO bore_cli::client: listening at bore.pub:41234" */
        private val LISTENING = Regex("""listening at (\S+):(\d+)""")
        /** DNS blockers' block pages, answered for what they block: AdGuard DNS's. */
        private val BLOCK_PAGES = setOf("94.140.14.33")
    }
}
