package org.lukemech.lmtoolbox.e2e

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** The device under test: screenshots, waiting. */
object Device {
    /** Where the screenshots go, pulled by the workflow: the app's external files (instrumentation runs in its process). */
    val outputDir: File
        get() = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "e2e").apply { mkdirs() }

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
     */
    fun waitFor(what: String, timeoutMs: Long, intervalMs: Long = 1000, failFast: () -> String? = { null }, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            if (condition()) return
            failFast()?.let { throw AssertionError("$what: $it") }
            if (SystemClock.uptimeMillis() > end) throw AssertionError("Timed out after ${timeoutMs / 1000} s waiting for $what")
            Thread.sleep(intervalMs)
        }
    }

    const val MINUTE = 60_000L
}
