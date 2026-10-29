package org.lukemech.lmtoolbox.linux

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import org.lukemech.lmtoolbox.tasks.AppTask
import org.lukemech.lmtoolbox.tasks.AppTasks
import org.lukemech.lmtoolbox.tasks.ToolService
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
 * Each is an app task ([AppTasks]): in the task list, the bottom bar and the notification as any other.
 */
object LinuxTasks {
    private const val TAG = "LinuxTasks"
    private const val LOG_LIMIT = 64 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

    // Kept until collected: a page opened with the app may run a command before MainScreen listens
    private val _envNotReady = Channel<Unit>(Channel.CONFLATED)

    /**
     * A command was refused while the subsystem is being unpacked or updated: the app goes back
     * from the page that ran it and asks to wait (MainScreen).
     */
    val envNotReady: Flow<Unit> = _envNotReady.receiveAsFlow()

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
        val task = AppTasks.add(
            AppTask(
                AppTasks.newId(), AppTask.Type.Command, source,
                command = cmd.joinToString(" "), label = displayCommand(cmd)
            )
        )
        var partial: ExecResult? = null
        val result = try {
            envLock.read {
                AppTasks.set(task.id) { it.copy(state = AppTask.State.Running) }
                LinuxEnv.exec(app, cmd, timeoutMs, task = task.id) { partial = it }
            }
        } catch (e: CancellationException) {
            AppTasks.finish(task.id, AppTask.State.Cancelled, log = partial?.let(::log) ?: app.getString(R.string.s_tasks_cancelled_no_output))
            throw e
        } catch (e: Exception) {
            ExecResult(-1, "", e.stackTraceToString())
        }
        val state = if (result.isSuccess) AppTask.State.Done else AppTask.State.Failed
        AppTasks.finish(task.id, state, if (result.isSuccess) null else summary(result), log(result), result.exitCode)
        return result
    }

    /**
     * Command that keeps running when its page is left (it then shows up in the bottom bar).
     * Cancelling the caller doesn't stop it; use [cancel] for that. Refused like [run] while the
     * subsystem is being set up.
     *
     * [onStart] gets the task's id (for [cancel], [detach], [setProgress]); [onLine] each line
     * the command prints, as it comes. [label] is what the UI shows of it (e.g. a file's name).
     * [maxOutput]: how much of its output is kept (see [LinuxEnv.exec]). [retried]: whether the caller runs it
     * again after this result (a download it couldn't make): then it's done, not failed, the next run the one to tell.
     */
    suspend fun runInBackground(
        context: Context, cmd: List<String>, source: AppFeature,
        label: String = displayCommand(cmd),
        timeoutMs: Long = LinuxEnv.DEFAULT_TIMEOUT_MS,
        maxOutput: Int = LinuxEnv.DEFAULT_MAX_OUTPUT,
        onStart: (id: Long) -> Unit = {},
        onLine: ((String) -> Unit)? = null,
        retried: (ExecResult) -> Boolean = { false },
    ): ExecResult {
        val app = context.applicationContext
        notReady(app, leavePage = true)?.let { return it }
        prepareExtension(app, source, install = true)?.let { return it }

        val result = CompletableDeferred<ExecResult>()
        val task = AppTasks.add(
            AppTask(
                AppTasks.newId(), AppTask.Type.Command, source,
                command = cmd.joinToString(" "), label = label
            )
        )
        onStart(task.id)
        var partial: ExecResult? = null
        AppTasks.launchTask(app, task, cancelledLog = { partial?.let(::log) }) {
            val r = envLock.read {
                AppTasks.set(task.id) { it.copy(state = AppTask.State.Running) }
                LinuxEnv.exec(app, cmd, timeoutMs, onLine, maxOutput, task = task.id) { partial = it }
            }
            result.complete(r)
            val ok = r.isSuccess || retried(r)
            AppTasks.finish(task.id, if (ok) AppTask.State.Done else AppTask.State.Failed, if (ok) null else summary(r), log(r), r.exitCode)
        }.invokeOnCompletion { e ->
            // No-op if the command produced a result; otherwise it failed or was canceled
            val message = AppTasks.tasks.value.firstOrNull { it.id == task.id }?.message
            result.complete(ExecResult(-1, "", message ?: e?.message ?: "Failed"))
        }
        return try {
            result.await()
        } catch (e: CancellationException) {
            // The page was left: the command goes on and becomes visible in the bottom bar
            AppTasks.set(task.id) { it.copy(detached = true) }
            throw e
        }
    }

    /**
     * A tool's program running until it's stopped (a server, its tunnel), its tool keeping it going
     * with a service of its own (and its notification): not a task. It's in neither the task list nor
     * the subsystem's notification, and in the bottom bar as what runs ([services]), without progress. It
     * holds the subsystem as any command does (an update waits for it; other commands don't).
     * Refused like [run] while the subsystem is set up; its tool's parts unpacked first, as a task.
     *
     * [onStart] gets its id (for [cancel], which stops it); [onLine] each line it prints. Cancelling
     * the caller doesn't stop it. Of its output only the last [maxOutput] characters are kept. [group]:
     * the label of the service it's part of (see [ToolService.group]).
     */
    suspend fun runService(
        context: Context, cmd: List<String>, source: AppFeature, label: String,
        maxOutput: Int = 64 * 1024,
        group: String? = null,
        onStart: (id: Long) -> Unit = {},
        onLine: ((String) -> Unit)? = null
    ): ExecResult {
        val app = context.applicationContext
        notReady(app, leavePage = true)?.let { return it }
        prepareExtension(app, source, install = true)?.let { return it }

        val service = ToolService(AppTasks.newId(), source, label, group)
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
                AppTasks.untrack(service.id)
                AppTasks.serviceEnded(service.id)
            }
        }
        AppTasks.track(service.id, job)
        AppTasks.addService(service)
        onStart(service.id)
        job.start()
        return result.await()
    }

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
        val failed = AppTasks.tasks.value.firstOrNull { it.type == AppTask.Type.Extension && it.source == source }
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
            val underway = AppTasks.tasks.value.filter { t -> t.isActive && t.type == AppTask.Type.Extension && missing.any { it.name in t.packages } }
            val waitFor = if (missing.all { m -> underway.any { m.name in it.packages } }) {
                underway.mapNotNull { AppTasks.job(it.id) }
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
        val task = AppTasks.add(
            AppTask(
                AppTasks.newId(), AppTask.Type.Extension, source,
                label = app.getString(source.titleStringResId),
                detached = true,
                packages = missing.map { it.name },
                downloads = missing.map { it.name }.filterNot { LinuxParts.isExtensionDownloaded(app, it) }.toSet()
            )
        )
        return AppTasks.launchTask(app, task) {
            // Not during an installation or update of the core, which would take it away again
            envLock.read {
                extensionLock.withLock {
                    _packages.value = missing.associate { it.name to PackageState.Waiting }
                    // From Play first: each extension is the module of the same name, 
                    val downloading = missing.map { it.name }.filterNot { LinuxParts.isExtensionDownloaded(app, it) }
                    var last = -1
                    // Downloading only shows when something is
                    AppTasks.set(task.id) {
                        it.copy(state = AppTask.State.Running, stage = R.string.s_tasks_downloading.takeIf { downloading.isNotEmpty() }, progress = null)
                    }
                    FeatureDelivery.install(app, downloading) { p ->
                        if ((p?.toInt() ?: -1) != last) {
                            last = p?.toInt() ?: -1
                            AppTasks.set(task.id) { it.copy(progress = p) }
                            downloading.forEach { setPackage(it, PackageState.Downloading(p)) }
                        }
                    }
                    downloading.forEach { setPackage(it, PackageState.Downloaded) }
                    // What's unpacked of a package whose module wasn't there (e.g. Play uninstalled it) is
                    // stale: a fresh download is unpacked afresh
                    downloading.filter { LinuxParts.isExtensionUnpacked(app, it) }.forEach { LinuxInstaller.removeExtension(app, it) }
                    AppTasks.set(task.id) { it.copy(stage = null, progress = 0f) }
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
                                    AppTasks.set(task.id) { it.copy(progress = overall) }
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
            AppTasks.finish(task.id, AppTask.State.Done)
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
     * through and through), the core too; so does one of a tool's extra ones it uses now (see
     * AppFeature.usesExtraExtension), with what that builds on; nothing else is used.
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
        // And those a tool uses now, one on no part of its own too (the folder share's tunnels)
        FeatureRegistry.features.filter { it !in gone }
            .flatMap { f -> f.extraExtensions.filter { LinuxParts.isExtensionUnpacked(context, it) && f.usesExtraExtension(context, it) } }
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
            AppTasks.tasks.value.firstOrNull { it.type.isEnvChange && it.isActive }?.let { running ->
                AppTasks.job(running.id)?.let { return it }
            }
            val task = AppTasks.add(AppTask(AppTasks.newId(), AppTask.Type.Update))
            return AppTasks.launchTask(app, task) {
                envLock.write {
                    AppTasks.set(task.id) { it.copy(state = AppTask.State.Running, progress = 0f) }
                    var last = -1
                    val installed = LinuxInstaller.install(app) { p ->
                        // Only push whole-percent changes to the UI / notification
                        if (p.toInt() != last) {
                            last = p.toInt()
                            AppTasks.set(task.id) { it.copy(progress = p) }
                        }
                    }
                    // Script folders no tool has anymore (the others' scripts are synced as they run)
                    val tools = FeatureRegistry.features.mapNotNull { (it as? ModuleFeature)?.scriptDir }.toSet()
                    val done = installed + LinuxScripts.removeStale(app, tools).map { "scripts of $it: removed, no longer used" }
                    // Its log: which parts it unpacked or updated, from which version to which
                    AppTasks.finish(task.id, AppTask.State.Done, log = done.joinToString("\n").ifEmpty { null })
                }
                reinstallReplaced(app)
            }
        }
    }

    private fun summary(r: ExecResult): String? =
        (r.stderr.ifBlank { r.stdout }).lineSequence().lastOrNull { it.isNotBlank() }?.trim()

    private fun log(r: ExecResult): String? =
        r.output.ifBlank { null }?.let { if (it.length > LOG_LIMIT) "…" + it.takeLast(LOG_LIMIT) else it }

}
