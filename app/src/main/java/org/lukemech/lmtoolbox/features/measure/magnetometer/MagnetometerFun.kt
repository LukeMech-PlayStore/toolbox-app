package org.lukemech.lmtoolbox.features.measure.magnetometer

import android.content.Context
import android.hardware.Sensor
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.runtime.Composable
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.measure.common.hasSensor
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.measure.magnetometer.MagnetometerPage

/** Magnetic field: the magnetometer's strength and components, and a metal detector on it. */
object MagnetometerFeature : AppFeature {

    override val route = "magnetometer"
    override val titleStringResId: Int = R.string.fp_magnetometer
    override val keywordsResId: Int = R.string.fk_magnetometer
    override val descriptionResId: Int = R.string.fd_magnetometer
    override val category = FeatureCategory.Measure
    override val icon @Composable get() = Icons.Default.Sensors

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        MagnetometerPage()
    }

    /** Whether the device has a magnetometer (the compass's, which most phones have). */
    fun hasSensors(context: Context) = hasSensor(context, Sensor.TYPE_MAGNETIC_FIELD)

    /** How the detector tells of metal: not at all, by a tone rising in pitch, or by buzzes coming faster. */
    enum class Feedback { Off, Tone, Buzz }

    /**
     * Plays the detector's tone until cancelled: a sine at [frequency] (Hz, read as it plays; null: silent),
     * gliding to each new one and fading in and out, so it never clicks.
     */
    suspend fun playTone(frequency: () -> Double?) = withContext(Dispatchers.Default) {
        val minBuffer = AudioTrack.getMinBufferSize(TONE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(TONE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer, TONE_CHUNK * 2 * 2))
            .build()
        val chunk = ShortArray(TONE_CHUNK)
        var phase = 0.0
        var pitch = 0.0
        var volume = 0.0
        try {
            track.play()
            while (currentCoroutineContext().isActive) {
                val target = frequency()
                if (pitch == 0.0) pitch = target ?: 0.0
                for (i in chunk.indices) {
                    if (target != null) pitch += (target - pitch) * GLIDE
                    volume += ((if (target != null) TONE_VOLUME else 0.0) - volume) * GLIDE
                    phase = (phase + 2 * PI * pitch / TONE_RATE) % (2 * PI)
                    chunk[i] = (sin(phase) * volume * Short.MAX_VALUE).toInt().toShort()
                }
                // Blocks while the track's buffer is full: it keeps the pace
                track.write(chunk, 0, chunk.size)
            }
        } finally {
            track.stop()
            track.release()
        }
    }

    /** Buzzes until cancelled, [interval] ms apart (read before each; null: still for now). */
    suspend fun buzz(context: Context, interval: () -> Long?) {
        val vibrator = vibrator(context)?.takeIf { it.hasVibrator() } ?: return
        try {
            while (currentCoroutineContext().isActive) {
                val wait = interval()
                if (wait != null) vibrator.vibrate(VibrationEffect.createOneShot(BUZZ_MS, VibrationEffect.DEFAULT_AMPLITUDE))
                delay(wait ?: IDLE_MS)
            }
        } finally {
            vibrator.cancel()
        }
    }

    /** Whether the device can buzz at all (a tablet may not). */
    fun canBuzz(context: Context) = vibrator(context)?.hasVibrator() == true

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Vibrator::class.java)

    private const val TONE_RATE = 22050
    /** 20 ms of the tone at a time: a new pitch is heard that soon. */
    private const val TONE_CHUNK = TONE_RATE / 50
    private const val TONE_VOLUME = 0.4
    /** How far towards a new pitch or volume each sample goes: about 10 ms to get there. */
    private const val GLIDE = 0.005
    private const val BUZZ_MS = 20L
    private const val IDLE_MS = 100L
}
