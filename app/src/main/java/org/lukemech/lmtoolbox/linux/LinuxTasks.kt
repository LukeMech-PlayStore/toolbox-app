package org.lukemech.lmtoolbox.linux

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureDelivery
import org.lukemech.lmtoolbox.helpers.FeatureRegistry
import org.lukemech.lmtoolbox.helpers.ModuleFeature
import org.lukemech.lmtoolbox.helpers.PreferenceHelper

data class LinuxTask(
    val id: Long,
    val type: Type,
    /** The tool that started it, e.g. the currency converter; null: the subsystem's own (its install, its update). */
    val source: AppFeature? = null,
    /** Full command line, for copying and reports. */
    val command: String = "",
    /** Short form of [command] shown in the UI, see [displayCommand]. */
    val label: String = "",
    val state: State = State.Queued,
    /** 0..100, or null when the progress is unknown. */
    val progress: Float? = null,
    /** Short error summary, shown in status lines and notifications. */
    val message: String? = null,
    /** Full output (or stack trace), for the task list's copy / report actions. */
    val log: String? = null,
    val exitCode: Int? = null,
    /** A background command whose page was left; only then is it shown in the bottom bar. */
    val detached: Boolean = false,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    /** Its failure was looked at (its log opened from the bottom bar), so the bar doesn't show it anymore. */
    val seen: Boolean = false,
    /** What it's at, when that has a title of its own (e.g. a tool's programs downloading before they unpack). */
    @param:StringRes val stage: Int? = null,
    /**
     * An extension task's packages as it started (those missing then), and which of them it downloads first:
     * their tables list them so while it runs, however far it is, whenever their page is opened.
     */
    val packages: List<String> = emptyList(),
    val downloads: Set<String> = emptySet(),
) {
    enum class Type(@param:StringRes val titleRes: Int, val isEnvChange: Boolean) {
        Update(R.string.s_tasks_type_update, true),
        Command(R.string.s_tasks_type_command, false),
        /** A feature's tools unpacked on its first use (see [LinuxInstaller.installExtension]). */
        Extension(R.string.s_tasks_type_extension, false),
    }

    enum class State { Queued, Running, Done, Failed, Cancelled }

    val isActive get() = state == State.Queued || state == State.Running

    /** Commands are titled by the tool that ran them, see [detail] for the command itself. */
    fun title(context: Context): String =
        context.getString(stage ?: if (type == Type.Command) sourceTitle else type.titleRes)

    /** The title of the tool that started it, or the subsystem's (its manager's) for its own. */
    @get:StringRes val sourceTitle: Int get() = source?.titleStringResId ?: R.string.s_env_manager

    /** The short command line for commands, the tool whose programs are unpacked, null for installs/updates. */
    val detail: String? get() = if (type == Type.Command || type == Type.Extension) label else null

    /** The icon of the tool that started it, null for the subsystem's own tasks. */
    val featureIcon: ImageVector?
        @Composable get() = source?.icon
}

/**
 * A tool's program running until it's stopped (a server, its tunnel), see [LinuxTasks.runService]: not a
 * task. [source] is the tool running it, [label] what's running (e.g. the server's name).
 */
data class LinuxService(val id: Long, val source: AppFeature, val label: String) {
    /** The icon of the tool running it. */
    val featureIcon: ImageVector
        @Composable get() = source.icon
}

/**
 * Short, single-line form of a command for the UI: paths become file names and long arguments
 * are cut, e.g. `python3 /data/.../update_rates.py True` -> `python3 update_rates.py True`.
 */
internal fun displayCommand(cmd: List<String>): String = cmd.joinToString(" ") { arg ->
    val shown = (if (arg.startsWith("/") && ' ' !in arg) arg.substringAfterLast('/') else arg)
        .replace('\n', ' ')
    if (shown.length > 32) shown.take(31) + "…" else shown
}

/**
 * Runs everything that touches the Linux subsystem as a tracked task.
 *
 * - [ensureReady] (installing or updating the subsystem), [unpack]: app-wide, keep running when
 *   the app is left.
 * - [run]: page-bound command (versions, loading data): canceled with its caller, never shown
 *   in the bottom bar.
 * - [runInBackground]: keeps running when its page is left (e.g. saving a file); shown in the
 *   bottom bar once that happens.
 *
 * A feature's first command unpacks its tools (its package, and what that builds on) first, shown in the bottom bar.
 *
 * [LinuxTaskService] keeps the process alive (with a notification) while the app is in the
 * background and a task is active.
 */
object LinuxTasks {
    private const val TAG = "LinuxTasks"
    private const val HISTORY_LIMIT = 50
    private const val LOG_LIMIT = 64 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ids = AtomicLong()
    private val jobs = mutableMapOf<Long, Job>()
    private val envLock = EnvLock()
    /** Unpacking or deleting extensions: one at a time (commands may go on meanwhile). */
    private val extensionLock = Mutex()
    private val extensionJobs = mutableMapOf<String, Job>()

    /** How a package of the running (or last) install is doing, for its page. */
    sealed interface PackageState {
        data object Waiting : PackageState
        /** Of all the packages downloading together (Play counts them as one). */
        data class Downloading(val percent: Float?) : PackageState
        /** Downloaded, waiting to be unpacked. */
        data object Downloaded : PackageState
        data class Unpacking(val percent: Float) : PackageState
        data object Done : PackageState
    }

    private val _packages = MutableStateFlow<Map<String, PackageState>>(emptyMap())
    /** The packages of the running (or last) install, by name. */
    val packages: StateFlow<Map<String, PackageState>> = _packages.asStateFlow()

    private fun setPackage(name: String, state: PackageState) = _packages.update { it + (name to state) }

    private val _tasks = MutableStateFlow<List<LinuxTask>>(emptyList())
    /** Newest first. */
    val tasks: StateFlow<List<LinuxTask>> = _tasks.asStateFlow()

    // Kept until collected: a page opened with the app may run a command before MainScreen listens
    private val _envNotReady = Channel<Unit>(Channel.CONFLATED)

    /**
     * A command was refused while the subsystem is being unpacked or updated: the app goes back
     * from the page that ran it and asks to wait (MainScreen).
     */
    val envNotReady: Flow<Unit> = _envNotReady.receiveAsFlow()

    /** The task for the notification: an env change first, else the oldest active command. */
    fun current(tasks: List<LinuxTask>): LinuxTask? =
        tasks.filter { it.isActive }.let { active ->
            active.lastOrNull { it.type.isEnvChange } ?: active.lastOrNull()
        }

    /** The task for the in-app bottom bar: like [current], but only commands whose page was left. */
    fun barTask(tasks: List<LinuxTask>): LinuxTask? =
        current(tasks.filter { it.type.isEnvChange || it.detached })

    /**
     * Updates what's unpacked of the subsystem if the app ships another one; null if it's up to date,
     * or the core isn't unpacked: it comes with the first tool on Linux (see [unpack]), and goes once
     * none uses it (see [unused]), its module maybe still here until Play removes it.
     */
    fun ensureReady(context: Context): Job? =
        if (LinuxParts.isUpToDate(context)) null else startEnvTask(context)

    /** As the app starts: the subsystem ready (see [ensureReady]) and nothing unused left of it. */
    fun startUp(context: Context) {
        if (ensureReady(context) == null) removeUnusedExtensions(context)
    }

    /**
     * Page-bound command: runs in the caller's coroutine (leaving the page cancels it). Listed in
     * the task list like any other command, but never shown in the bottom bar.
     *
     * While the subsystem is being unpacked or updated it doesn't run: the result says so
     * ([ExecResult.isEnvNotReady]) and, unless [leavePageIfNotReady] is off, [envNotReady] takes
     * the user back from the page.
     */
    suspend fun run(
        context: Context, cmd: List<String>, source: AppFeature, leavePageIfNotReady: Boolean = true,
        timeoutMs: Long = LinuxEnv.DEFAULT_TIMEOUT_MS,
        /** Off: refused ([ExecResult.isExtensionMissing]) while the feature's tools aren't unpacked. */
        installExtensions: Boolean = true
    ): ExecResult {
        val app = context.applicationContext
        notReady(app, leavePageIfNotReady)?.let { return it }
        prepareExtension(app, source, installExtensions)?.let { return it }
        val task = add(
            LinuxTask(
                ids.incrementAndGet(), LinuxTask.Type.Command, source,
                command = cmd.joinToString(" "), label = displayCommand(cmd)
            )
        )
        var partial: ExecResult? = null
        val result = try {
            envLock.read {
                set(task.id) { it.copy(state = LinuxTask.State.Running) }
                LinuxEnv.exec(app, cmd, timeoutMs, task = task.id) { partial = it }
            }
        } catch (e: CancellationException) {
            finishCancelled(app, task.id, partial)
            throw e
        } catch (e: Exception) {
            ExecResult(-1, "", e.stackTraceToString())
        }
        val state = if (result.isSuccess) LinuxTask.State.Done else LinuxTask.State.Failed
        finish(task.id, state, if (result.isSuccess) null else summary(result), log(result), result.exitCode)
        return result
    }

    /**
     * Command that keeps running when its page is left (it then shows up in the bottom bar).
     * Cancelling the caller doesn't stop it; use [cancel] for that. Refused like [run] while the
     * subsystem is being set up.
     *
     * [onStart] gets the task's id (for [cancel], [detach], [setProgress]); [onLine] each line
     * the command prints, as it comes. [label] is what the UI shows of it (e.g. a file's name).
     * [maxOutput]: how much of its output is kept (see [LinuxEnv.exec]).
     */
    suspend fun runInBackground(
        context: Context, cmd: List<String>, source: AppFeature,
        label: String = displayCommand(cmd),
        timeoutMs: Long = LinuxEnv.DEFAULT_TIMEOUT_MS,
        maxOutput: Int = LinuxEnv.DEFAULT_MAX_OUTPUT,
        onStart: (id: Long) -> Unit = {},
        onLine: ((String) -> Unit)? = null
    ): ExecResult {
        val app = context.applicationContext
        notReady(app, leavePage = true)?.let { return it }
        prepareExtension(app, source, install = true)?.let { return it }

        val result = CompletableDeferred<ExecResult>()
        val task = add(
            LinuxTask(
                ids.incrementAndGet(), LinuxTask.Type.Command, source,
                command = cmd.joinToString(" "), label = label
            )
        )
        onStart(task.id)
        var partial: ExecResult? = null
        launchTask(app, task, cancelledOutput = { partial }) {
            val r = envLock.read {
                set(task.id) { it.copy(state = LinuxTask.State.Running) }
                LinuxEnv.exec(app, cmd, timeoutMs, onLine, maxOutput, task = task.id) { partial = it }
            }
            result.complete(r)
            val state = if (r.isSuccess) LinuxTask.State.Done else LinuxTask.State.Failed
            finish(task.id, state, if (r.isSuccess) null else summary(r), log(r), r.exitCode)
        }.invokeOnCompletion { e ->
            // No-op if the command produced a result; otherwise it failed or was canceled
            val message = _tasks.value.firstOrNull { it.id == task.id }?.message
            result.complete(ExecResult(-1, "", message ?: e?.message ?: "Failed"))
        }
        return try {
            result.await()
        } catch (e: CancellationException) {
            // The page was left: the command goes on and becomes visible in the bottom bar
            set(task.id) { it.copy(detached = true) }
            throw e
        }
    }

    private val _services = MutableStateFlow<List<LinuxService>>(emptyList())
    /** What runs as a service (see [runService]), oldest first: for the bottom bar. */
    val services: StateFlow<List<LinuxService>> = _services.asStateFlow()

    /**
     * A tool's program running until it's stopped (a server, its tunnel), its tool keeping it going
     * with a service of its own (and its notification): not a task. It's in neither the task list nor
     * the subsystem's notification, and in the bottom bar as what runs ([services]), without progress. It
     * holds the subsystem as any command does (an update waits for it; other commands don't).
     * Refused like [run] while the subsystem is set up; its tool's parts unpacked first, as a task.
     *
     * [onStart] gets its id (for [cancel], which stops it); [onLine] each line it prints. Cancelling
     * the caller doesn't stop it. Of its output only the last [maxOutput] characters are kept.
     */
    suspend fun runService(
        context: Context, cmd: List<String>, source: AppFeature, label: String,
        maxOutput: Int = 64 * 1024,
        onStart: (id: Long) -> Unit = {},
        onLine: ((String) -> Unit)? = null
    ): ExecResult {
        val app = context.applicationContext
        notReady(app, leavePage = true)?.let { return it }
        prepareExtension(app, source, install = true)?.let { return it }

        val service = LinuxService(ids.incrementAndGet(), source, label)
        val result = CompletableDeferred<ExecResult>()
        // LAZY: registered before it can end and unregister itself
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                result.complete(envLock.read { LinuxEnv.exec(app, cmd, Long.MAX_VALUE, onLine, maxOutput, task = service.id) })
            } catch (e: CancellationException) {
                result.complete(ExecResult(-1, "", "Stopped"))
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Service ${service.id} failed", e)
                result.complete(ExecResult(-1, "", e.stackTraceToString()))
            } finally {
                synchronized(jobs) { jobs.remove(service.id) }
                _services.update { list -> list.filterNot { it.id == service.id } }
            }
        }
        synchronized(jobs) { jobs[service.id] = job }
        _services.update { it + service }
        onStart(service.id)
        job.start()
        return result.await()
    }

    /**
     * A tool's failure outside a command (e.g. a download of its own, a server that crashed), as a failed
     * task: errors are told in one place, the bottom bar's red line (tapping it opens the tool), its [log]
     * in the task list. [label]: what it was for (e.g. a server's name).
     */
    fun reportFailure(source: AppFeature, label: String, message: String, log: String? = null) {
        val now = System.currentTimeMillis()
        add(
            LinuxTask(
                ids.incrementAndGet(), LinuxTask.Type.Command, source, label = label,
                state = LinuxTask.State.Failed, message = message, log = log ?: message, startedAt = now, finishedAt = now
            )
        )
    }

    /** Stops a task (see [launchTask]) or a service ([runService]). */
    fun cancel(id: Long) {
        synchronized(jobs) { jobs[id] }?.cancel()
    }

    /** A background command started outside a page (see [runInBackground]) whose page was left: shown in the bottom bar. */
    fun detach(id: Long) = set(id) { it.copy(detached = true) }

    /** Whether task [id] (null: none started yet) was canceled, e.g. to tell that from its failing. */
    fun isCancelled(id: Long?) = id != null && _tasks.value.firstOrNull { it.id == id }?.state == LinuxTask.State.Cancelled

    /**
     * Progress (0..100) of a running command, for the bottom bar, task list and notification; a command's every
     * line may set it (nothing's done while it's the same).
     */
    fun setProgress(id: Long, progress: Float?) {
        if (_tasks.value.firstOrNull { it.id == id }?.progress == progress) return
        set(id) { if (it.isActive) it.copy(progress = progress) else it }
    }

    fun clearFinished() = _tasks.update { list -> list.filter { it.isActive } }

    /** The latest failure the bottom bar tells of (not looked at yet, nor the subsystem's own, which has its screen). */
    fun barFailure(tasks: List<LinuxTask>): LinuxTask? =
        tasks.firstOrNull { it.state == LinuxTask.State.Failed && !it.seen && !it.type.isEnvChange }

    /** The failures were looked at (their logs opened): the bottom bar lets them be. */
    fun markFailuresSeen() = _tasks.update { list -> list.map { if (it.state == LinuxTask.State.Failed) it.copy(seen = true) else it } }

    /**
     * Null once the tools of the feature behind [source] are unpacked: unpacks them first (or waits
     * for it), unless not to [install]. Otherwise, why they aren't.
     */
    private suspend fun prepareExtension(app: Context, source: AppFeature, install: Boolean): ExecResult? {
        // A command of a tool on no part needs the core still
        val names = source.linuxExtensions
        // A part of it isn't built for this device's ABI
        if (!LinuxParts.isAvailable(app, source)) return ExecResult(ExecResult.EXIT_EXTENSION_MISSING, "", "$names isn't available on this device")
        if (LinuxParts.missingExtensions(app, names).isEmpty()) {
            // Running on it: the tool uses it (installed for another tool on the same part, see unused)
            markUsing(app, source)
            return null
        }
        if (!install) return ExecResult(ExecResult.EXIT_EXTENSION_MISSING, "", "Not unpacked yet")
        extensionJob(app, source, names).join()
        if (LinuxParts.missingExtensions(app, names).isEmpty()) return null
        val failed = _tasks.value.firstOrNull { it.type == LinuxTask.Type.Extension && it.source == source }
        return ExecResult(-1, "", failed?.message ?: app.getString(R.string.s_ext_failed))
    }

    /**
     * Installs [names], parts of the tool behind [source] it needs now (its extraExtensions, e.g. a Java
     * version): downloaded and unpacked as when its page is first opened, a task in the bottom bar; or
     * waits for that under way. What's still to get is asked first ([InstallPrompt]), unless [confirm]
     * is off (a page telling it all itself, e.g. full screen). Cancelling the caller doesn't stop it.
     */
    suspend fun installParts(context: Context, source: AppFeature, names: List<String>, confirm: Boolean = true): InstallResult {
        val app = context.applicationContext
        if (!LinuxParts.isAvailable(app, names)) return InstallResult.Failed
        if (!LinuxParts.isUpToDate(app)) ensureReady(app)?.join()
        // With the tool's own (the core first, then theirs): as on its first use
        val all = source.linuxExtensions + names
        val missing = LinuxParts.missingExtensions(app, all)
        if (missing.isNotEmpty()) {
            // Already installing (or queued to), asked then: not asked again, that one waited for
            val underway = _tasks.value.filter { t -> t.isActive && t.type == LinuxTask.Type.Extension && missing.any { it.name in t.packages } }
            val waitFor = if (missing.all { m -> underway.any { m.name in it.packages } }) {
                underway.mapNotNull { synchronized(jobs) { jobs[it.id] } }
            } else {
                if (confirm && !InstallPrompt.confirm(source, missing)) return InstallResult.Declined
                listOf(extensionJob(app, source, all))
            }
            waitFor.joinAll()
            // Canceled (its ✕ in the task list): not wanted after all, as declined; nothing to tell
            if (waitFor.any { it.isCancelled }) return InstallResult.Declined
        }
        val there = LinuxParts.missingExtensions(app, all).isEmpty().also { if (it) markUsing(app, source) }
        return if (there) InstallResult.Installed else InstallResult.Failed
    }

    /** Unpacks [feature]'s tools now (e.g. when its page asks for that first), unless they are already, or it's under way. */
    fun unpack(context: Context, feature: AppFeature) {
        val app = context.applicationContext
        val names = feature.linuxExtensions.takeIf { it.isNotEmpty() } ?: return
        // An update of what's there first; a missing core comes along with the tool's own
        if (!LinuxParts.isUpToDate(app) && ensureReady(app) != null) return
        if (LinuxParts.missingExtensions(app, names).isNotEmpty()) extensionJob(app, feature, names)
    }

    /** The unpacking of extensions [names] (none: just the core): the one under way, or a new one. */
    private fun extensionJob(app: Context, source: AppFeature, names: List<String>): Job = synchronized(extensionJobs) {
        val key = names.ifEmpty { listOf(LinuxParts.CORE) }.joinToString("+")
        extensionJobs[key]?.takeIf { it.isActive } ?: startExtensionTask(app, source, names).also { extensionJobs[key] = it }
    }

    /**
     * Unpacks what extensions [names] need (none: just the core), in the bottom bar from the start (it
     * takes a while): every package it builds on first (the core, with PRoot, first of all), as any other.
     */
    private fun startExtensionTask(app: Context, source: AppFeature, names: List<String>): Job {
        val missing = LinuxParts.missingExtensions(app, names)
        val task = add(
            LinuxTask(
                ids.incrementAndGet(), LinuxTask.Type.Extension, source,
                label = app.getString(source.titleStringResId),
                detached = true,
                packages = missing.map { it.name },
                downloads = missing.map { it.name }.filterNot { LinuxParts.isExtensionDownloaded(app, it) }.toSet()
            )
        )
        return launchTask(app, task) {
            // Not during an installation or update of the core, which would take it away again
            envLock.read {
                extensionLock.withLock {
                    _packages.value = missing.associate { it.name to PackageState.Waiting }
                    // From Play first: each extension is the module of the same name, 
                    val downloading = missing.map { it.name }.filterNot { LinuxParts.isExtensionDownloaded(app, it) }
                    var last = -1
                    // Downloading only shows when something is
                    set(task.id) {
                        it.copy(state = LinuxTask.State.Running, stage = R.string.s_tasks_downloading.takeIf { downloading.isNotEmpty() }, progress = null)
                    }
                    FeatureDelivery.install(app, downloading) { p ->
                        if ((p?.toInt() ?: -1) != last) {
                            last = p?.toInt() ?: -1
                            set(task.id) { it.copy(progress = p) }
                            downloading.forEach { setPackage(it, PackageState.Downloading(p)) }
                        }
                    }
                    downloading.forEach { setPackage(it, PackageState.Downloaded) }
                    // What's unpacked of a package whose module wasn't there (e.g. Play uninstalled it) is
                    // stale: a fresh download is unpacked afresh
                    downloading.filter { LinuxParts.isExtensionUnpacked(app, it) }.forEach { LinuxInstaller.removeExtension(app, it) }
                    set(task.id) { it.copy(stage = null, progress = 0f) }
                    // By size, as one bar
                    val total = missing.sumOf { it.size }.coerceAtLeast(1).toFloat()
                    var before = 0L
                    last = -1
                    for ((pkg, _, size) in missing) {
                        if (!LinuxParts.isExtensionUnpacked(app, pkg)) {
                            setPackage(pkg, PackageState.Unpacking(0f))
                            var lastOwn = 0
                            LinuxInstaller.installExtension(app, pkg) { p ->
                                val overall = ((before + size * p / 100) / total * 100).coerceAtMost(100f)
                                if (overall.toInt() != last) {
                                    last = overall.toInt()
                                    set(task.id) { it.copy(progress = overall) }
                                }
                                if (p.toInt() != lastOwn) {
                                    lastOwn = p.toInt()
                                    setPackage(pkg, PackageState.Unpacking(p))
                                }
                            }
                        }
                        setPackage(pkg, PackageState.Done)
                        before += size
                    }
                }
            }
            // The tool it was for uses it now, and so does every tool it unlocked (all of whose parts are there now,
            // e.g. the video encoder with the media downloader's FFmpeg)
            markUsing(app, source)
            markUnlocked(app)
            finish(task.id, LinuxTask.State.Done)
        }
    }

    /**
     * After an update: each tool that used the subsystem whose part is missing now (e.g. its part was
     * merged into another one, see [LinuxParts.staleExtensions]) gets it, as on its first use.
     */
    private fun reinstallReplaced(app: Context) {
        FeatureRegistry.features
            .filter { f -> f.linuxExtensions.isNotEmpty() && PartUsers.isUser(app, f.route) }
            .filter { f -> LinuxParts.missingExtensions(app, f.linuxExtensions).isNotEmpty() }
            .forEach { f -> extensionJob(app, f, f.linuxExtensions) }
    }

    /**
     * Once, for installations from before an installation marked what it unlocked ([markUnlocked]): the
     * tools it unlocked then count as installed too.
     */
    fun markUnlockedOnce(context: Context) {
        val app = context.applicationContext
        if (PreferenceHelper.get(app, UNLOCKED_MARKED, false)) return
        markUnlocked(app)
        PreferenceHelper.put(app, UNLOCKED_MARKED, true)
    }

    private const val UNLOCKED_MARKED = "linux_unlocked_marked"

    /**
     * Records the tools all of whose parts are unpacked (and that don't use them yet) as unlocked: an
     * installation brings the tools on what it unpacked too. They count as installed, but keep no part:
     * one goes with the tool that got it unless they've used it since ([markUsing]).
     */
    private fun markUnlocked(app: Context) {
        if (!LinuxParts.isExtensionUnpacked(app, LinuxParts.CORE)) return
        FeatureRegistry.features
            .filter { it.linuxExtensions.isNotEmpty() && !PartUsers.isUser(app, it.route) && !PartUsers.isUnlocked(app, it.route) }
            .filter { LinuxParts.missingExtensions(app, it.linuxExtensions).isEmpty() }
            .forEach { PartUsers.setUnlocked(app, it.route, true) }
    }

    /** Unlocked tools a part of which is gone (with the tool that got it) are locked again. */
    private fun forgetLocked(app: Context) {
        FeatureRegistry.features
            .filter { PartUsers.isUnlocked(app, it.route) && LinuxParts.missingExtensions(app, it.linuxExtensions).isNotEmpty() }
            .forEach { PartUsers.setUnlocked(app, it.route, false) }
    }

    /** Records the tool behind [source] as using its part (see [PartUsers.isUsing]). */
    private fun markUsing(app: Context, source: AppFeature) {
        if (source.linuxExtensions.isNotEmpty() && LinuxParts.isExtensionUnpacked(app, LinuxParts.CORE)) PartUsers.setUsing(app, source.route, true)
    }

    /**
     * Deletes [feature]'s tools, and whatever else nothing uses anymore (e.g. Python once no feature
     * running on it has its tools), see [unused].
     */
    fun removeExtension(context: Context, feature: AppFeature) = removeExtensions(context) { app ->
        // It doesn't use its part anymore (nor is it unlocked): that goes once no other tool uses it (e.g. pypkgs)
        PartUsers.setUsing(app, feature.route, false)
        PartUsers.setUnlocked(app, feature.route, false)
        unused(app, listOf(feature)).map { it.name }
    }

    /**
     * Deletes (and uninstalls) parts [names] a tool installed itself and doesn't need anymore (its
     * extraExtensions, e.g. a Java version no server of it runs on), unless another tool uses them.
     */
    fun removeParts(context: Context, names: List<String>) = removeExtensions(context) { app ->
        val others = FeatureRegistry.features.filter { hasExtension(app, it) }.flatMap { it.linuxExtensions }.toSet()
        names.filter { it !in others && (LinuxParts.isExtensionUnpacked(app, it) || LinuxParts.isExtensionDownloaded(app, it)) }
    }

    /** Deletes (and uninstalls) whatever nothing uses, e.g. left by an installation that failed half-way; on start. */
    fun removeUnusedExtensions(context: Context) = removeExtensions(context) { app ->
        unused(app, emptyList()).map { it.name }
    }

    /** Deletes every extension and the core, and uninstalls their modules (e.g. resetting every feature). */
    fun removeAllExtensions(context: Context) = removeExtensions(context) { app ->
        LinuxParts.extensions(app).keys.toList() + LinuxParts.CORE
    }

    private val _extensionsRemoved = MutableStateFlow(0)
    /** Bumped once extensions are deleted (in the background): what shows whether they're there looks again. */
    val extensionsRemoved: StateFlow<Int> = _extensionsRemoved.asStateFlow()

    /** Under the lock installs take: never what an installation has unpacked so far but not yet its tool's own. */
    private fun removeExtensions(context: Context, which: (Context) -> List<String>) {
        val app = context.applicationContext
        scope.launch {
            envLock.read {
                extensionLock.withLock {
                    val removed = which(app)
                    if (removed.isNotEmpty()) {
                        // The core last: all of what's unpacked goes with it
                        removed.sortedBy { it == LinuxParts.CORE }.forEach { LinuxInstaller.removeExtension(app, it) }
                        // And their Play modules (with the tool's pages; the core's with PRoot)
                        FeatureDelivery.uninstall(app, removed)
                    }
                    // Tools unlocked by what was removed are locked again
                    forgetLocked(app)
                }
            }
            _extensionsRemoved.update { it + 1 }
        }
    }

    /**
     * The parts on the device (downloaded or unpacked) that nothing uses once [gone]'s data is
     * deleted. A feature uses its own extension while that's unpacked (its data; just downloaded, it's
     * a module waiting to be uninstalled), and with it all it builds on ([LinuxExtension.requires],
     * through and through), the core too; nothing else is used.
     */
    fun unused(context: Context, gone: List<AppFeature>): List<LinuxExtension> {
        val all = LinuxParts.extensions(context)
        val used = mutableSetOf<String>()
        fun use(n: String) {
            if (used.add(n)) all[n]?.requires?.forEach(::use)
        }
        // By tool: a part several tools run on (e.g. pypkgs) stays while any of them uses it
        FeatureRegistry.features.filter { it !in gone && hasExtension(context, it) }
            // Its extra ones it unpacked too (e.g. a Java version a server needed)
            .flatMap { f -> f.linuxExtensions + f.extraExtensions.filter { LinuxParts.isExtensionUnpacked(context, it) } }
            .forEach(::use)
        // The core, while any extension is used
        val core = LinuxParts.core(context)?.takeIf { used.isEmpty() }
        return (all.values + listOfNotNull(core)).filter {
            it.name !in used && (LinuxParts.isExtensionUnpacked(context, it.name) || LinuxParts.isExtensionDownloaded(context, it.name))
        }
    }

    /**
     * Whether [feature] uses its tools: unpacked, and it got them or ran on them (its data; a part
     * several tools share stays while any uses it). Just downloaded, its module is Play's to uninstall
     * (asked for when its data was deleted).
     */
    fun hasExtension(context: Context, feature: AppFeature): Boolean =
        feature.linuxExtensions.isNotEmpty() && feature.linuxExtensions.all { PartUsers.isUsing(context, feature.route, it) }

    /**
     * Null when commands can run. Otherwise (other packages shipped with the app than unpacked) the
     * unpacking is started if it isn't running (e.g. after it failed) and the command is refused.
     */
    private fun notReady(app: Context, leavePage: Boolean): ExecResult? {
        // Not unpacked (up to date then), the core is installed with the command's tool (see prepareExtension)
        if (LinuxParts.isUpToDate(app)) return null
        ensureReady(app)
        if (leavePage) _envNotReady.trySend(Unit)
        return ExecResult(ExecResult.EXIT_ENV_NOT_READY, "", "The Linux subsystem is being set up")
    }

    /** Starts (or joins an already queued/running) update of what's unpacked. */
    private fun startEnvTask(context: Context): Job {
        val app = context.applicationContext
        synchronized(this) {
            _tasks.value.firstOrNull { it.type.isEnvChange && it.isActive }?.let { running ->
                synchronized(jobs) { jobs[running.id] }?.let { return it }
            }
            val task = add(LinuxTask(ids.incrementAndGet(), LinuxTask.Type.Update))
            return launchTask(app, task) {
                envLock.write {
                    set(task.id) { it.copy(state = LinuxTask.State.Running, progress = 0f) }
                    var last = -1
                    val installed = LinuxInstaller.install(app) { p ->
                        // Only push whole-percent changes to the UI / notification
                        if (p.toInt() != last) {
                            last = p.toInt()
                            set(task.id) { it.copy(progress = p) }
                        }
                    }
                    // Script folders no tool has anymore (the others' scripts are synced as they run)
                    val tools = FeatureRegistry.features.mapNotNull { (it as? ModuleFeature)?.scriptDir }.toSet()
                    val done = installed + LinuxScripts.removeStale(app, tools).map { "scripts of $it: removed, no longer used" }
                    // Its log: which parts it unpacked or updated, from which version to which
                    finish(task.id, LinuxTask.State.Done, log = done.joinToString("\n").ifEmpty { null })
                }
                reinstallReplaced(app)
            }
        }
    }

    private fun launchTask(
        app: Context, task: LinuxTask,
        cancelledOutput: () -> ExecResult? = { null },
        block: suspend () -> Unit
    ): Job {
        // LAZY: register the job before it can finish and unregister itself
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } catch (e: CancellationException) {
                finishCancelled(app, task.id, cancelledOutput())
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Task ${task.id} (${task.type}) failed", e)
                finish(task.id, LinuxTask.State.Failed, e.message ?: e.javaClass.simpleName, e.stackTraceToString())
            } finally {
                synchronized(jobs) { jobs.remove(task.id) }
            }
        }
        synchronized(jobs) { jobs[task.id] = job }
        // Only needed (and only shown) while the app is in the background
        LinuxTaskService.startIfInBackground(app)
        job.start()
        return job
    }

    /** Keeps what a canceled command printed until then, or says it printed nothing. */
    private fun finishCancelled(app: Context, id: Long, partial: ExecResult?) =
        finish(
            id, LinuxTask.State.Cancelled,
            log = partial?.let { log(it) } ?: app.getString(R.string.s_tasks_cancelled_no_output)
        )

    private fun summary(r: ExecResult): String? =
        (r.stderr.ifBlank { r.stdout }).lineSequence().lastOrNull { it.isNotBlank() }?.trim()

    private fun log(r: ExecResult): String? =
        r.output.ifBlank { null }?.let { if (it.length > LOG_LIMIT) "…" + it.takeLast(LOG_LIMIT) else it }

    private fun add(task: LinuxTask): LinuxTask {
        _tasks.update { (listOf(task) + it).take(HISTORY_LIMIT) }
        return task
    }

    private fun set(id: Long, change: (LinuxTask) -> LinuxTask) =
        _tasks.update { list -> list.map { if (it.id == id) change(it) else it } }

    private fun finish(
        id: Long, state: LinuxTask.State, message: String? = null, log: String? = null, exitCode: Int? = null
    ) = set(id) {
        if (!it.isActive) it // already finished (e.g. failed before being canceled)
        else it.copy(state = state, message = message, log = log, exitCode = exitCode, finishedAt = System.currentTimeMillis())
    }
}
