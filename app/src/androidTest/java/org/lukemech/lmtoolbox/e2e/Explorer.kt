package org.lukemech.lmtoolbox.e2e

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.Screen
import org.lukemech.lmtoolbox.linux.LinuxParts

/**
 * Goes through everything a page offers, as a curious user would: taps every button, switch, chip and choice,
 * types into every field, moves every slider; a dialog it opened is looked at, then closed (Cancel, else back);
 * a page it led to is gone through as well ([depth] pages down), then left. What it did is logged and each page
 * it went through is screenshot (named after [name]). What deletes, clears, removes or resets is left alone, in the
 * device's language (the app's own strings for it, see [destructive]), as are the bottom bar's tabs and [extraSkips].
 *
 * It fails the test when the app crashes (the run reports it), when it ends up on another page it can't
 * come back from, or when [check] (after each action) says what's wrong.
 */
class Explorer(
    private val ui: Ui,
    private val name: String,
    private val maxActions: Int = 120,
    private val depth: Int = 1,
    extraSkips: List<String> = emptyList(),
    private val check: () -> String? = { null },
) {
    // As words: "Start" skips the server's Start, not a switch that "starts with the server" (letters of any language)
    private val skips: List<Regex> = (DEFAULT_SKIPS + extraSkips).map { Regex("(?<![\\p{L}\\p{N}])${Regex.escape(it.lowercase())}(?![\\p{L}\\p{N}])") }
    private val destructive = destructive(ui)
    private val navLabels = setOf(ui.str(R.string.nav_main_page), ui.str(R.string.nav_settings))
    private val cancel = ui.str(android.R.string.cancel)
    private var actions = 0
    private var shot = 0

    /** Goes through the page shown now. */
    fun explore() = explorePage(ui.page, depth, mutableSetOf())

    private fun explorePage(route: String?, depthLeft: Int, seenPages: MutableSet<String?>) {
        seenPages += route
        screenshot("page-$route")
        val visited = mutableSetOf<String>()
        while (actions < maxActions) {
            val node = nextNode(visited) ?: break
            visited += key(node)
            actions++
            act(node)
            check()?.let { throw AssertionError("$name: after ${describe(node)}: $it") }
            if (ui.dialogShown) {
                screenshot("dialog")
                closeDialogs()
            }
            val now = ui.page
            if (now != route) {
                // A page of its own (a subpage, the tool's settings): through it as well, then back
                if (depthLeft > 0 && now !in seenPages) {
                    ui.log("$name: on to $now")
                    explorePage(now, depthLeft - 1, seenPages)
                }
                comeBack(route)
            }
        }
        ui.log("$name: $route done after ${visited.size} of its elements ($actions actions)")
    }

    /** The next element not gone through yet: what can be tapped, typed into or slid, top to bottom. */
    private fun nextNode(visited: Set<String>): SemanticsNode? {
        val actionable = SemanticsMatcher("actionable") {
            it.config.contains(SemanticsActions.OnClick) || it.config.contains(SemanticsActions.SetText) ||
                it.config.contains(SemanticsActions.SetProgress)
        } and isEnabled()
        return ui.nodes(actionable)
            .filterNot { isSkipped(it) }
            .sortedBy { it.positionInRoot.y }
            .firstOrNull { key(it) !in visited }
    }

    private fun act(node: SemanticsNode) {
        ui.log("$name: ${describe(node)}")
        val target = ui.compose.onNode(Ui.withId(node.id))
        runCatching {
            runCatching { target.performScrollTo() }
            val config = node.config
            when {
                config.contains(SemanticsActions.SetText) -> {
                    target.performTextReplacement(sampleText(node))
                    ui.hideKeyboard()
                }
                config.contains(SemanticsActions.SetProgress) -> {
                    val range = config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range ?: 0f..1f
                    val value = range.start + (range.endInclusive - range.start) * 0.6f
                    target.performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
                }
                else -> target.performClick()
            }
            ui.compose.waitForIdle()
        }.onFailure { ui.log("$name: couldn't act on ${describe(node)}: $it") }
    }

    /** What's typed into a field: a number for most (the calculators), a word where numbers don't fit. */
    private fun sampleText(node: SemanticsNode): String {
        val label = texts(node).joinToString(" ").lowercase()
        return when {
            // Something to download (the media downloader): a short video that's been up for years
            listOf("url", "link", "http").any { it in label } -> SAMPLE_URL
            listOf("search", "name", "text", "command", "title", "motd").any { it in label } -> "Test 1"
            else -> "10"
        }
    }

    private fun closeDialogs() {
        repeat(3) {
            if (!ui.dialogShown) return
            val dismiss = hasText(cancel, ignoreCase = true) and hasClickAction() and Ui.hasAnyAncestorDialog()
            if (ui.exists(dismiss)) runCatching { ui.compose.onAllNodes(dismiss)[0].performClick() }
            else ui.back()
            ui.compose.waitForIdle()
        }
        if (ui.dialogShown) throw AssertionError("$name: a dialog doesn't close (Cancel, back)")
    }

    /**
     * Back to [route]: the way back from a page opened over it; gone back past it instead (its own arrow back), or
     * on the home page (where back closes the app), it's opened again.
     */
    private fun comeBack(route: String?) {
        repeat(3) {
            if (ui.page == route) return
            if (ui.page == Screen.Main.route) return@repeat
            ui.back()
        }
        if (ui.page != route && route != null) ui.goTo(route)
        if (ui.page != route) throw AssertionError("$name: couldn't come back to $route from ${ui.page}")
    }

    private fun isSkipped(node: SemanticsNode): Boolean {
        val words = texts(node) + node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        if (words.any { it in navLabels }) return true
        // The bottom bar's tabs: icons alone (their names for screen readers), the middle one the tool's
        if (node.config.getOrNull(SemanticsProperties.Role) == Role.Tab && texts(node).isEmpty()) return true
        if (words.any { it.lowercase() in destructive }) return true
        val all = words.joinToString(" ").lowercase()
        return all == ui.str(R.string.back).lowercase() || skips.any { it.containsMatchIn(all) }
    }

    private fun texts(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.config.getOrNull(SemanticsProperties.EditableText)?.let { listOf(it.text) }.orEmpty()

    /** An element by what it says and is, not where: the same one after the page changed around it. */
    private fun key(node: SemanticsNode): String {
        val config = node.config
        val role = config.getOrNull(SemanticsProperties.Role)
        val text = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("|") { it.text }
        val description = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString("|")
        val kind = when {
            config.contains(SemanticsActions.SetText) -> "field"
            config.contains(SemanticsActions.SetProgress) -> "slider"
            else -> "tap"
        }
        val label = text.ifEmpty { description }.ifEmpty { config.getOrNull(SemanticsProperties.TestTag).orEmpty() }
        // Unnamed (an icon without a description): by where it is
        return "$kind:$role:$label" + if (label.isEmpty()) ":${node.positionInRoot.x.toInt() / 40},${node.positionInRoot.y.toInt() / 40}" else ""
    }

    private fun describe(node: SemanticsNode) = key(node).take(120)

    private fun screenshot(what: String) = Device.screenshot("$name-${shot++}-$what")

    companion object {
        /** Not tapped, besides [destructive]: what deletes, clears or resets (the tests' own data and installs) in English. */
        val DEFAULT_SKIPS = listOf("delete", "remove", "clear", "uninstall", "reset", "discard", "erase")

        private val DESTRUCTIVE_NAME = Regex("delete|clear|remove|reset|discard|uninstall|erase")

        /**
         * What the app's buttons for deleting, clearing, removing or resetting say, in the device's language: its
         * strings named so (s_feature_delete, recent_clear...), the app's and its Play modules' (their R classes),
         * lower case.
         */
        fun destructive(ui: Ui): Set<String> {
            val modules = LinuxParts.parts(ui.context).keys.map { $$"org.lukemech.lmtoolbox.features.$$it.R$string" }
            return (listOf($$"org.lukemech.lmtoolbox.R$string") + modules).asSequence()
                .mapNotNull { runCatching { Class.forName(it) }.getOrNull() }
                .flatMap { it.fields.toList() }
                .filter { DESTRUCTIVE_NAME.containsMatchIn(it.name) }
                .mapNotNull { runCatching { ui.context.getString(it.getInt(null)).lowercase() }.getOrNull() }
                .toSet()
        }

        const val SAMPLE_URL = "https://www.w3schools.com/html/mov_bbb.mp4"
    }
}
