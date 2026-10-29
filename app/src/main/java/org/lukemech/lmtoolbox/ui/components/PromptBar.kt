package org.lukemech.lmtoolbox.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ContactSupport
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.soundMuted

/**
 * A bar on top of a page telling of something to do (e.g. calibrate, allow location), in [color], folding away;
 * tapping it explains or fixes it ([onClick]). A row like any other: as far from what's under it as its page's
 * RowArrangement puts rows (no gap of its own), none left as it folds away.
 */
@Composable
fun PromptBar(visible: Boolean, title: String, color: Color, icon: ImageVector, icon2: ImageVector, onClick: () -> Unit) {
    Reveal(visible = visible) {
        FeatureButton(
            title = title,
            colors = RowColors.tinted(color),
            onClick = onClick,
            icon = icon,
            icon2 = icon2
        )
    }
}

/**
 * A measuring tool's sensors' (or other hardware's, e.g. a torch's) warning, on top of its page: a red bar while the
 * device lacks them ([hasSensors] false, [missingMessage]) or they aren't [calibrated] ([calibrationTitle],
 * [calibrationMessage], [calibrationIcon]; left out for what has nothing to calibrate), explained in a dialog when
 * it's tapped.
 */
@Composable
fun SensorWarning(
    hasSensors: Boolean,
    @StringRes missingMessage: Int,
    calibrated: Boolean = true,
    @StringRes calibrationTitle: Int? = null,
    @StringRes calibrationMessage: Int? = null,
    calibrationIcon: ImageVector = Icons.Default.SyncDisabled,
) {
    var explained by remember { mutableStateOf(false) }
    // Uncalibrated only where there's a calibration to tell of
    val calibrating = hasSensors && calibrationTitle != null && calibrationMessage != null
    val warning = !hasSensors || (calibrating && !calibrated)
    val title = stringResource(if (calibrating) calibrationTitle else R.string.f_missing_sensors_title)
    if (warning && explained) {
        AlertDialog(
            onDismissRequest = { explained = false },
            confirmButton = {
                Button(onClick = { explained = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { Text(title) },
            text = { Text(stringResource(if (calibrating) calibrationMessage else missingMessage)) }
        )
    }
    PromptBar(
        visible = warning,
        title = title,
        color = MaterialTheme.colorScheme.error,
        icon = if (calibrating) calibrationIcon else Icons.Default.SyncDisabled,
        icon2 = Icons.AutoMirrored.Default.ContactSupport,
        onClick = { explained = true }
    )
}

/**
 * A bar on top of a page that plays a [sound] (while it does) while the media volume is down to nothing, so it can't be
 * heard. Followed as the volume changes; tapped, it shows the volume's slider.
 */
@Composable
fun MutedWarning(sound: Boolean) {
    val context = LocalContext.current
    var muted by remember { mutableStateOf(soundMuted(context)) }
    LifecycleResumeEffect(Unit) {
        muted = soundMuted(context)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                muted = soundMuted(context)
            }
        }
        // The system's own broadcast: not exported still gets it
        ContextCompat.registerReceiver(context, receiver, IntentFilter(VOLUME_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        onPauseOrDispose { context.unregisterReceiver(receiver) }
    }
    PromptBar(
        visible = sound && muted,
        title = stringResource(R.string.muted_sound),
        color = MaterialTheme.colorScheme.error,
        icon = Icons.AutoMirrored.Default.VolumeOff,
        icon2 = Icons.Default.Tune,
        onClick = {
            context.getSystemService(AudioManager::class.java)?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        }
    )
}

/** Sent as any stream's volume changes (AudioManager's, hidden but long kept). */
private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
