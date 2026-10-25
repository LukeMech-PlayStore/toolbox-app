package org.lukemech.lmtoolbox.e2e

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import org.junit.Assume.assumeTrue
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureDelivery
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.LinuxTask
import org.lukemech.lmtoolbox.linux.LinuxTasks

/**
 * The tools on the Linux subsystem: their Play modules installed as from Play (bundletool's local testing,
 * see tests.yml) and their packages unpacked, through their installation page; and what failed meanwhile.
 */
class Linux(private val ui: Ui) {
    private val known = LinuxTasks.tasks.value.map { it.id }.toMutableSet()

    /** The tasks that failed since this was made (or since [acknowledge]), with why. */
    fun newFailures(): List<LinuxTask> = LinuxTasks.tasks.value.filter { it.state == LinuxTask.State.Failed && it.id !in known }

    fun acknowledge() {
        known += LinuxTasks.tasks.value.map { it.id }
    }

    fun describe(tasks: List<LinuxTask>) = tasks.joinToString("\n\n") { t ->
        "${t.title(ui.context)} (${t.type}): ${t.message ?: "failed"}\n${t.log?.takeLast(3000).orEmpty()}"
    }

    /** A message when a task failed since, for [Device.waitFor]'s failFast and [Explorer]'s check. */
    fun failure(): String? = newFailures().takeIf { it.isNotEmpty() }?.let { "A task failed:\n" + describe(it) }

    /** Skips the test where [feature] isn't built for this device's ABI (e.g. Java 25 on x86): its page says so. */
    fun assumeAvailable(feature: AppFeature) =
        assumeTrue("${feature.route} isn't built for this device's ABI", LinuxParts.isAvailable(ui.context, feature))

    /** Whether [feature]'s packages are there and its pages loaded. */
    fun isInstalled(feature: AppFeature) =
        LinuxParts.missingExtensions(ui.context, feature.linuxExtensions).isEmpty() &&
            (feature.uiClass == null || FeatureDelivery.ui(feature) != null)

    /**
     * On [feature]'s page: its installation page's button (downloading from Play, or just unpacking), what's
     * asked confirmed, until its own page shows. Nothing when it's installed already.
     */
    fun install(feature: AppFeature, timeoutMs: Long = 25 * Device.MINUTE) {
        if (isInstalled(feature)) return
        val install = (hasText(ui.str(R.string.s_ext_install)) or hasText(ui.str(R.string.s_ext_unpack))) and hasClickAction()
        ui.click(install, what = "${feature.route}'s install button")
        Device.screenshot("install-${feature.route}")
        Device.waitFor("${feature.route} installed", timeoutMs, intervalMs = 2000, failFast = ::failure) {
            confirmPrompt()
            isInstalled(feature)
        }
        ui.compose.waitForIdle()
    }

    /** Accepts the question asked before parts are installed (InstallPrompt), if it's asked. */
    fun confirmPrompt(): Boolean {
        val accept = hasText(ui.str(R.string.s_ext_install)) and hasClickAction() and Ui.hasAnyAncestorDialog()
        if (!ui.exists(accept)) return false
        ui.click(accept)
        return true
    }
}
