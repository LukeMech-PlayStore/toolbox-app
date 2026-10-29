package org.lukemech.lmtoolbox.helpers

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * What a tool's sounds and buzzes go out as: media, as the user asked for them on its page. Not sonification
 * (the system's stream), which silent and vibrate modes mute, nor the ringer's.
 */
val SIGNAL_AUDIO: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/** The device's vibrator, null without one (a tablet may have none). */
fun vibrator(context: Context): Vibrator? =
    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else context.getSystemService(Vibrator::class.java))?.takeIf { it.hasVibrator() }

/** One buzz of [ms], as media (see [SIGNAL_AUDIO]): without it Android takes it for a touch's, which settings turn off. */
fun Vibrator.buzz(ms: Long) {
    val effect = VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
    else @Suppress("DEPRECATION") vibrate(effect, SIGNAL_AUDIO)
}

/** Whether the media volume is down to nothing (or muted): sounds played as [SIGNAL_AUDIO] aren't heard. */
fun soundMuted(context: Context): Boolean {
    val audio = context.getSystemService(AudioManager::class.java) ?: return false
    return audio.isStreamMute(AudioManager.STREAM_MUSIC) || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
}
