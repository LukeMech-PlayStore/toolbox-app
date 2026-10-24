package org.lukemech.lmtoolbox.e2e

import java.io.File
import org.junit.Before
import org.junit.Test
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.LinuxTasks

/**
 * A tool of the app's own (the measuring tools, the calculators, the image resizer...): opened from the home
 * page and gone through, every option tried ([Explorer]). Its test is named after it, next to where its code is
 * (features/<category>/<route>/), and adds what's particular to it.
 */
abstract class ToolTest(val feature: AppFeature) : E2eTest() {
    @Before
    fun files() {
        // What a picker returns (the image resizer's pictures, any file)
        Pickers.pick(context, Pickers.samplePicture(context), "image/")
    }

    @Test
    fun throughAndThrough() {
        ui.openTool(feature)
        Device.screenshot("${feature.route}-opened")
        Explorer(ui, feature.route, depth = 1).explore()
    }
}

/**
 * A tool on the Linux subsystem: installed from its page as from Play (its module by bundletool's local testing,
 * its packages unpacked), then gone through with what it works on ([files]). No task may fail meanwhile.
 */
abstract class LinuxToolTest(val feature: AppFeature) : E2eTest() {
    protected val linux by lazy { Linux(ui) }

    @Before
    fun pictures() {
        Pickers.pick(context, Pickers.samplePicture(context, "photo.jpg"), "image/")
    }

    /**
     * Its own part removed first (as deleting its data does), so its installation page is gone through every
     * time, whichever tool ran before; what another tool still uses (e.g. python) stays, as the app keeps it.
     */
    @Before
    fun removeItsPart() {
        if (!LinuxParts.isAvailable(context, feature) || !linux.isInstalled(feature)) return
        val removals = LinuxTasks.extensionsRemoved.value
        LinuxTasks.removeExtension(context, feature)
        Device.waitFor("${feature.route}'s part removed", 2 * Device.MINUTE) { LinuxTasks.extensionsRemoved.value > removals }
    }

    /** What it's given to work on once it's installed (e.g. a video made by the subsystem's ffmpeg). */
    open fun files(): List<Pair<String, File>> = emptyList()

    @Test
    fun installedAndThroughAndThrough() {
        linux.assumeAvailable(feature)
        ui.openTool(feature)
        Device.screenshot("${feature.route}-gate")
        linux.install(feature)
        Device.screenshot("${feature.route}-installed")
        files().forEach { (mime, file) -> Pickers.pick(context, file, mime) }
        Explorer(ui, feature.route, depth = 1, check = linux::failure).explore()
        // What it started in the background, to its end
        Device.waitFor("${feature.route}'s tasks to end", 10 * Device.MINUTE, failFast = linux::failure) {
            LinuxTasks.tasks.value.none { it.isActive }
        }
        linux.failure()?.let { throw AssertionError(it) }
    }
}
