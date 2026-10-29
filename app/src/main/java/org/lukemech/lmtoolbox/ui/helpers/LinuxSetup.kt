package org.lukemech.lmtoolbox.ui.helpers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.LinuxInstaller
import org.lukemech.lmtoolbox.tasks.AppTask
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.helpers.SettingsSubPage
import org.lukemech.lmtoolbox.ui.components.SystemStats
import org.lukemech.lmtoolbox.tasks.AppTasks

/**
 * Updates what's unpacked of the Linux subsystem when the app ships a new image (and deletes what
 * nothing uses anymore), once the app shows; with nothing unpacked there's nothing to do (its core
 * comes with the first tool on Linux). The app stays usable meanwhile: the bottom bar shows the progress
 * (opening [LinuxSetupScreen]), and commands are refused until it's done (LinuxTasks.envNotReady).
 */
@Composable
fun LinuxSetupStarter() {
    val context = LocalContext.current
    // After the first frame, so the app shows first
    LaunchedEffect(Unit) {
        withFrameNanos { }
        LinuxTasks.startUp(context)
    }
}

/**
 * The unpacking or update full screen (opened from the status bar): its progress and what it takes.
 * Closes itself once it's done, or opens its log (the task list) if it failed.
 */
@Composable
fun LinuxSetupScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val tasks by AppTasks.tasks.collectAsState()
    val envTask = tasks.firstOrNull { it.type.isEnvChange }
    // Re-read from disk whenever the installation/update changes state
    val envTaskState = envTask?.let { it.id to it.state }
    val ready = remember(envTaskState) { LinuxParts.isUpToDate(context) }
    val failed = envTask != null && !envTask.isActive && !ready
    val navController = LocalAppNav.current.controller

    BackHandler(onBack = onClose)
    LaunchedEffect(ready) { if (ready) onClose() }
    LaunchedEffect(failed) {
        if (failed) {
            onClose()
            AppTasks.markFailuresSeen()
            navController.openSettingsPage(SettingsSubPage.Tasks)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
            IconButton(onClick = onClose, modifier = Modifier.padding(4.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.back))
            }
            SetupProgress(envTask?.takeIf { it.isActive })
        }
    }
}

@Composable
private fun SetupProgress(task: AppTask?) {
    val context = LocalContext.current
    val progress = task?.progress?.takeIf { task.state == AppTask.State.Running }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            ImageVector.vectorResource(R.drawable.linux_penguin), null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            task?.title(context) ?: stringResource(R.string.s_tasks_type_install),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (progress != null) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            when {
                progress != null -> "${progress.toInt()}%"
                task?.state == AppTask.State.Running -> stringResource(R.string.s_tasks_state_running)
                else -> stringResource(R.string.s_tasks_state_queued)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // What it takes, while it runs
        // From before it starts, so they're there when it does
        if (task == null || task.isActive) {
            Spacer(modifier = Modifier.height(24.dp))
            SystemStats(writtenBytes = { LinuxInstaller.unpackedBytes })
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.s_setup_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

