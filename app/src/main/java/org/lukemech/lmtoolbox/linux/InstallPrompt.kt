package org.lukemech.lmtoolbox.linux

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.lukemech.lmtoolbox.helpers.AppFeature

/**
 * Asks before parts of the subsystem are installed for a tool ([LinuxTasks.installParts]): what they
 * are and what they take, accepted or not. Shown by the app's host (ui.helpers.InstallPromptHost), one
 * question at a time; with none shown (the app in the background), what the user started goes on.
 */
object InstallPrompt {
    /** A question: the tool behind [source] needs [parts]; [answer] is accepted (true) or not. */
    class Request(val source: AppFeature, val parts: List<LinuxExtension>, internal val answer: CompletableDeferred<Boolean>)

    private val _request = MutableStateFlow<Request?>(null)
    /** The question being asked, for the host. */
    val request: StateFlow<Request?> = _request.asStateFlow()

    private val asking = Mutex()

    /** Hosts shown: none, nothing to ask with. */
    @Volatile
    internal var hosts = 0

    /** Whether the user accepts installing [parts] for the tool behind [source]; true without a host to ask. Cancelling the caller takes the question back. */
    suspend fun confirm(source: AppFeature, parts: List<LinuxExtension>): Boolean {
        return hosts == 0 || parts.isEmpty() || asking.withLock {
            val request = Request(source, parts, CompletableDeferred())
            _request.value = request
            try {
                request.answer.await()
            } finally {
                _request.compareAndSet(request, null)
            }
        }
    }

    /** The host's answer to [request]. */
    fun answer(request: Request, accepted: Boolean) {
        request.answer.complete(accepted)
    }
}

/** How installing parts went ([LinuxTasks.installParts]). */
enum class InstallResult {
    Installed,
    /** The user didn't want them (asked by [InstallPrompt], or canceled their task): not an error, nothing to tell. */
    Declined,
    Failed;

    val ok get() = this == Installed
}
