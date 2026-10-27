package org.lukemech.lmtoolbox.features.servers.folder_share

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.MainActivity
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.common.Reach
import org.lukemech.lmtoolbox.linux.parts.Bore
import org.lukemech.lmtoolbox.linux.parts.Tunnel

/**
 * Keeps the app (and with it the folder's server) going while it's shared, the app left or the screen off: a
 * notification with its address and who's connected (and a button stopping it), the CPU and Wi-Fi kept awake.
 * Stops once the share has. As the Minecraft server's ServerService.
 */
class ShareService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var collecting = false
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must follow every startForegroundService call
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(FolderShare.state.value, null),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        lastStartId = startId
        if (intent?.action == ACTION_STOP) FolderShare.stop()
        acquireLocks()

        if (!collecting) {
            collecting = true
            // Who's connected changes as time goes too (counted within a while of their last request)
            val ticks = flow {
                while (true) {
                    emit(Unit)
                    delay(30.seconds)
                }
            }
            scope.launch {
                combine(FolderShare.state, FolderShare.tunnels.tunnel, FolderShare.tunnels.bore, ticks) { run, tunnel, bore, _ ->
                    run to address(run, tunnel?.state, bore?.state)
                }
                    .distinctUntilChanged { a, b -> key(a.first, a.second) == key(b.first, b.second) }
                    .collect { (run, address) ->
                        if (run?.isActive != true) {
                            if (FolderShare.isRunning) return@collect
                            ServiceCompat.stopForeground(this@ShareService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelfResult(lastStartId)
                        } else {
                            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(run, address))
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    /** The address it's best opened at: a tunnel's (from anywhere), else the network's. */
    private fun address(run: FolderShare.Run?, tunnel: Tunnel.State?, bore: Bore.State?): String? {
        run?.takeIf { it.isActive } ?: return null
        tunnel?.url?.takeIf { tunnel.phase == Tunnel.Phase.Connected }?.let { return it }
        bore?.address?.takeIf { bore.phase == Bore.Phase.Connected }?.let { return "http://$it" }
        return Reach.lanAddresses(this).firstOrNull()?.let { "http://$it:${run.port}" }
    }

    private fun key(run: FolderShare.Run?, address: String?) = listOf(run?.phase, run?.name, run?.connected(), address)

    /** The CPU and Wi-Fi kept going with the screen off, else Android puts them to sleep mid-download. */
    @SuppressLint("WakelockTimeout") // Until it's stopped: a share has no end of its own (but its idle stop)
    private fun acquireLocks() {
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LMToolbox:FolderShare")
                .apply { setReferenceCounted(false); acquire() }
        }
        if (wifiLock == null) {
            // The only mode that keeps Wi-Fi up with the screen off (see ServerService)
            @Suppress("DEPRECATION")
            wifiLock = getSystemService(WifiManager::class.java)
                ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LMToolbox:FolderShare")
                ?.apply { setReferenceCounted(false); acquire() }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun notification(run: FolderShare.Run?, address: String?): Notification {
        val connected = run?.connected() ?: 0
        val details = listOfNotNull(
            address ?: getString(R.string.f_Fs_phase_running),
            resources.getQuantityString(R.plurals.f_Fs_connected, connected, connected).takeIf { connected > 0 }
        ).joinToString(" · ")
        val open = PendingIntent.getActivity(
            this, 0, MainActivity.openFeatureIntent(this, FolderShareFeature.route),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_folder_share)
            .setContentTitle(run?.name?.let { getString(R.string.f_Fs_notification_title, it) } ?: getString(R.string.fp_FolderShare))
            .setContentText(details)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(0, getString(R.string.f_Fs_stop), stop)
            .build()
    }

    companion object {
        private const val TAG = "ShareService"
        private const val CHANNEL = "folder_share"
        /** Not the subsystem's (1, 2) nor the Minecraft server's (0x4d43). */
        private const val NOTIFICATION_ID = 0x4653
        private const val ACTION_STOP = "org.lukemech.lmtoolbox.features.servers.folder_share.STOP"

        /** Started with the share (the app is in front then: Android allows it). */
        fun start(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.fp_FolderShare), NotificationManager.IMPORTANCE_LOW)
            )
            try {
                ContextCompat.startForegroundService(context, Intent(context, ShareService::class.java))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Couldn't start the share's service", e)
            }
        }
    }
}
