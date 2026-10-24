package org.lukemech.lmtoolbox.ui.pages.settings

import org.junit.Test
import org.lukemech.lmtoolbox.MainActivity
import org.lukemech.lmtoolbox.e2e.E2eTest
import org.lukemech.lmtoolbox.e2e.Explorer
import org.lukemech.lmtoolbox.helpers.SettingsSubPage

/** The task list, as a task's notification opens it (wherever the app was), and what's on it. */
class TasksTest : E2eTest() {
    @Test
    fun notificationOpensTheTasks() {
        ui.home()
        ui.compose.runOnUiThread { ui.compose.activity.startActivity(MainActivity.openTasksIntent(context)) }
        ui.waitPage(SettingsSubPage.Tasks.route)
        Explorer(ui, "settings-tasks", maxActions = 30, depth = 1).explore()
    }
}
