package org.lukemech.lmtoolbox.features.servers.mc_server

import org.lukemech.lmtoolbox.R
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.MainActivity
import org.lukemech.lmtoolbox.linux.parts.Tunnel

/**
 * Keeps the app (and with it the server) going while a server runs, the app left or the screen off:
 * a notification telling how it's doing (with a button stopping it), the CPU and Wi-Fi kept awake
 * for the players. Stops once the server has.
 */
class ServerService : Service() {
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
            this, NOTIFICATION_ID, notification(ServerRunner.state.value, ServerRunner.tunnel.value),
            // The type exists from Android 14 (as the app's widgets' services have it)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        lastStartId = startId
        if (intent?.action == ACTION_STOP) ServerRunner.stop()
        acquireLocks()

        if (!collecting) {
            collecting = true
            scope.launch {
                combine(ServerRunner.state, ServerRunner.tunnel) { run, tunnel -> run to tunnel }
                    .distinctUntilChanged { a, b -> key(a.first, a.second) == key(b.first, b.second) }
                    .collect { (run, tunnel) ->
                        if (run?.isActive != true) {
                            // Started again meanwhile (a restart): it goes on. Else stopped only if no start
                            // has come since (one that hasn't reached onStartCommand yet would never get its
                            // startForeground, and Android kills the app for that)
                            if (ServerRunner.state.value?.isActive == true) return@collect
                            ServiceCompat.stopForeground(this@ServerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelfResult(lastStartId)
                        } else {
                            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(run, tunnel))
                        }
                    }
            }
        }
        // Killed with the app, the server is gone too: nothing to come back for
        return START_NOT_STICKY
    }

    /** What the notification shows of it changed. */
    private fun key(run: ServerRunner.Run?, tunnel: ServerRunner.ServerTunnel?) =
        listOf(run?.phase, run?.online, run?.name, tunnel?.state?.phase)

    /**
     * The CPU and Wi-Fi kept going with the screen off (it goes off as always: neither keeps it on),
     * else Android puts them to sleep and the players freeze or drop. Held while the server runs alone.
     */
    @SuppressLint("WakelockTimeout") // Until it's stopped: a server has no end of its own
    private fun acquireLocks() {
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LMToolbox:MinecraftServer")
                .apply { setReferenceCounted(false); acquire() }
        }
        if (wifiLock == null) {
            // The only mode that keeps Wi-Fi up with the screen off; deprecated for apps that only need it
            // while they're in front (low latency), which a server isn't
            @Suppress("DEPRECATION")
            wifiLock = getSystemService(WifiManager::class.java)
                ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LMToolbox:MinecraftServer")
                ?.apply { setReferenceCounted(false); acquire() }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun notification(run: ServerRunner.Run?, tunnel: ServerRunner.ServerTunnel?): Notification {
        val status = getString(
            when (run?.phase) {
                ServerRunner.Phase.Running -> R.string.f_Mc_phase_running
                ServerRunner.Phase.Stopping -> R.string.f_Mc_phase_stopping
                else -> R.string.f_Mc_phase_starting
            }
        )
        val details = listOfNotNull(
            status,
            run?.online?.let { resources.getQuantityString(R.plurals.f_Mc_players_online, it, it) },
            getString(R.string.f_Mc_tunnel_short).takeIf { tunnel?.state?.phase == Tunnel.Phase.Connected }
        ).joinToString(" · ")
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.mc_block)
            .setContentTitle(run?.name ?: getString(R.string.fp_McServer))
            .setContentText(details)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .apply {
                if (run?.phase != ServerRunner.Phase.Stopping) {
                    addAction(0, getString(R.string.f_Mc_stop), stop)
                }
            }
            .build()
    }

    companion object {
        private const val TAG = "ServerService"
        private const val CHANNEL = "minecraft_server"
        /** Not the subsystem's notifications' (LinuxTaskService: 1, 2). */
        private const val NOTIFICATION_ID = 0x4d43
        private const val ACTION_STOP = "org.lukemech.lmtoolbox.features.servers.mc_server.STOP"

        /** Started with the server (the app is in front then: Android allows it). */
        fun start(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.f_Mc_channel), NotificationManager.IMPORTANCE_LOW)
            )
            try {
                ContextCompat.startForegroundService(context, Intent(context, ServerService::class.java))
            } catch (e: IllegalStateException) {
                // The server still runs while the app is in front
                Log.w(TAG, "Couldn't start the server's service", e)
            }
        }
    }
}
