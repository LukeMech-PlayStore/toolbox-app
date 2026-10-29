package org.lukemech.lmtoolbox.helpers

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.net.toUri
import org.lukemech.lmtoolbox.R

/**
 * Puts [text] on the clipboard ([label]: what it is, for the system's own preview). [confirm] is toasted only
 * before Android 13, which tells of every copy itself; by default "Copied".
 */
// A setter: primaryClip is a val in Kotlin (its getter is nullable, the setter isn't)
@Suppress("UsePropertyAccessSyntax")
fun copyText(context: Context, text: String, label: String = text, confirm: String = context.getString(R.string.copied)) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, confirm, Toast.LENGTH_SHORT).show()
}

/** What's on the clipboard, as text (trimmed); null if nothing is, or nothing but blanks. */
fun pastedText(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }

/** Opens [url] in the app for it (the browser, Play); a toast if there's none, not a crash. */
fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_opens_link, Toast.LENGTH_SHORT).show()
    }
}
