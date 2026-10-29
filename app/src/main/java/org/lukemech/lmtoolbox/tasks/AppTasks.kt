package org.lukemech.lmtoolbox.tasks

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature

data class AppTask(
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
    /**
     * The part of its tool's page showing it (e.g. a tab), for a tool's own work ([AppTasks.launch]): the bottom
     * bar and the notification open it there; null: the page as it is.
     */
    val page: String? = null,
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
 * A tool's work running until it's stopped (a server, its tunnel: see [AppTasks.serviceStarted], LinuxTasks.runService):
 * not a task. [source] is the tool running it, [label] what's running (e.g. the server's name).
 */
data class ToolService(
    val id: Long,
    val source: AppFeature,
    val label: String,
    /** What it's part of, as that one's [label] (e.g. a server's, for its tunnels): one with it in the bottom bar. */
    val group: String? = null,
) {
    /** The icon of the tool running it. */
    val featureIcon: ImageVector
        @Composable get() = source.icon
}

/**
 * The app's tasks: what a tool does in the background, the Linux subsystem's commands (LinuxTasks) and a tool's own work
 * ([launch]: a speed test, a scan) alike, as the user sees them: listed (the task list, newest first), in the bottom bar
 * with their progress (once their page is left), and in the notification ([TaskService]) while the app's in the back.
 * A tool's work running until it's stopped is a service instead ([serviceStarted]); a failure outside a task is
 * reported as one ([reportFailure]).
 */
object AppTasks {
    private const val TAG = "AppTasks"
    private const val HISTORY_LIMIT = 50

    /** The tasks' own: leaving a page doesn't stop them. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ids = AtomicLong()
    private val jobs = mutableMapOf<Long, Job>()

    /** A new task's (or service's) id. */
    internal fun newId() = ids.incrementAndGet()

    /** The job running task (or service) [id], if it runs: [cancel] stops it. */
    internal fun job(id: Long): Job? = synchronized(jobs) { jobs[id] }

    internal fun track(id: Long, job: Job) = synchronized(jobs) { jobs[id] = job }

    internal fun untrack(id: Long) = synchronized(jobs) { jobs.remove(id) }

    /** A service in the bottom bar (see [serviceStarted]). */
    internal fun addService(service: ToolService) = _services.update { it + service }

    private val _tasks = MutableStateFlow<List<AppTask>>(emptyList())
    /** Newest first. */
    val tasks: StateFlow<List<AppTask>> = _tasks.asStateFlow()

    /** The task for the notification: an env change first, else the oldest active command. */
    fun current(tasks: List<AppTask>): AppTask? =
        tasks.filter { it.isActive }.let { active ->
            active.lastOrNull { it.type.isEnvChange } ?: active.lastOrNull()
        }

    /** The task for the in-app bottom bar: like [current], but only commands whose page was left. */
    fun barTask(tasks: List<AppTask>): AppTask? =
        current(tasks.filter { it.type.isEnvChange || it.detached })

    /**
     * A tool's own work, not on the subsystem (e.g. a speed test, a scan): a task as a command is, run in the app's
     * scope (leaving the page doesn't stop it; [cancel] does). Its progress ([setProgress]) shows as a command's, in
     * the notification while the app's in the back and in the bottom bar while [page] (the part of its tool's page
     * showing it) isn't in sight (see [setShown]); tapped, they open it there. [onStart] gets its id before [block]
     * runs; what [block] throws fails it.
     */
    fun launch(
        context: Context, source: AppFeature, label: String, page: String? = null,
        onStart: (id: Long) -> Unit = {}, block: suspend () -> Unit,
    ): Long {
        val task = add(
            AppTask(newId(), AppTask.Type.Command, source, label = label, state = AppTask.State.Running, page = page)
        )
        onStart(task.id)
        launchTask(context.applicationContext, task) {
            block()
            finish(task.id, AppTask.State.Done)
        }
        return task.id
    }

    /** Whether [launch]'s task [id] is in sight on its page ([AppTask.page]): out of the bottom bar then, else in it. */
    fun setShown(id: Long, shown: Boolean) = set(id) { it.copy(detached = !shown) }

    private val _services = MutableStateFlow<List<ToolService>>(emptyList())
    /** What runs as a service ([serviceStarted], LinuxTasks.runService), oldest first: for the bottom bar. */
    val services: StateFlow<List<ToolService>> = _services.asStateFlow()

    /**
     * A tool's own service, not on the subsystem (e.g. the folder share's server), in the bottom bar as
     * LinuxTasks.runService's are while it runs: its id, for [serviceEnded].
     */
    fun serviceStarted(source: AppFeature, label: String): Long {
        val service = ToolService(newId(), source, label)
        addService(service)
        return service.id
    }

    /** [serviceStarted]'s service stopped: out of the bottom bar. */
    fun serviceEnded(id: Long) = _services.update { list -> list.filterNot { it.id == id } }

    /**
     * A tool's failure outside a command (e.g. a download of its own, a server that crashed), as a failed
     * task: errors are told in one place, the bottom bar's red line (tapping it opens the tool), its [log]
     * in the task list. [label]: what it was for (e.g. a server's name).
     */
    fun reportFailure(source: AppFeature, label: String, message: String, log: String? = null) {
        val now = System.currentTimeMillis()
        add(
            AppTask(
                newId(), AppTask.Type.Command, source, label = label,
                state = AppTask.State.Failed, message = message, log = log ?: message, startedAt = now, finishedAt = now
            )
        )
    }

    /** Stops a task (see [launch]) or a service (LinuxTasks.runService). */
    fun cancel(id: Long) {
        job(id)?.cancel()
    }

    /** A background command started outside a page (see LinuxTasks.runInBackground) whose page was left: shown in the bottom bar. */
    fun detach(id: Long) = set(id) { it.copy(detached = true) }

    /** Whether task [id] (null: none started yet) was canceled, e.g. to tell that from its failing. */
    fun isCancelled(id: Long?) = id != null && _tasks.value.firstOrNull { it.id == id }?.state == AppTask.State.Cancelled

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
    fun barFailure(tasks: List<AppTask>): AppTask? =
        tasks.firstOrNull { it.state == AppTask.State.Failed && !it.seen && !it.type.isEnvChange }

    /** The failures were looked at (their logs opened): the bottom bar lets them be. */
    fun markFailuresSeen() = _tasks.update { list -> list.map { if (it.state == AppTask.State.Failed) it.copy(seen = true) else it } }

    internal fun launchTask(
        app: Context, task: AppTask,
        /** What a canceled task did until then, for its log (none: it says so). */
        cancelledLog: () -> String? = { null },
        block: suspend () -> Unit
    ): Job {
        // LAZY: register the job before it can finish and unregister itself
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } catch (e: CancellationException) {
                finish(task.id, AppTask.State.Cancelled, log = cancelledLog() ?: app.getString(R.string.s_tasks_cancelled_no_output))
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Task ${task.id} (${task.type}) failed", e)
                finish(task.id, AppTask.State.Failed, e.message ?: e.javaClass.simpleName, e.stackTraceToString())
            } finally {
                untrack(task.id)
            }
        }
        track(task.id, job)
        // Only needed (and only shown) while the app is in the background
        TaskService.startIfInBackground(app)
        job.start()
        return job
    }

    internal fun add(task: AppTask): AppTask {
        _tasks.update { (listOf(task) + it).take(HISTORY_LIMIT) }
        return task
    }

    internal fun set(id: Long, change: (AppTask) -> AppTask) =
        _tasks.update { list -> list.map { if (it.id == id) change(it) else it } }

    internal fun finish(
        id: Long, state: AppTask.State, message: String? = null, log: String? = null, exitCode: Int? = null
    ) = set(id) {
        if (!it.isActive) it // already finished (e.g. failed before being canceled)
        else it.copy(state = state, message = message, log = log, exitCode = exitCode, finishedAt = System.currentTimeMillis())
    }
}
