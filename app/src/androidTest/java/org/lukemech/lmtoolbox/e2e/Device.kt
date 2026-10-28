package org.lukemech.lmtoolbox.e2e

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.MainTestClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.lukemech.lmtoolbox.linux.LinuxTask
import org.lukemech.lmtoolbox.linux.LinuxTasks
import java.io.File

/** The device under test: screenshots, waiting, the system's dialogs. */
object Device {
    /** Where the screenshots go, pulled by the workflow: the app's external files (instrumentation runs in its process). */
    val outputDir: File
        get() = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "e2e").apply { mkdirs() }

    /**
     * The test's clock, which the app's pages' coroutines run on (the compose rule's: its effects, what a page
     * runs, e.g. a command): they go on only as it's moved, which [waitFor] does as it waits. Set by [E2eTest].
     */
    var clock: MainTestClock? = null

    /** The screen as it is, as [name].png. */
    fun screenshot(name: String) {
        runCatching {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
            val file = File(outputDir, "screenshots/${name.replace(Regex("""[^\w.\-]"""), "_")}.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { Log.w(E2eTest.TAG, "No screenshot $name", it) }
    }

    /**
     * Waits (polling, off the main thread: not for the UI to settle, a server takes minutes) until [condition]
     * holds; fails saying [what] after [timeoutMs]. [failFast]: a message when it can't come anymore (e.g. a crash).
     * Every half a minute it says what it's still waiting for, how far it is ([status]) and the Linux tasks running
     * (the job's log shows it).
     */
    fun waitFor(
        what: String, timeoutMs: Long, intervalMs: Long = 1000, failFast: () -> String? = { null }, status: () -> String? = { null },
        condition: () -> Boolean,
    ) {
        val start = SystemClock.uptimeMillis()
        val end = start + timeoutMs
        var told = start
        while (true) {
            if (condition()) return
            failFast()?.let { throw AssertionError("$what: $it") }
            val now = SystemClock.uptimeMillis()
            if (now > end) throw AssertionError("Timed out after ${timeoutMs / 1000} s waiting for $what")
            if (now - told >= 30_000) {
                told = now
                Log.i(E2eTest.TAG, "Waiting for $what (${(now - start) / 1000} s)${status()?.let { ": $it" }.orEmpty()}${running()}")
            }
            Thread.sleep(intervalMs)
            // The pages' coroutines as far on as real time went (a sleep alone would hold them where they are)
            clock?.let { c -> runCatching { c.advanceTimeBy(intervalMs) } }
        }
    }

    /**
     * The system's "isn't responding" for another app (the emulator's launcher, hung under the load) waited out, as a
     * user would: in front of the app, it takes every key (back never reached the app's dialogs) and tap.
     */
    fun dismissSystemDialogs() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        repeat(3) {
            val wait = device.findObject(By.res("android", "aerr_wait")) ?: return
            Log.w(E2eTest.TAG, "A system dialog in front of the app (another app not responding): waited")
            runCatching { wait.click() }
            device.waitForIdle()
        }
    }

    private fun running() = LinuxTasks.tasks.value.filter { it.state == LinuxTask.State.Running }
        .joinToString("") { "\n  ${it.type} ${it.label}" + (it.progress?.let { p -> " ${p.toInt()}%" } ?: "") }

    const val MINUTE = 60_000L
}
