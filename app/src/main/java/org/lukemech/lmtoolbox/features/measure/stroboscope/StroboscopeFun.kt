package org.lukemech.lmtoolbox.features.measure.stroboscope

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.runtime.Composable
import java.util.concurrent.locks.LockSupport
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.measure.stroboscope.StroboscopePage

/** Stroboscope and tachometer: the torch flashing at a rate, in Hz or RPM. */
object StroboscopeFeature : AppFeature {

    override val route = "stroboscope"
    override val titleStringResId: Int = R.string.fp_stroboscope
    override val keywordsResId: Int = R.string.fk_stroboscope
    override val descriptionResId: Int = R.string.fd_stroboscope
    override val category = FeatureCategory.Measure
    override val icon @Composable get() = Icons.Default.FlashOn

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        StroboscopePage()
    }

    /** The camera with a flash unit (the back one), whose torch flashes; null without any. */
    fun torchCamera(context: Context): String? {
        val cameras = context.getSystemService(CameraManager::class.java) ?: return null
        return runCatching {
            cameras.cameraIdList.firstOrNull { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull()
    }

    /**
     * The torch flashing on a thread of its own (a timer's precision; the main thread's would wait for frames)
     * from [start] to [stop], at [hz] (read every flash, so a change is seen at once). Stopping always leaves
     * it off. [onError] (main thread) when the torch can't be switched, e.g. another app has the camera: it's
     * stopped then.
     */
    class Strobe(context: Context, private val cameraId: String, private val onError: (Exception) -> Unit) {
        private val cameras = context.getSystemService(CameraManager::class.java)
        private val main = Handler(Looper.getMainLooper())
        private var thread: Thread? = null

        @Volatile var hz = 5.0

        @Volatile private var running = false

        /** How fast it really flashed over the last flashes (Hz); null until it has. */
        @Volatile var measuredHz: Double? = null
            private set

        fun start() {
            if (running) return
            running = true
            measuredHz = null
            thread = Thread(::flash, "Stroboscope").apply { start() }
        }

        fun stop() {
            if (!running) return
            running = false
            thread?.let {
                LockSupport.unpark(it)
                it.join(JOIN_MS)
            }
            thread = null
            // The thread turns it off as it ends; this in case it hadn't by now
            runCatching { cameras.setTorchMode(cameraId, false) }
        }

        private fun flash() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            val flashes = LongArray(MEASURED)
            var count = 0
            var next = System.nanoTime()
            try {
                while (running) {
                    val period = (1e9 / clampRate(hz)).toLong()
                    cameras.setTorchMode(cameraId, true)
                    val now = System.nanoTime()
                    flashes[count % MEASURED] = now
                    count++
                    if (count >= MEASURED) {
                        val oldest = flashes[count % MEASURED]
                        measuredHz = (MEASURED - 1) * 1e9 / (flashes[(count - 1) % MEASURED] - oldest)
                    }
                    sleepUntil(next + flashOnTime(period))
                    cameras.setTorchMode(cameraId, false)
                    next += period
                    // Fallen behind (the torch slower than the rate): on from now, not a burst to catch up
                    if (System.nanoTime() > next) next = System.nanoTime()
                    sleepUntil(next)
                }
            } catch (e: Exception) {
                Log.w(TAG, "The torch can't be switched", e)
                running = false
                main.post { onError(e) }
            } finally {
                runCatching { cameras.setTorchMode(cameraId, false) }
            }
        }

        private fun sleepUntil(time: Long) {
            while (running) {
                val left = time - System.nanoTime()
                if (left <= 0) return
                LockSupport.parkNanos(left)
            }
        }

        private companion object {
            const val TAG = "Stroboscope"
            /** Flashes the measured rate is taken over. */
            const val MEASURED = 10
            const val JOIN_MS = 500L
        }
    }
}
