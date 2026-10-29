package org.lukemech.lmtoolbox.ui.helpers

import org.lukemech.lmtoolbox.ui.components.REVEAL_MS
import org.lukemech.lmtoolbox.ui.components.revealExit
import org.lukemech.lmtoolbox.ui.components.revealEnter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.tasks.ToolService
import org.lukemech.lmtoolbox.tasks.AppTask
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.helpers.Screen
import org.lukemech.lmtoolbox.helpers.SettingsSubPage
import org.lukemech.lmtoolbox.tasks.AppTasks
import org.lukemech.lmtoolbox.ui.components.StepBar

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
    // Its parts unpacking, though, only the bar shows the whole of (its page: each package's steps). One shown on a part of
    // its page alone (a tab) is in the bar as another part is looked at: its page says when (AppTasks.setShown)
    fun shownOnPage(task: AppTask) = task.type == AppTask.Type.Command && onItsPage(task.source) && task.page == null
    val tasks by AppTasks.tasks.collectAsState()
    // Only installs/updates and commands whose page was left; page-bound work stays on its page
    val current = remember(tasks, pageRoute) { AppTasks.barTask(tasks.filterNot(::shownOnPage)) }
    val activeCount = tasks.count { it.isActive && (it.type.isEnvChange || it.detached) && !shownOnPage(it) }
    // Its last install/update failed (until one runs again: its own progress shows then)
    val setupFailed = remember(tasks) {
        tasks.none { it.type.isEnvChange && it.isActive } &&
            tasks.firstOrNull { it.type.isEnvChange }?.state == AppTask.State.Failed && !LinuxParts.isUpToDate(context)
    }
    // Else any other failure not looked at yet: what failed. Over whatever runs meanwhile, not hidden by it
    val failure = remember(tasks) { if (setupFailed) null else AppTasks.barFailure(tasks) }
    val error: String? = when {
        setupFailed -> stringResource(R.string.s_setup_failed)
        // The tool it was for (unpacking's title says just that)
        failure != null -> stringResource(
            R.string.s_tasks_failed, if (failure.type == AppTask.Type.Extension) failure.label else failure.title(context)
        )
        else -> null
    }
    // Keeps showing the last one while the bar animates out
    val lastError = remember { mutableStateOf<String?>(null) }
    if (error != null) lastError.value = error

    // As a task's bar (StepBar), but failed: in red, a ✕ where the percentage goes
    AnimatedVisibility(visible = error != null, enter = barEnter, exit = barExit, modifier = modifier) {
        val text = lastError.value ?: return@AnimatedVisibility
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth().clickable {
                open {
                    AppTasks.markFailuresSeen()
                    // The task list, where what failed shows its log (the subsystem's, a retry too)
                    navController.openSettingsPage(SettingsSubPage.Tasks)
                }
            }
        ) {
            // One failure after another: what it says changes smoothly
            Crossfade(text, animationSpec = tween(REVEAL_MS), label = "error") { text ->
                StepBar(count = 0, current = 0, loadingNext = false, caption = text, detail = stringResource(R.string.s_tasks_failed_open), failed = true)
            }
        }
    }

    // Keeps showing the last task while the bar animates out
    val lastShown = remember { mutableStateOf<AppTask?>(null) }
    if (current != null) lastShown.value = current

    // As the page's own steps show a task (StepBar): how far as a pill, the tool, what it's doing, and stopping it
    AnimatedVisibility(visible = current != null, enter = barEnter, exit = barExit, modifier = modifier) {
        val task = lastShown.value ?: return@AnimatedVisibility
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    open {
                        // The subsystem's own: its progress; else the page of the tool it's for (at the part showing it)
                        if (task.type.isEnvChange) onOpenSetup()
                        else if (!navController.openFeature(task.source, task.page)) navController.openSettingsPage(SettingsSubPage.Tasks)
                    }
                }
        ) {
            val queued = task.state == AppTask.State.Queued
            // A tool's parts installing: downloaded first (if they need to be), then unpacked, a step each
            val steps = if (task.type == AppTask.Type.Extension && task.downloads.isNotEmpty()) 2 else 1
            val step = if (steps == 2 && task.stage != R.string.s_tasks_downloading) 1 else 0
            StepBar(
                count = steps, current = step, loadingNext = false, working = true,
                progress = task.progress?.takeIf { !queued }?.div(100f),
                caption = task.title(context),
                detail = listOfNotNull(
                    if (queued) stringResource(R.string.s_tasks_state_queued) else task.detail?.ifBlank { null },
                    "+${activeCount - 1}".takeIf { activeCount > 1 }
                ).joinToString(" · ").ifEmpty { null },
                // The subsystem's own isn't stopped halfway
                onCancel = if (task.type.isEnvChange) null else ({ AppTasks.cancel(task.id) })
            )
        }
    }

    // What runs as a service: that it does (it has no end of its own: no bar), in the same words as a task's
    val services by AppTasks.services.collectAsState()
    // Not on its tool's own page, which shows it; one that's part of another (a server's tunnel) as that one, counted once
    val shown = services.filterNot { onItsPage(it.source) }
        .map { if (it.group != null) it.copy(label = it.group, group = null) else it }
        .distinctBy { it.source to it.label }
    val lastService = remember { mutableStateOf<Pair<ToolService, Int>?>(null) }
    shown.firstOrNull()?.let { lastService.value = it to shown.size }

    // Under a task's bar too: a job going on (e.g. an encoding) doesn't hide that a server runs
    AnimatedVisibility(visible = shown.isNotEmpty(), enter = barEnter, exit = barExit, modifier = modifier) {
        val (service, count) = lastService.value ?: return@AnimatedVisibility
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth().clickable { open { navController.openFeature(service.source) } }
        ) {
            StepBar(
                count = 0, current = 0, loadingNext = false,
                caption = stringResource(service.source.titleStringResId),
                detail = listOfNotNull(service.label, stringResource(R.string.s_service_running), "+${count - 1}".takeIf { count > 1 })
                    .joinToString(" · ")
            )
        }
    }
}
