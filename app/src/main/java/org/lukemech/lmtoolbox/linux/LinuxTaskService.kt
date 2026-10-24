package org.lukemech.lmtoolbox.linux

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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

/**
 * Foreground service that keeps the app process alive while the app is in the background and a
 * [LinuxTasks] task is active, mirroring its progress in a notification. While the app is open
 * there is no service and no notification (the bottom bar shows progress instead); it stops as soon
 * as the app comes back or nothing is running.
 */
class LinuxTaskService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collecting = false
    /** Env tasks seen running, to post a result notification when they finish. */
    private val watchedEnvTasks = mutableSetOf<Long>()
    /** Last progress notification posted (see [onTasksChanged]) and a throttled one waiting. */
    private var lastNotified: Triple<Long, LinuxTask.State, Int?>? = null
    private var lastNotifiedAt = 0L
    private var pendingNotify: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must follow every startForegroundService call
        val current = LinuxTasks.current(LinuxTasks.tasks.value)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, progressNotification(current),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        if (!collecting) {
            collecting = true
            scope.launch {
                combine(LinuxTasks.tasks, appInForeground) { tasks, foreground -> tasks to foreground }
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
    private fun onTasksChanged(tasks: List<LinuxTask>, foreground: Boolean) {
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

        val current = LinuxTasks.current(tasks)
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
                LinuxTasks.current(LinuxTasks.tasks.value)?.let { postProgress(it) }
            }
        }
    }

    @SuppressLint("MissingPermission") // guarded by canNotify()
    private fun postProgress(task: LinuxTask) {
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

    private fun progressNotification(task: LinuxTask?): Notification {
        val builder = baseBuilder(CHANNEL_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // Android 12+ otherwise holds it back ~10s, so tasks shorter than that showed nothing
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentTitle(task?.title(this) ?: getString(R.string.s_tasks))
            .setContentText(task?.detail)
        when {
            task == null -> builder.setProgress(0, 0, true)
            // Commands keep their command line as the text, with their progress if they report it
            task.type == LinuxTask.Type.Command -> task.progress
                ?.let { builder.setProgress(100, it.toInt(), false) }
                ?: builder.setProgress(0, 0, true)
            task.state == LinuxTask.State.Queued -> builder
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
    private fun notifyResult(task: LinuxTask) {
        if (!canNotify() || task.state == LinuxTask.State.Cancelled) return
        val text = when (task.state) {
            LinuxTask.State.Done -> getString(R.string.s_tasks_state_done)
            else -> getString(R.string.s_tasks_state_failed) + (task.message?.let { ": $it" } ?: "")
        }
        val notification = baseBuilder(CHANNEL_RESULT)
            .setContentTitle(task.title(this))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(RESULT_NOTIFICATION_ID, notification)
    }

    private fun baseBuilder(channel: String) = NotificationCompat.Builder(this, channel)
        .setSmallIcon(R.drawable.linux_penguin)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, MainActivity.openTasksIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

    private fun canNotify() = NotificationManagerCompat.from(this).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    companion object {
        private const val TAG = "LinuxTaskService"
        private const val CHANNEL_PROGRESS = "linux_tasks"
        private const val CHANNEL_RESULT = "linux_task_results"
        private const val NOTIFICATION_ID = 1
        private const val RESULT_NOTIFICATION_ID = 2
        private const val NOTIFY_INTERVAL_MS = 1_000L

        private val appInForeground = MutableStateFlow(false)

        private fun notificationKey(tasks: List<LinuxTask>) =
            LinuxTasks.current(tasks)?.let { Triple(it.id, it.state, it.progress?.toInt()) }

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
            if (!appInForeground.value && LinuxTasks.tasks.value.any { it.isActive }) start(context)
        }

        private fun start(context: Context) {
            createChannels(context)
            try {
                ContextCompat.startForegroundService(context, Intent(context, LinuxTaskService::class.java))
            } catch (e: IllegalStateException) {
                // Started while the app is in the background (Android 12+): the task still runs
                // as long as the process lives, just without the notification.
                Log.w(TAG, "Could not start foreground service", e)
            }
        }

        private fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.s_tasks_channel_progress), NotificationManager.IMPORTANCE_LOW)
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RESULT, context.getString(R.string.s_tasks_channel_result), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
}
