package org.lukemech.lmtoolbox.features.measure.sound_meter

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoiseAware
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.ui.pages.features.measure.sound_meter.CalibrationSetting
import org.lukemech.lmtoolbox.ui.pages.features.measure.sound_meter.SoundMeterPage

/** Sound level meter: the microphone's level as approximate dB SPL. */
object SoundMeterFeature : AppFeature {

    override val route = "sound_meter"
    override val titleStringResId: Int = R.string.fp_sound_meter
    override val keywordsResId: Int = R.string.fk_sound_meter
    override val descriptionResId: Int = R.string.fd_sound_meter
    override val category = FeatureCategory.Measure
    override val icon @Composable get() = Icons.Default.NoiseAware

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        SoundMeterPage()
    }

    private const val S_CALIBRATION = "sound_meter_calibration"
    private const val S_A_WEIGHTING = "sound_meter_a_weighting"
    /** Whether the microphone was asked for once (after that, Android may stop showing its dialog). */
    const val S_MIC_ASKED = "sound_meter_mic_asked"

    override val preferenceKeys = listOf(S_CALIBRATION, S_A_WEIGHTING, S_MIC_ASKED)

    override val hasSettingsContent get() = true

    @Composable
    override fun SettingsContent() {
        CalibrationSetting()
    }

    /** What the user's microphone reads off by, in dB (added to every reading; 0 until calibrated). */
    fun calibration(context: Context) = PreferenceHelper.get(context, S_CALIBRATION, 0f).toDouble()

    fun setCalibration(context: Context, db: Double) = PreferenceHelper.put(context, S_CALIBRATION, db.toFloat())

    /** Whether the readings are A-weighted, dB(A) (on by default: what noise is usually given in). */
    fun aWeighted(context: Context) = PreferenceHelper.get(context, S_A_WEIGHTING, true)

    fun setAWeighted(context: Context, on: Boolean) = PreferenceHelper.put(context, S_A_WEIGHTING, on)

    fun hasMicPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Whether the microphone can be read as it hears (UNPROCESSED: no noise suppression, no gain control, a flat
     * response); else VOICE_RECOGNITION is, which Android keeps close to that (no gain control, at least).
     */
    fun hasUnprocessed(context: Context) =
        context.getSystemService(AudioManager::class.java)
            .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

    /**
     * A block of the microphone's samples read: its Fast [level] (dBFS) as the block ends, its samples' [meanSquare]
     * (for the Leq), whether any of them [clipped] (the sound beyond what the microphone takes), and the dB SPL
     * that full scale stands for on the source it came from ([fullScaleSpl]).
     */
    class Reading(val level: Double, val meanSquare: Double, val clipped: Boolean, val fullScaleSpl: Double)

    /**
     * Reads the microphone until canceled, every [BLOCK_MS] giving [onReading] the block's reading (on the main
     * thread), [aWeighted] or not. False at once if the microphone can't be had (no permission, another app's
     * using it); it never ends by itself otherwise.
     */
    @SuppressLint("MissingPermission") // checked first
    suspend fun record(context: Context, aWeighted: Boolean, onReading: (Reading) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (!hasMicPermission(context)) return@withContext false
        val unprocessed = hasUnprocessed(context)
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return@withContext false
        val recorder = try {
            AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, BLOCK * 2 * 4))
        } catch (e: Exception) {
            Log.w(TAG, "No microphone", e)
            return@withContext false
        }
        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) return@withContext false
            recorder.startRecording()
            // Another app holds the microphone
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) return@withContext false
            val fullScale = fullScaleSpl(unprocessed)
            val weighting = if (aWeighted) AWeighting(SAMPLE_RATE.toDouble()) else null
            val meter = LevelMeter(SAMPLE_RATE.toDouble())
            val buffer = ShortArray(BLOCK)
            var blocks = 0
            while (currentCoroutineContext().isActive) {
                var read = 0
                while (read < BLOCK) {
                    val n = recorder.read(buffer, read, BLOCK - read)
                    if (n < 0) return@withContext false
                    read += n
                }
                var clipped = false
                for (raw in buffer) {
                    if (raw == Short.MAX_VALUE || raw == Short.MIN_VALUE) clipped = true
                    val x = raw / FULL_SCALE
                    meter.add(weighting?.process(x) ?: x)
                }
                val meanSquare = meter.takeMeanSquare()
                // The Fast level and the filter settle first (the level starts from silence)
                if (++blocks <= SETTLING_BLOCKS) continue
                val reading = Reading(meter.level, meanSquare, clipped, fullScale)
                withContext(Dispatchers.Main) { onReading(reading) }
            }
            true
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            recorder.release()
        }
    }

    private const val TAG = "SoundMeter"

    /** Every device records at it. */
    const val SAMPLE_RATE = 44100

    /** How often a reading comes, ms: ten a second, as a meter's display. */
    const val BLOCK_MS = 100
    private const val BLOCK = SAMPLE_RATE * BLOCK_MS / 1000
    private const val SETTLING_BLOCKS = 3
}
