package org.lukemech.lmtoolbox.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat

/** Makes (or keeps as it is) notification channel [id], named [name]; quiet by default, as those of ongoing work are. */
fun createChannel(context: Context, id: String, name: String, importance: Int = NotificationManager.IMPORTANCE_LOW) =
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(id, name, importance))

/**
 * A notification of work going on (a foreground service's) in [channel], with [icon]: there until it's over, telling
 * of it once (not on each update), and shown at once (Android 12+ otherwise holds it back ~10s, so short work showed none).
 */
fun ongoingNotification(context: Context, channel: String, @DrawableRes icon: Int): NotificationCompat.Builder =
    NotificationCompat.Builder(context, channel)
        .setSmallIcon(icon)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

/** A notification's tap: [intent]'s activity (the app, at a page; see MainActivity's intents). */
fun openActivity(context: Context, intent: Intent, requestCode: Int = 0): PendingIntent =
    PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

/** The foreground service type of the app's services that keep a tool going (a server, a live widget): from Android 14. */
val SPECIAL_USE_SERVICE: Int
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
