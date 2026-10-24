package org.lukemech.lmtoolbox.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.measure.ruler.RulerFeature

class LinuxTasksTest {
    private fun task(id: Long, type: LinuxTask.Type, state: LinuxTask.State, detached: Boolean = false) =
        LinuxTask(id, type, command = "cmd$id", state = state, detached = detached)

    @Test
    fun `nothing is shown when no task is active`() {
        val tasks = listOf(
            task(2, LinuxTask.Type.Command, LinuxTask.State.Done),
            task(1, LinuxTask.Type.Install, LinuxTask.State.Failed),
        )
        assertNull(LinuxTasks.current(tasks))
    }

    @Test
    fun `env change wins over commands`() {
        // Newest first, like LinuxTasks.tasks
        val tasks = listOf(
            task(3, LinuxTask.Type.Command, LinuxTask.State.Running),
            task(2, LinuxTask.Type.Update, LinuxTask.State.Running),
            task(1, LinuxTask.Type.Command, LinuxTask.State.Queued),
        )
        assertEquals(2L, LinuxTasks.current(tasks)?.id)
    }

    @Test
    fun `oldest active command is shown`() {
        val tasks = listOf(
            task(3, LinuxTask.Type.Command, LinuxTask.State.Queued),
            task(2, LinuxTask.Type.Command, LinuxTask.State.Running),
            task(1, LinuxTask.Type.Command, LinuxTask.State.Done),
        )
        assertEquals(2L, LinuxTasks.current(tasks)?.id)
    }

    @Test
    fun `only queued and running tasks are active`() {
        val active = LinuxTask.State.entries.filter { task(1, LinuxTask.Type.Command, it).isActive }
        assertEquals(listOf(LinuxTask.State.Queued, LinuxTask.State.Running), active)
    }

    @Test
    fun `only install and update change the environment`() {
        val envChanges = LinuxTask.Type.entries.filter { it.isEnvChange }
        assertEquals(listOf(LinuxTask.Type.Install, LinuxTask.Type.Update), envChanges)
    }

    @Test
    fun `bottom bar hides commands while their page is open`() {
        val tasks = listOf(task(1, LinuxTask.Type.Command, LinuxTask.State.Running))
        assertNull(LinuxTasks.barTask(tasks))
        // ...but the notification (app in background) still shows it
        assertEquals(1L, LinuxTasks.current(tasks)?.id)
    }

    @Test
    fun `bottom bar shows commands whose page was left`() {
        val tasks = listOf(
            task(2, LinuxTask.Type.Command, LinuxTask.State.Running),
            task(1, LinuxTask.Type.Command, LinuxTask.State.Running, detached = true),
        )
        assertEquals(1L, LinuxTasks.barTask(tasks)?.id)
    }

    @Test
    fun `bottom bar always shows installs and updates`() {
        val tasks = listOf(
            task(2, LinuxTask.Type.Command, LinuxTask.State.Running, detached = true),
            task(1, LinuxTask.Type.Install, LinuxTask.State.Running),
        )
        assertEquals(1L, LinuxTasks.barTask(tasks)?.id)
    }

    @Test
    fun `commands are shown short`() {
        // Script paths become file names
        assertEquals(
            "python3 update_rates.py True",
            displayCommand(listOf("python3", "/data/user/0/app/local/bin/features/currencyconv/update_rates.py", "True"))
        )
        // Long arguments are cut, multi-line ones flattened
        assertEquals("exiftool -Comment=line1 line2", displayCommand(listOf("exiftool", "-Comment=line1\nline2")))
        assertEquals("echo " + "x".repeat(31) + "…", displayCommand(listOf("echo", "x".repeat(50))))
        // Arguments with spaces aren't mistaken for paths
        assertEquals("qr -i /not a path", displayCommand(listOf("qr", "-i", "/not a path")))
    }

    @Test
    fun `exec result success and output`() {
        assertTrue(ExecResult(0, "out", "").isSuccess)
        assertFalse(ExecResult(1, "out", "err").isSuccess)
        assertEquals("out\nerr", ExecResult(1, "out", "err").output)
        assertEquals("err", ExecResult(1, "", "err").output)
        assertEquals("", ExecResult(0, " ", "").output)
    }

    @Test
    fun `a task is titled by its tool, the subsystem's own by its manager`() {
        assertEquals(RulerFeature.titleStringResId, LinuxTask(1, LinuxTask.Type.Command, RulerFeature).sourceTitle)
        assertEquals(R.string.s_env_manager, LinuxTask(2, LinuxTask.Type.Update).sourceTitle)
    }
}
