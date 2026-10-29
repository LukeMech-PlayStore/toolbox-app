package org.lukemech.lmtoolbox.tasks

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lukemech.lmtoolbox.helpers.AppFeature

/**
 * A tool's own work, one run at a time, as an app task ([AppTasks.launch]): it goes on as its page is left or the app
 * goes to the back (Stop, its page's or the notification's, stops it), in the bottom bar and the notification with how
 * far it is ([progress]); tapped, they open [source]'s page, at its part [page] (e.g. a tab) if it has one. Kept by the
 * tool (an object of its own, not a page), so its page finds it as it was.
 *
 * [running] from its start until it's over, stopped or not, and only once it's over, not as soon as it's canceled, so a
 * new one can't start while the last still lets go of what it held (a scan's sockets, a file).
 */
class TaskRun(private val source: AppFeature, private val page: String? = null) {
    private var task by mutableStateOf<Long?>(null)
    val running get() = task != null

    /** Runs [block] (on the main thread, as a page's own work is: its state is written as it comes), [label] its task's. */
    fun start(context: Context, label: String, block: suspend CoroutineScope.() -> Unit) {
        if (running) return
        AppTasks.launch(context, source, label, page, onStart = { task = it }) {
            val id = task
            try {
                withContext(Dispatchers.Main, block)
            } finally {
                if (task == id) task = null
            }
        }
    }

    fun stop() {
        task?.let(AppTasks::cancel)
    }

    /** How far it is (0-1; null: no telling), for the bottom bar and the notification. */
    fun progress(progress: Float?) {
        task?.let { AppTasks.setProgress(it, progress?.coerceIn(0f, 1f)?.times(100)) }
    }

    /** Whether it's in sight (its page, its part of it): out of the bottom bar then (see [ShownWhile]). */
    fun setShown(shown: Boolean) {
        task?.let { AppTasks.setShown(it, shown) }
    }
}

/** [run] in sight while [shown] (its part of the page is: a tab) and its page is: in the bottom bar else. */
@Composable
fun ShownWhile(run: TaskRun, shown: Boolean = true) {
    DisposableEffect(run, shown) {
        run.setShown(shown)
        onDispose { run.setShown(false) }
    }
}
