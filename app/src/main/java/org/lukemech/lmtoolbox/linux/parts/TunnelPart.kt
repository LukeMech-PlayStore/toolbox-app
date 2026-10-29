package org.lukemech.lmtoolbox.linux.parts

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.linux.ExecResult
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.tasks.AppTasks

/** How a tunnel part's connection (bore's, cloudflared's) is doing. */
enum class ConnectionPhase { Connecting, Connected, Stopped, Failed }

/** A tunnel part's state, as [Bore]'s and [Tunnel]'s have it: how it's doing, its service (for stopping it), why it failed. */
interface TunnelState {
    val phase: ConnectionPhase
    /** Its run's (see LinuxTasks.runService), for stopping it. */
    val taskId: Long?
    /** Why it couldn't connect, as its program said. */
    val error: String?
    val isActive get() = phase == ConnectionPhase.Connecting || phase == ConnectionPhase.Connected
}

/**
 * What the parts connecting a tool's server to the internet share ([Bore], [Tunnel]): their program run as a service of
 * [source]'s until it's stopped ([runService]: [label] in the bottom bar, as part of [group]'s), how it's doing ([state]),
 * stopping it, and the secret it reads from a file of the app's alone ([saveSecret]).
 */
abstract class TunnelPart<S : TunnelState>(
    protected val source: AppFeature,
    /** What the bottom bar shows of it while it runs (a service, see LinuxTasks.runService), e.g. "My server · bore". */
    protected val label: String,
    /** The service it's part of, as its label (e.g. its server's name): one with it in the bottom bar. */
    protected val group: String?,
) {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    protected val mutableState = MutableStateFlow<S?>(null)
    /** Null until it's started. */
    val state: StateFlow<S?> = mutableState.asStateFlow()

    /** It's being stopped: its program ending then is no failure. */
    @Volatile
    protected var stopping = false

    /** Disconnects (its program ends its connections as it's stopped). */
    fun stop() {
        val s = mutableState.value?.takeIf { it.isActive } ?: return
        stopping = true
        s.taskId?.let(AppTasks::cancel)
    }

    /** [command] run until it ends or is stopped, a service of the tool's; [onStart] gets its id, [onLine] each line. */
    protected suspend fun runService(app: Context, command: List<String>, onStart: (Long) -> Unit, onLine: (String) -> Unit): ExecResult =
        LinuxTasks.runService(app, command, source, label, group = group, onStart = onStart, onLine = onLine)

    companion object {
        /** Whether [file] holds a secret (a token). */
        fun hasSecret(file: File?) = file?.let { it.exists() && it.length() > 0 } == true

        /** Saves [secret] to [file], readable by the app alone (blank: removes it). */
        fun saveSecret(file: File, secret: String?) {
            if (secret.isNullOrBlank()) {
                file.delete()
                return
            }
            file.parentFile?.mkdirs()
            file.writeText(secret.trim())
            file.setReadable(false, false)
            file.setReadable(true, true)
        }
    }
}
