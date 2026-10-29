package org.lukemech.lmtoolbox.ui.widgets.helpers

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.createChannel
import org.lukemech.lmtoolbox.helpers.ongoingNotification
import org.lukemech.lmtoolbox.helpers.SPECIAL_USE_SERVICE

/**
 * Keeps a widget live (e.g. moving with the sensors): background apps get no sensor readings, so
 * this runs in the foreground, with a notification that opens the feature or switches it off.
 * Switched on and off by tapping the widget ([toggleIntent]); stops with the screen, so it can't
 * drain the battery when forgotten.
 *
 * [onFrame] runs once per display refresh (Choreographer, like the app's animations, e.g. 120
 * times a second on a 120 Hz screen); everything runs on the main thread.
 *
 * Watchdogs keep it going when the system holds back the sensors or display frames from a process
 * that only has this service (e.g. started from the widget while the app wasn't running): sensors
 * are registered again while they stay silent, and a timer stands in for missing frames.
 */
abstract class LiveWidgetService : Service(), SensorEventListener {

    protected abstract val widget: FeatureWidget
    protected abstract val notificationId: Int
    protected abstract val notificationIcon: Int
    protected abstract val notificationTitle: Int
    protected abstract val notificationText: Int

    /** Sensors the widget follows (Sensor.TYPE_*), read at the UI rate while live. */
    protected abstract val sensorTypes: IntArray

    /** Sets the widget's first live frame (the sensors start right after). */
    protected abstract fun onLiveStart()

    /** Clears the live frame, so the widget shows it's off (the sensors are already stopped). */
    protected abstract fun onLiveStop()

    protected abstract fun onReading(event: SensorEvent)

    protected abstract fun onReadingAccuracy(sensor: Sensor, accuracy: Int)

    protected abstract fun onFrame(frameTimeNanos: Long)

    /**
     * Foreground service types (ServiceInfo.FOREGROUND_SERVICE_TYPE_*) the widget needs beyond
     * reading sensors, e.g. location; also declared on its `<service>` in the manifest.
     */
    protected open fun extraServiceTypes(): Int = 0

    /** Whether the system let the service run with [extraServiceTypes] (set before [onLiveStart] and [onRefresh]). */
    protected var hasExtraServiceTypes = false
        private set

    /** Something the widget depends on changed while live (see [refresh]); the service types are set again before. */
    protected open fun onRefresh() {}

    /**
     * Switched off on the widget: how long (ms) to keep drawing frames first, e.g. to animate
     * back to how it looks while off; 0 stops right away. See [isStopping].
     */
    protected open fun stoppingMs(): Long = 0

    /** Switched off, still drawing its last frames ([stoppingMs]); switching on again cancels it. */
    protected var isStopping = false
        private set

    private val stop = Runnable { stopSelf() }

    private var running = false
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val handler = Handler(Looper.getMainLooper())
    private var lastReadingAt = 0L
    private var lastFrameAt = 0L
    private var framesStalled = false

    private fun registerSensors() {
        for (type in sensorTypes) {
            sensorManager.getDefaultSensor(type)?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    final override fun onSensorChanged(event: SensorEvent) {
        lastReadingAt = SystemClock.uptimeMillis()
        onReading(event)
    }

    final override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = onReadingAccuracy(sensor, accuracy)

    /** Registers the sensors again while no readings come (they can be held back right at start). */
    private val sensorWatchdog = object : Runnable {
        override fun run() {
            if (!running) return
            val silentFor = SystemClock.uptimeMillis() - lastReadingAt
            if (silentFor >= SENSOR_STALL_MS) {
                Log.w(TAG, "No sensor readings for ${silentFor}ms, registering the sensors again")
                sensorManager.unregisterListener(this@LiveWidgetService)
                registerSensors()
                lastReadingAt = SystemClock.uptimeMillis()
            }
            handler.postDelayed(this, SENSOR_STALL_MS)
        }
    }

    /** When the next timer frame is due (uptime ms), see [frameWatchdog]. */
    private var nextTimerFrame = 0.0

    /** Draws frames on a timer, [FALLBACK_FPS] a second, while the display sends none. */
    private val frameWatchdog = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val stalled = now - lastFrameAt > FRAME_STALL_NS
            if (!stalled) {
                handler.postDelayed(this, FRAME_CHECK_MS)
                return
            }
            if (!framesStalled) Log.w(TAG, "No display frames, drawing on a timer")
            framesStalled = true
            onFrame(now)
            // A steady beat, however long drawing took (catching up only to now, not with a burst)
            nextTimerFrame = maxOf(nextTimerFrame + 1000.0 / FALLBACK_FPS, SystemClock.uptimeMillis().toDouble())
            handler.postAtTime(this, nextTimerFrame.toLong())
        }
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = stopSelf()
    }

    private val frameCallback: Choreographer.FrameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        if (!running) return@FrameCallback
        lastFrameAt = frameTimeNanos
        framesStalled = false
        onFrame(frameTimeNanos)
        scheduleFrame()
    }

    private fun scheduleFrame(): Unit = Choreographer.getInstance().postFrameCallback(frameCallback)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) {
            // Not a foreground start: only reaches a running service, see refresh()
            if (running) {
                startInForeground()
                onRefresh()
            } else stopSelf()
            return START_NOT_STICKY
        }
        // startForeground must follow every startForegroundService call, even one switching it off
        startInForeground()
        when {
            !running -> start()
            intent?.action != ACTION_TOGGLE -> {}
            // Tapped again while going off: stays on
            isStopping -> {
                isStopping = false
                handler.removeCallbacks(stop)
            }
            else -> {
                val ms = stoppingMs()
                if (ms <= 0) stopSelf()
                else {
                    isStopping = true
                    handler.postDelayed(stop, ms)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun start() {
        running = true
        onLiveStart()
        widget.updateAll(this)
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        registerSensors()
        lastReadingAt = SystemClock.uptimeMillis()
        lastFrameAt = System.nanoTime()
        scheduleFrame()
        handler.postDelayed(sensorWatchdog, SENSOR_STALL_MS)
        handler.postDelayed(frameWatchdog, FRAME_CHECK_MS)
    }

    override fun onDestroy() {
        if (running) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            handler.removeCallbacksAndMessages(null)
            sensorManager.unregisterListener(this)
            unregisterReceiver(screenOff)
            onLiveStop()
        }
        running = false
        widget.updateAll(this)
        super.onDestroy()
    }

    private fun startInForeground() {
        createChannel(this, CHANNEL, getString(R.string.w_channel))
        val notification = ongoingNotification(this, CHANNEL, notificationIcon)
            .setContentTitle(getString(notificationTitle))
            .setContentText(getString(notificationText))
            .setContentIntent(widget.openFeatureIntent(this))
            .addAction(0, getString(R.string.w_turn_off), toggleIntent(this, javaClass, foreground = false))
            .setSilent(true)
            .build()
        val baseType = SPECIAL_USE_SERVICE
        val extraTypes = extraServiceTypes()
        hasExtraServiceTypes = extraTypes != 0 && try {
            ServiceCompat.startForeground(this, notificationId, notification, baseType or extraTypes)
            true
        } catch (e: SecurityException) {
            // E.g. location while Android counts the app as in the background: live without it
            Log.w(TAG, "Not allowed to run with the extra service types, running without them", e)
            false
        }
        if (!hasExtraServiceTypes) ServiceCompat.startForeground(this, notificationId, notification, baseType)
    }

    companion object {
        private const val ACTION_TOGGLE = "org.lukemech.lmtoolbox.WIDGET_TOGGLE"
        private const val ACTION_REFRESH = "org.lukemech.lmtoolbox.WIDGET_REFRESH"
        private const val CHANNEL = "widgets"
        private const val TAG = "LiveWidgetService"
        private const val SENSOR_STALL_MS = 1000L
        private const val FRAME_STALL_NS = 100_000_000L
        private const val FRAME_CHECK_MS = 100L
        /** Frames a second drawn on the timer, as many as a 60 Hz display shows. */
        private const val FALLBACK_FPS = 60

        /**
         * Lets a running [service] know something it depends on changed (e.g. a permission was
         * granted in the app, which is in front then, so the service may take it up).
         */
        fun refresh(context: Context, service: Class<out LiveWidgetService>) {
            runCatching { context.startService(Intent(context, service).setAction(ACTION_REFRESH)) }
                .onFailure { Log.w(TAG, "Could not refresh ${service.simpleName}", it) }
        }

        /**
         * Switches [service] on, or off while it's on. [foreground] (tapping the widget) may start
         * it; the notification's action only reaches it while it runs.
         */
        fun toggleIntent(context: Context, service: Class<out LiveWidgetService>, foreground: Boolean = true): PendingIntent {
            val intent = Intent(context, service).setAction(ACTION_TOGGLE)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return if (foreground) PendingIntent.getForegroundService(context, 0, intent, flags)
            else PendingIntent.getService(context, 0, intent, flags)
        }
    }
}
