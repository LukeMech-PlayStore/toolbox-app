package org.lukemech.lmtoolbox.ui.helpers

import org.lukemech.lmtoolbox.ui.components.Reveal
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.ui.components.PermissionDialog

private const val PREF_NOTIFICATIONS_REQUESTED = "notifications_requested"
internal const val PREF_NOTIFICATIONS_LATER = "notifications_later"

/** Whether the app may post notifications; re-checked whenever the app comes back to front. */
@Composable
fun rememberNotificationsEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(notificationsEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = notificationsEnabled(context)
        onPauseOrDispose {}
    }
    return enabled
}

private fun notificationsEnabled(context: Context) =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

/** Whether the user said "Not now" to notifications (see [NotificationPermissionBar]). */
@Composable
fun rememberNotificationsPostponed(notificationsEnabled: Boolean): MutableState<Boolean> {
    val context = LocalContext.current
    val postponed = remember { mutableStateOf(PreferenceHelper.get(context, PREF_NOTIFICATIONS_LATER, false)) }
    // Allowed since: an earlier "Not now" no longer counts, turning them off again shows the red bar
    LaunchedEffect(notificationsEnabled) {
        if (notificationsEnabled && postponed.value) {
            postponed.value = false
            PreferenceHelper.put(context, PREF_NOTIFICATIONS_LATER, false)
        }
    }
    return postponed
}

/**
 * Red bar at the top of the app while notifications are off; once the user said "Not now"
 * ([postponed]) in the app's own colors, and shown by the caller only in the settings. Tapping
 * it explains what they're used for and asks for them (or opens the system settings once
 * Android stops asking).
 */
@Composable
fun NotificationPermissionBar(visible: Boolean, postponed: MutableState<Boolean>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var later by postponed
    // The dialog covers the page: the keyboard goes away with it
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val color by animateColorAsState(
        if (later) MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp) else MaterialTheme.colorScheme.error
    )
    val contentColor by animateColorAsState(
        if (later) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onError
    )

    Reveal(visible = visible, modifier = modifier) {
        Surface(
            color = color,
            contentColor = contentColor,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    showDialog = true
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.NotificationsOff, null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.s_notifications_off),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
            }
        }
    }

    if (showDialog) {
        NotificationPermissionDialog(
            onDismiss = { showDialog = false },
            onLater = {
                showDialog = false
                later = true
                PreferenceHelper.put(context, PREF_NOTIFICATIONS_LATER, true)
            }
        )
    }
}

@Composable
private fun NotificationPermissionDialog(onDismiss: () -> Unit, onLater: () -> Unit) {
    val context = LocalContext.current
    PermissionDialog(
        icon = Icons.Default.NotificationsOff,
        title = stringResource(R.string.s_notifications_title),
        message = stringResource(R.string.s_notifications_desc),
        allowLabel = stringResource(R.string.s_notifications_allow),
        laterLabel = stringResource(R.string.s_notifications_later),
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray(),
        askedKey = PREF_NOTIFICATIONS_REQUESTED,
        settings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        // Before Android 13 there's none to ask for; granted, notifications were turned off in the settings
        askable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED,
        onDismiss = onDismiss,
        onLater = onLater
    )
}
