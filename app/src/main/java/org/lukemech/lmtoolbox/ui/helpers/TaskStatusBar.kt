package org.lukemech.lmtoolbox.ui.helpers

import org.lukemech.lmtoolbox.ui.components.REVEAL_MS
import org.lukemech.lmtoolbox.ui.components.revealExit
import org.lukemech.lmtoolbox.ui.components.revealEnter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.LinuxService
import org.lukemech.lmtoolbox.linux.LinuxTask
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.helpers.Screen
import org.lukemech.lmtoolbox.helpers.SettingsSubPage

/** The bars come and go as anything in the app does (see Reveal). */
private val barEnter = revealEnter()
private val barExit = revealExit()

/**
 * Shown above the bottom navigation while a Linux subsystem task runs in the background: the
 * unpacking or update of the subsystem itself (tapping opens its full-screen progress, [onOpenSetup]),
 * a tool's programs unpacking or commands whose page was left (tapping opens that tool's page, which
 * shows how it goes, its log and cancelling it). A failure (the subsystem couldn't be set up, or a
 * task failed) shows red above it, whatever runs meanwhile, until it's tapped (the task list, its log
 * open there; the subsystem's with a retry). Under it, what runs
 * as a service (a server, see LinuxTasks.runService): just that it runs, opening its tool, but on its
 * own page.
 */
@Composable
fun TaskStatusBar(onOpenSetup: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val navController = LocalAppNav.current.controller
    // What it opens covers the page: the keyboard goes away with it
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun open(action: () -> Unit) {
        focusManager.clearFocus()
        keyboard?.hide()
        action()
    }
    val backStack by navController.currentBackStackEntryAsState()
    val pageRoute = backStack?.arguments?.getString(Screen.Dynamic.ARG)
        ?.takeIf { backStack?.destination?.route == Screen.Dynamic.route }
    // A tool's own page shows what it runs: not in the bar too while it's open
    fun onItsPage(source: AppFeature?) = source != null && source.route == pageRoute
    // Its parts unpacking, though, only the bar shows the whole of (its page: each package's steps)
    fun shownOnPage(task: LinuxTask) = task.type == LinuxTask.Type.Command && onItsPage(task.source)
    val tasks by LinuxTasks.tasks.collectAsState()
    // Only installs/updates and commands whose page was left; page-bound work stays on its page
    val current = remember(tasks, pageRoute) { LinuxTasks.barTask(tasks.filterNot(::shownOnPage)) }
    val activeCount = tasks.count { it.isActive && (it.type.isEnvChange || it.detached) && !shownOnPage(it) }
    // Its last install/update failed (until one runs again: its own progress shows then)
    val setupFailed = remember(tasks) {
        tasks.none { it.type.isEnvChange && it.isActive } &&
            tasks.firstOrNull { it.type.isEnvChange }?.state == LinuxTask.State.Failed && !LinuxParts.isUpToDate(context)
    }
    // Else any other failure not looked at yet: what failed. Over whatever runs meanwhile, not hidden by it
    val failure = remember(tasks) { if (setupFailed) null else LinuxTasks.barFailure(tasks) }
    val error: String? = when {
        setupFailed -> stringResource(R.string.s_setup_failed)
        // The tool it was for (unpacking's title says just that)
        failure != null -> stringResource(
            R.string.s_tasks_failed_bar, if (failure.type == LinuxTask.Type.Extension) failure.label else failure.title(context)
        )
        else -> null
    }
    // Keeps showing the last one while the bar animates out
    val lastError = remember { mutableStateOf<Pair<String, ImageVector?>?>(null) }
    if (error != null) lastError.value = error to failure?.featureIcon

    AnimatedVisibility(visible = error != null, enter = barEnter, exit = barExit, modifier = modifier) {
        val (text, icon) = lastError.value ?: return@AnimatedVisibility
        Surface(
            color = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            modifier = Modifier.fillMaxWidth().clickable {
                open {
                    LinuxTasks.markFailuresSeen()
                    // The task list, where what failed shows its log (the subsystem's, a retry too)
                    navController.openSettingsPage(SettingsSubPage.Tasks)
                }
            }
        ) {
            // One failure after another: what it says changes smoothly
            Crossfade(text to icon, animationSpec = tween(REVEAL_MS), label = "error") { (text, icon) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(icon ?: ImageVector.vectorResource(R.drawable.linux_penguin), null, modifier = Modifier.size(18.dp))
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }

    // Keeps showing the last task while the bar animates out
    val lastShown = remember { mutableStateOf<LinuxTask?>(null) }
    if (current != null) lastShown.value = current

    AnimatedVisibility(visible = current != null, enter = barEnter, exit = barExit, modifier = modifier) {
        val task = lastShown.value ?: return@AnimatedVisibility
        Surface(
            // Like the surrounding app: only what it says and its progress stand out
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    open {
                        // The subsystem's own: its progress; else the page of the tool it's for, which shows how it goes
                        if (task.type.isEnvChange) onOpenSetup()
                        else if (!navController.openFeature(task.source)) navController.openSettingsPage(SettingsSubPage.Tasks)
                    }
                }
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        task.featureIcon ?: ImageVector.vectorResource(R.drawable.linux_penguin), null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        task.title(context),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(
                        buildString {
                            append(
                                when {
                                    task.state == LinuxTask.State.Queued -> stringResource(R.string.s_tasks_state_queued)
                                    task.progress != null -> "${task.progress.toInt()}%"
                                    else -> stringResource(R.string.s_tasks_state_running)
                                }
                            )
                            if (activeCount > 1) append(" (+${activeCount - 1})")
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                val progress = task.progress
                if (progress != null && task.state == LinuxTask.State.Running) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    // What runs as a service: that it does, no progress (it has none: it runs until it's stopped)
    val services by LinuxTasks.services.collectAsState()
    // Not on its tool's own page, which shows it; one that's part of another (a server's tunnel) as that one, counted once
    val shown = services.filterNot { onItsPage(it.source) }
        .map { if (it.group != null) it.copy(label = it.group, group = null) else it }
        .distinctBy { it.source to it.label }
    val lastService = remember { mutableStateOf<Pair<LinuxService, Int>?>(null) }
    shown.firstOrNull()?.let { lastService.value = it to shown.size }

    // Under a task's bar too: a job going on (e.g. an encoding) doesn't hide that a server runs
    AnimatedVisibility(visible = shown.isNotEmpty(), enter = barEnter, exit = barExit, modifier = modifier) {
        val (service, count) = lastService.value ?: return@AnimatedVisibility
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().clickable { open { navController.openFeature(service.source) } }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(service.featureIcon, null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(service.source.titleStringResId) + " · " + service.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(
                    stringResource(R.string.s_service_running) + if (count > 1) " (+${count - 1})" else "",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
