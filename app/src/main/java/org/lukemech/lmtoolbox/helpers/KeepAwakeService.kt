package org.lukemech.lmtoolbox.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.MainActivity

/**
 * Keeps the app going while a tool's work runs until it's stopped (a server), the app left or the screen off: a
 * notification telling how it's doing (with a button stopping it), the CPU and Wi-Fi kept awake for it. Stops
 * once the work has. The Minecraft server's ServerService and the folder share's ShareService.
 */
abstract class KeepAwakeService : Service() {
    /** What the notification shows: opening [route]'s tool, a Stop button labeled [stop] (none: null). */
    data class Shown(val title: String, val text: String, val route: String, val stop: String?)

    /** Not another's: the subsystem's notifications are 1 and 2. */
    protected abstract val notificationId: Int
    protected abstract val channel: String
    @get:DrawableRes protected abstract val icon: Int
    /** The wake and Wi-Fi locks' name, e.g. "LMToolbox:FolderShare". */
    protected abstract val lockTag: String

    /** Whether the work runs (or starts): an end seen meanwhile doesn't stop the service then (a restart). */
    protected abstract val isActive: Boolean
    /** What the notification shows now, before [shown] says. */
    protected abstract fun shownNow(): Shown
    /** What the notification shows as it changes; null once the work has stopped. */
    protected abstract fun shown(): Flow<Shown?>
    /** Its Stop button tapped. */
    protected abstract fun stopWork()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var collecting = false
    /** The last start's: stopping goes with it, so a start come meanwhile (a restart) isn't stopped too. */
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must follow every startForegroundService call
        ServiceCompat.startForeground(
            this, notificationId, notification(shownNow()), SPECIAL_USE_SERVICE
        )
        lastStartId = startId
        if (intent?.action == ACTION_STOP) stopWork()
        acquireLocks()

        if (!collecting) {
            collecting = true
            scope.launch {
                shown().distinctUntilChanged().collect { shown ->
                    if (shown != null) {
                        getSystemService(NotificationManager::class.java).notify(notificationId, notification(shown))
                        return@collect
                    }
                    // Started again meanwhile (a restart): it goes on. Else stopped only if no start has come
                    // since (one that hasn't reached onStartCommand yet would never get its startForeground,
                    // and Android kills the app for that)
                    if (isActive) return@collect
                    ServiceCompat.stopForeground(this@KeepAwakeService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelfResult(lastStartId)
                }
            }
        }
        // Killed with the app, the work is gone too: nothing to come back for
        return START_NOT_STICKY
    }

    /**
     * The CPU and Wi-Fi kept going with the screen off (it goes off as always: neither keeps it on), else
     * Android puts them to sleep mid-work (players freeze, a download stalls).
     */
    @SuppressLint("WakelockTimeout") // Until it's stopped: a server has no end of its own
    private fun acquireLocks() {
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, lockTag)
                .apply { setReferenceCounted(false); acquire() }
        }
        if (wifiLock == null) {
            // The only mode that keeps Wi-Fi up with the screen off; deprecated for apps that only need it
            // while they're in front (low latency), which a server isn't
            @Suppress("DEPRECATION")
            wifiLock = getSystemService(WifiManager::class.java)
                ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, lockTag)
                ?.apply { setReferenceCounted(false); acquire() }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun notification(shown: Shown): Notification {
        return ongoingNotification(this, channel, icon)
            .setContentTitle(shown.title)
            .setContentText(shown.text)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openActivity(this, MainActivity.openFeatureIntent(this, shown.route)))
            .apply {
                shown.stop?.let {
                    val stop = PendingIntent.getService(
                        this@KeepAwakeService, 1, Intent(this@KeepAwakeService, this@KeepAwakeService.javaClass).setAction(ACTION_STOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    addAction(0, it, stop)
                }
            }
            .build()
    }

    companion object {
        private const val TAG = "KeepAwakeService"
        /** Each service's own: an intent names its class. */
        private const val ACTION_STOP = "org.lukemech.lmtoolbox.STOP_WORK"

        /** Starts [service] with its work, its notifications in [channel] ([channelName]); the app is in front then: Android allows it. */
        fun start(context: Context, service: Class<out KeepAwakeService>, channel: String, channelName: String) {
            createChannel(context, channel, channelName)
            try {
                ContextCompat.startForegroundService(context, Intent(context, service))
            } catch (e: IllegalStateException) {
                // The work still runs while the app is in front
                Log.w(TAG, "Couldn't start ${service.simpleName}", e)
            }
        }
    }
}
