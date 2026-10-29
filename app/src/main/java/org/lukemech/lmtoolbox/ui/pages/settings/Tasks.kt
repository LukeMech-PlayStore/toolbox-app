package org.lukemech.lmtoolbox.ui.pages.settings

import org.lukemech.lmtoolbox.ui.components.Terminal
import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.tasks.AppTask
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.ui.components.ClearAction
import org.lukemech.lmtoolbox.ui.components.EmptyText
import org.lukemech.lmtoolbox.ui.components.Fade
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.Reveal
import java.text.DateFormat
import java.util.Date
import org.lukemech.lmtoolbox.ui.components.RowColors
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.helpers.copyText
import org.lukemech.lmtoolbox.tasks.AppTasks

@Composable
fun TasksPage() {
    val tasks by AppTasks.tasks.collectAsState()
    PageHeading(
        title = stringResource(R.string.s_env_manager),
        back = true,
        verticalArrangement = RowArrangement,
        // Fading in once there's something finished to clear
        action = {
            Fade(visible = tasks.any { !it.isActive }) {
                ClearAction { AppTasks.clearFinished() }
            }
        },
        // What's installed of it, on top while what it's been doing scrolls under it
        header = {
            InstalledPackagesButton()
            Spacer(modifier = Modifier.height(6.dp))
        }
    ) {
        TaskList()
    }
}

@Composable
fun TaskList() {
    val tasks by AppTasks.tasks.collectAsState()

    Reveal(visible = tasks.isEmpty()) { EmptyText(stringResource(R.string.s_tasks_empty)) }
    tasks.forEach { TaskItem(it) }
}

@Composable
private fun TaskItem(task: AppTask) {
    val context = LocalContext.current
    val failed = task.state == AppTask.State.Failed
    // Failures open with their log; other logs on tap
    var expanded by remember(task.id, failed) { mutableStateOf(failed) }
    var reporting by remember { mutableStateOf(false) }
    // Finished commands always open, even if they printed nothing
    val log = task.log ?: task.message ?: if (task.type == AppTask.Type.Command && !task.isActive) {
        stringResource(R.string.s_tasks_no_output)
    } else null

    FeatureButton(
        title = task.title(context),
        subtitle = taskStatus(task),
        icon = taskIcon(task),
        colors = if (failed) RowColors.alert else RowColors.default,
        onClick = { if (log != null) expanded = !expanded },
        buttonContent = {
            Fade(visible = task.isActive) {
                IconButton(onClick = { AppTasks.cancel(task.id) }) {
                    Icon(Icons.Default.Close, stringResource(R.string.s_tasks_cancel))
                }
            }
        },
        expanded = task.state == AppTask.State.Running || (expanded && log != null),
        popupContent = {
            // The log shown last, while it folds away
            val lastLog = remember { arrayOfNulls<String>(1) }
            if (log != null) lastLog[0] = log
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Reveal(visible = task.state == AppTask.State.Running) {
                    val progress = task.progress
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                Reveal(visible = expanded && log != null) {
                    val shown = lastLog[0] ?: return@Reveal
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // As a terminal shows it, after the command that ran
                        Terminal(shown, command = task.command.takeIf { task.type == AppTask.Type.Command && it.isNotBlank() })
                        Row(modifier = Modifier.align(Alignment.End)) {
                            TextButton(onClick = { copyLog(context, task, shown) }) {
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.s_tasks_copy), modifier = Modifier.padding(start = 6.dp))
                            }
                            // The subsystem's unpacking or update, while it's still not set up
                            if (failed && task.type.isEnvChange && !LinuxParts.isUpToDate(context)) {
                                TextButton(onClick = { LinuxTasks.ensureReady(context) }) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                                    Text(stringResource(R.string.s_setup_retry), modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            if (failed) {
                                TextButton(onClick = { reporting = true }) {
                                    Icon(Icons.Default.BugReport, null, modifier = Modifier.size(18.dp))
                                    Text(stringResource(R.string.s_tasks_report), modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    if (reporting) {
        BugReportDialog(attachment = reportText(context, task, log), onDismiss = { reporting = false })
    }
}

private fun reportText(context: Context, task: AppTask, log: String?) = buildString {
    appendLine("Tool: ${context.getString(task.sourceTitle)}")
    appendLine("Task: ${context.getString(task.type.titleRes)}")
    if (task.command.isNotEmpty()) appendLine("Command: ${task.command}")
    task.exitCode?.let { appendLine("Exit code: $it") }
    if (log != null) appendLine().append(log)
}

private fun copyLog(context: Context, task: AppTask, log: String) =
    copyText(context, reportText(context, task, log), task.title(context), context.getString(R.string.s_tasks_copied))

@Composable
private fun taskStatus(task: AppTask): String {
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(task.startedAt))
    val state = when (task.state) {
        AppTask.State.Queued -> stringResource(R.string.s_tasks_state_queued)
        AppTask.State.Running -> task.progress?.let { "${it.toInt()}%" } ?: stringResource(R.string.s_tasks_state_running)
        AppTask.State.Done -> stringResource(R.string.s_tasks_state_done)
        AppTask.State.Failed -> stringResource(R.string.s_tasks_state_failed) +
            (task.exitCode?.let { " (${stringResource(R.string.s_tasks_exit_code, it)})" } ?: "")
        AppTask.State.Cancelled -> stringResource(R.string.s_tasks_state_cancelled)
    }
    val duration = task.finishedAt?.let { " · ${"%.1f".format((it - task.startedAt) / 1000f)}s" } ?: ""
    // The title names the tool; commands show the command itself here
    val command = task.detail?.let { "$it\n" } ?: ""
    return "$command$time · $state$duration"
}

@Composable
private fun taskIcon(task: AppTask): ImageVector = when (task.state) {
    AppTask.State.Queued -> Icons.Default.HourglassEmpty
    AppTask.State.Running -> if (task.type == AppTask.Type.Command) Icons.Default.Terminal else Icons.Default.Sync
    // Done: the tool it was for, if any
    AppTask.State.Done -> task.featureIcon ?: Icons.Default.CheckCircle
    AppTask.State.Failed -> Icons.Default.Error
    AppTask.State.Cancelled -> Icons.Default.Cancel
}
