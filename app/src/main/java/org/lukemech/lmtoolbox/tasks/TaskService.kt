package org.lukemech.lmtoolbox.tasks

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.MainActivity
import org.lukemech.lmtoolbox.R
import kotlin.time.Duration.Companion.milliseconds
import org.lukemech.lmtoolbox.helpers.createChannel
import org.lukemech.lmtoolbox.helpers.ongoingNotification
import org.lukemech.lmtoolbox.helpers.openActivity

/**
 * Foreground service that keeps the app process alive while the app is in the background and a
 * [LinuxTasks] task is active, mirroring its progress in a notification. While the app is open
 * there is no service and no notification (the bottom bar shows progress instead); it stops as soon
 * as the app comes back or nothing is running.
 */
class TaskService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collecting = false
    /** Env tasks seen running, to post a result notification when they finish. */
    private val watchedEnvTasks = mutableSetOf<Long>()
    /** Last progress notification posted (see [onTasksChanged]) and a throttled one waiting. */
    private var lastNotified: Triple<Long, AppTask.State, Int?>? = null
    private var lastNotifiedAt = 0L
    private var pendingNotify: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must follow every startForegroundService call
        val current = AppTasks.current(AppTasks.tasks.value)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, progressNotification(current),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        if (!collecting) {
            collecting = true
            scope.launch {
                combine(AppTasks.tasks, appInForeground) { tasks, foreground -> tasks to foreground }
                    // Only react to changes that alter what the notification shows
                    .distinctUntilChanged { old, new ->
                        old.second == new.second && notificationKey(old.first) == notificationKey(new.first)
                    }
                    .collect { (tasks, foreground) -> onTasksChanged(tasks, foreground) }
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // guarded by canNotify()
    private fun onTasksChanged(tasks: List<AppTask>, foreground: Boolean) {
        if (foreground) {
            // Back in the app: the process stays alive anyway, progress is in the bottom bar
            pendingNotify?.cancel()
            watchedEnvTasks.clear()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        tasks.filter { it.type.isEnvChange && it.isActive }.forEach { watchedEnvTasks += it.id }
        tasks.filter { it.id in watchedEnvTasks && !it.isActive }.forEach {
            watchedEnvTasks -= it.id
            notifyResult(it)
        }

        val current = AppTasks.current(tasks)
        pendingNotify?.cancel()
        if (current == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        // Android drops updates beyond ~5/s, which froze the bar mid-way: progress-only changes are
        // posted at most once per NOTIFY_INTERVAL_MS (the latest one wins), state changes at once
        val key = notificationKey(tasks)
        val sameTask = key?.first == lastNotified?.first && key?.second == lastNotified?.second
        val wait = lastNotifiedAt + NOTIFY_INTERVAL_MS - SystemClock.elapsedRealtime()
        if (!sameTask || wait <= 0) {
            postProgress(current)
        } else {
            pendingNotify = scope.launch {
                delay(wait.milliseconds)
                AppTasks.current(AppTasks.tasks.value)?.let { postProgress(it) }
            }
        }
    }

    @SuppressLint("MissingPermission") // guarded by canNotify()
    private fun postProgress(task: AppTask) {
        if (!canNotify()) return
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, progressNotification(task))
        lastNotified = notificationKey(listOf(task))
        lastNotifiedAt = SystemClock.elapsedRealtime()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ caps dataSync services at 6h/day; tasks keep running while the app is open
        Log.w(TAG, "Foreground service timed out")
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun progressNotification(task: AppTask?): Notification {
        val builder = ongoingNotification(this, CHANNEL_PROGRESS, R.drawable.linux_penguin)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentTitle(task?.title(this) ?: getString(R.string.s_tasks))
            .setContentText(task?.detail)
            // A tool's command opens its tool (at the part showing it, see AppTask.page), as the bottom bar does; else the task list
            .setContentIntent(
                openActivity(
                    this,
                    task?.source?.takeIf { task.type == AppTask.Type.Command }?.let { MainActivity.openFeatureIntent(this, it.route, page = task.page) }
                        ?: MainActivity.openTasksIntent(this)
                )
            )
        when {
            task == null -> builder.setProgress(0, 0, true)
            // Commands keep their command line as the text, with their progress if they report it
            task.type == AppTask.Type.Command -> task.progress
                ?.let { builder.setProgress(100, it.toInt(), false) }
                ?: builder.setProgress(0, 0, true)
            task.state == AppTask.State.Queued -> builder
                .setContentText(getString(R.string.s_tasks_state_queued))
                .setProgress(0, 0, true)
            task.progress != null -> builder
                .setContentText("${task.progress.toInt()}%")
                .setProgress(100, task.progress.toInt(), false)
            else -> builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    @SuppressLint("MissingPermission") // guarded by canNotify()
    private fun notifyResult(task: AppTask) {
        if (!canNotify() || task.state == AppTask.State.Cancelled) return
        val text = when (task.state) {
            AppTask.State.Done -> getString(R.string.s_tasks_state_done)
            else -> getString(R.string.s_tasks_state_failed) + (task.message?.let { ": $it" } ?: "")
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.linux_penguin)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openActivity(this, MainActivity.openTasksIntent(this)))
            .setContentTitle(task.title(this))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(RESULT_NOTIFICATION_ID, notification)
    }

    private fun canNotify() = NotificationManagerCompat.from(this).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    companion object {
        private const val TAG = "TaskService"
        private const val CHANNEL_PROGRESS = "linux_tasks"
        private const val CHANNEL_RESULT = "linux_task_results"
        private const val NOTIFICATION_ID = 1
        private const val RESULT_NOTIFICATION_ID = 2
        private const val NOTIFY_INTERVAL_MS = 1_000L

        private val appInForeground = MutableStateFlow(false)

        private fun notificationKey(tasks: List<AppTask>) =
            AppTasks.current(tasks)?.let { Triple(it.id, it.state, it.progress?.toInt()) }

        /** MainActivity is visible: no service or notification needed. */
        fun onAppForegrounded() {
            appInForeground.value = true
        }

        /** MainActivity was left: keep running tasks alive (Android grants a short grace period to start this). */
        fun onAppBackgrounded(context: Context) {
            appInForeground.value = false
            startIfInBackground(context)
        }

        /** Starts the service if the app is in the background and a task is active. */
        fun startIfInBackground(context: Context) {
            if (!appInForeground.value && AppTasks.tasks.value.any { it.isActive }) start(context)
        }

        private fun start(context: Context) {
            createChannels(context)
            try {
                ContextCompat.startForegroundService(context, Intent(context, TaskService::class.java))
            } catch (e: IllegalStateException) {
                // Started while the app is in the background (Android 12+): the task still runs
                // as long as the process lives, just without the notification.
                Log.w(TAG, "Could not start foreground service", e)
            }
        }

        private fun createChannels(context: Context) {
            createChannel(context, CHANNEL_PROGRESS, context.getString(R.string.s_tasks_channel_progress))
            createChannel(context, CHANNEL_RESULT, context.getString(R.string.s_tasks_channel_result), NotificationManager.IMPORTANCE_DEFAULT)
        }
    }
}
