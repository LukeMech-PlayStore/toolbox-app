package org.lukemech.lmtoolbox.ui.pages.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.linux.LinuxTask
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.helpers.SettingsSubPage
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.helpers.LocalAppNav

/**
 * Status of the Linux subsystem, opening its task list. It isn't there until a tool needs it (its
 * core comes with the first one, see MainActivity), and is updated automatically; this shows that,
 * progress, or an error to retry (versions are in the package tables).
 */
@Composable
fun EnvManagerSection() {
    val context = LocalContext.current
    val navController = LocalAppNav.current.controller

    // State lives in LinuxTasks, so it survives leaving this page
    val tasks by LinuxTasks.tasks.collectAsState()
    val envTask = tasks.firstOrNull { it.type.isEnvChange }
    val runningTask = envTask?.takeIf { it.isActive }
    val failedTask = envTask?.takeIf { it.state == LinuxTask.State.Failed }

    val subtitle = when {
        runningTask != null -> {
            val prefix = stringResource(
                if (runningTask.type == LinuxTask.Type.Update) R.string.s_env_manager_updating
                else R.string.s_env_manager_unpacking
            )
            runningTask.progress?.let { "$prefix: ${it.toInt()}%" } ?: "$prefix..."
        }
        failedTask != null ->
            "${failedTask.message ?: stringResource(R.string.s_tasks_state_failed)}\n${stringResource(R.string.s_env_manager_retry)}"
        tasks.any { it.isActive } -> stringResource(R.string.s_tasks_running, tasks.count { it.isActive })
        // Versions are in the package tables
        else -> null
    }

    FeatureButton(
        title = stringResource(R.string.s_env_manager),
        subtitle = subtitle,
        icon = ImageVector.vectorResource(R.drawable.linux_penguin),
        icon2 = Icons.AutoMirrored.Default.KeyboardArrowRight,
        // Only when there's something to show, else it'd leave a gap before the arrow
        buttonContent = when {
            runningTask != null -> {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            failedTask != null -> {
                {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = stringResource(R.string.s_tasks_state_failed),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> null
        },
        onClick = {
            // Retry after a failure (or if it's somehow missing / outdated), then follow it in the tasks
            if (runningTask == null) LinuxTasks.ensureReady(context)
            navController.navigate(SettingsSubPage.Tasks.route)
        }
    )
}
