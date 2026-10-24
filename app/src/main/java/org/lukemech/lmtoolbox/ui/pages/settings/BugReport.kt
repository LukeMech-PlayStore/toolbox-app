package org.lukemech.lmtoolbox.ui.pages.settings

import org.lukemech.lmtoolbox.ui.components.Terminal
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.lukemech.lmtoolbox.BuildConfig
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.ui.components.FeatureButton

private const val REPORT_EMAIL = "contact@lukemech.org"
/** Keeps the mail intent well below Binder transaction limits. */
private const val ATTACHMENT_LIMIT = 8 * 1024

@Composable
fun BugReportSection() {
    var showDialog by remember { mutableStateOf(false) }

    FeatureButton(
        title = stringResource(R.string.s_bug_report),
        icon = Icons.Default.BugReport,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        BugReportDialog(onDismiss = { showDialog = false })
    }
}

/**
 * Bug report dialog. [attachment] (e.g. a failed task's log) is added to the report together
 * with app, Linux subsystem and device details.
 */
@Composable
fun BugReportDialog(attachment: String? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var method by remember { mutableStateOf(ReportMethod.Email) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.s_bug_report)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.s_bug_report_choose_method),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                ReportMethod.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = option.available) { method = option }
                    ) {
                        RadioButton(
                            selected = method == option,
                            onClick = { method = option },
                            enabled = option.available
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(option.labelRes) + if (!option.available) " (🛠️)" else "",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (option.available) 1f else 0.4f)
                        )
                    }
                }
                if (attachment != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.s_bug_report_log_attached), style = MaterialTheme.typography.bodyMedium)
                    // As the task list shows logs
                    Terminal(attachment, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                sendEmailReport(context, attachment)
                onDismiss()
            }) {
                Text(stringResource(R.string.s_bug_report_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

private enum class ReportMethod(val labelRes: Int, val available: Boolean) {
    Email(R.string.s_bug_report_email, true),
    InApp(R.string.s_bug_report_app, false),
}

private fun sendEmailReport(context: Context, attachment: String?) {
    val body = buildString {
        append("Please describe the bug here:\n\n\n")
        append("---\n")
        append("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
        append("Linux subsystem: ${LinuxParts.unpackedVersion(context, LinuxParts.CORE) ?: "not installed"}\n")
        append("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        if (attachment != null) {
            append("\nLog:\n")
            append(if (attachment.length > ATTACHMENT_LIMIT) "…" + attachment.takeLast(ATTACHMENT_LIMIT) else attachment)
            append('\n')
        }
    }
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:".toUri()
        putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "Bug report - LMToolbox")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, REPORT_EMAIL, Toast.LENGTH_LONG).show()
    }
}
