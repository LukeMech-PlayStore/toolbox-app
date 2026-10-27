package org.lukemech.lmtoolbox.e2e

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.lukemech.lmtoolbox.MainActivity
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.Screen
import org.lukemech.lmtoolbox.helpers.SettingsSubPage
import org.lukemech.lmtoolbox.ui.helpers.CurrentPage

/**
 * The app driven as a user would: by what's on screen (texts, the icons' descriptions), scrolling to what's
 * further down before tapping it. Where the app is ([page]) comes from its navigation (CurrentPage).
 */
class Ui(val compose: AndroidComposeTestRule<*, MainActivity>) {
    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun str(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    /** The route of the page shown, e.g. "feature/ohm". */
    val page: String? get() = CurrentPage.route

    fun nodes(matcher: SemanticsMatcher): List<SemanticsNode> =
        runCatching { compose.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false) }.getOrDefault(emptyList())

    fun exists(matcher: SemanticsMatcher) = nodes(matcher).isNotEmpty()

    /** Waits for [matcher] to show (a page loading, a list arriving), up to [timeoutMs]. */
    fun waitShown(matcher: SemanticsMatcher, timeoutMs: Long = 20_000, what: String = matcher.description) {
        runCatching { compose.waitUntil(timeoutMs) { exists(matcher) } }
            .onFailure { throw AssertionError("Not shown after ${timeoutMs / 1000} s: $what") }
    }

    /**
     * Brings the node to sight: scrolled to in its page, or in a lazy list (the choices of a setting, a long list)
     * until it's composed. True once its middle (where a tap lands) is on screen: not yet if the scroll failed, or
     * the page moved after it.
     */
    fun scrollTo(matcher: SemanticsMatcher): Boolean {
        if (!exists(matcher)) {
            val lazyLists = nodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            for (list in lazyLists) {
                val found = runCatching { compose.onNode(withId(list.id)).performScrollToNode(matcher) }.isSuccess
                if (found) break
            }
        }
        if (!exists(matcher)) return false
        // Fails where nothing scrolls (a dialog's buttons): then it's where it is
        runCatching { compose.onAllNodes(matcher)[0].performScrollTo() }
        compose.waitForIdle()
        return nodes(matcher).firstOrNull()?.let(::middleShown) == true
    }

    /** Whether [node]'s middle is inside what scrolls it (a page, a list: each shows a part of what it holds). */
    private fun middleShown(node: SemanticsNode): Boolean {
        val middle = node.boundsInRoot.center
        return generateSequence(node.parent) { it.parent }
            .filter { SemanticsActions.ScrollBy in it.config }
            .all { it.boundsInRoot.contains(middle) }
    }

    /**
     * Taps the first enabled node matching [matcher] (waiting for it, up to [timeoutMs]). With [done], what the
     * tap does: tapped again if it hasn't after a few seconds (a tap that missed, the page moving under it).
     */
    fun click(matcher: SemanticsMatcher, timeoutMs: Long = 20_000, what: String = matcher.description, done: (() -> Boolean)? = null) {
        val target = matcher and isEnabled()
        repeat(TAPS) { tap ->
            runCatching {
                compose.waitUntil(timeoutMs) { scrollTo(target) }
            }.onFailure { throw AssertionError("Nothing to tap after ${timeoutMs / 1000} s: $what") }
            // Gone between the two (recomposed): looked for again
            if (runCatching { compose.onAllNodes(target)[0].performClick() }.isFailure) return@repeat
            compose.waitForIdle()
            if (done == null) return
            if (runCatching { compose.waitUntil(TAP_TOOK_MS) { done() } }.isSuccess) return
            log("Tapped $what, nothing came of it: tapping again (${tap + 2}/$TAPS)")
        }
        throw AssertionError("Tapped $what $TAPS times, nothing came of it")
    }

    fun clickText(@StringRes id: Int, timeoutMs: Long = 20_000, done: (() -> Boolean)? = null) = clickText(str(id), timeoutMs, done)
    fun clickText(text: String, timeoutMs: Long = 20_000, done: (() -> Boolean)? = null) =
        click(hasText(text) and hasClickAction(), timeoutMs, "\"$text\"", done)

    /** Taps [text] in the dialog shown (its buttons). */
    fun clickInDialog(text: String, timeoutMs: Long = 20_000) =
        click(hasText(text) and hasClickAction() and hasAnyAncestorDialog(), timeoutMs, "\"$text\" in a dialog")

    val dialogShown get() = exists(isDialog())

    /**
     * The text field named [label]: its own label (or placeholder) says so, or [label] is the title just above it
     * (the calculators' fields: "Voltage" over the box, its unit beside).
     */
    fun field(label: String): SemanticsNode? {
        nodes(hasSetTextAction() and hasText(label, substring = true)).firstOrNull()?.let { return it }
        val title = nodes(hasText(label)).firstOrNull() ?: return null
        // Where they are on the page, not on the screen: boundsInRoot is clipped to what's shown, a field scrolled off it
        // all zeros, so any of them would be "under" a title scrolled off too
        val below = title.positionInRoot.y + title.size.height
        val right = title.positionInRoot.x + title.size.width
        return nodes(hasSetTextAction())
            .filter { it.positionInRoot.y >= below - 1 && it.positionInRoot.x <= right }
            .minByOrNull { it.positionInRoot.y }
    }

    /** Replaces the text of the field named [label] (see [field]) by [text]. */
    fun type(label: String, text: String) {
        runCatching { compose.waitUntil(20_000) { field(label) != null } }
            .onFailure { throw AssertionError("No field \"$label\"") }
        val target = compose.onNode(withId(field(label)!!.id))
        runCatching { target.performScrollTo() }
        target.performTextReplacement(text)
        hideKeyboard()
    }

    /**
     * The keyboard a field opened, closed (as a user would once they were done typing): left open, on a small screen it
     * covers what's next (a setting's choices under it, never shown).
     */
    fun hideKeyboard() {
        compose.runOnUiThread {
            val window = compose.activity.window
            WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
        runCatching { compose.waitUntil(5_000) { !keyboardShown() } }
        compose.waitForIdle()
    }

    private fun keyboardShown(): Boolean {
        var shown = false
        compose.runOnUiThread {
            shown = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return shown
    }

    /** What the field named [label] holds (see [field]). */
    fun fieldText(label: String): String =
        field(label)?.config?.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    // The bottom bar's tabs: their icons' descriptions

    fun home() = navTab(str(R.string.nav_main_page), Screen.Main.route)
    fun settings() = navTab(str(R.string.nav_settings), Screen.Settings.route)
    private fun navTab(label: String, route: String) =
        click(hasContentDescription(label) and hasClickAction(), what = "tab $label") { page == route }

    /** Opens [feature] as a user would: home, every tool, then its row. */
    fun openTool(feature: AppFeature) {
        home()
        waitPage(Screen.Main.route)
        // Tapped again if the tap missed (the page still coming in under it)
        val all = "category/${Screen.Category.ALL}"
        clickText(R.string.all_features) { page == all }
        waitPage(all)
        val tool = "feature/${feature.route}"
        clickText(str(feature.titleStringResId)) { page == tool }
        waitPage(tool)
    }

    fun waitPage(route: String, timeoutMs: Long = 20_000) {
        runCatching { compose.waitUntil(timeoutMs) { page == route } }
            .onFailure { throw AssertionError("Expected page $route, on ${page ?: "none"}") }
    }

    /**
     * The system's back button: to what's in front (a dialog, a menu, else the page). On the home page it'd close
     * the app, so it isn't pressed there.
     */
    fun back() {
        if (page == Screen.Main.route && !dialogShown) throw AssertionError("Back on the home page would close the app")
        Device.dismissSystemDialogs()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitForIdle()
    }

    /** Straight to the page [route] (CurrentPage's), however the app is: e.g. back to what a tap led away from. */
    fun goTo(route: String) {
        val activity = compose.activity
        fun open(intent: android.content.Intent) = compose.runOnUiThread { activity.startActivity(intent) }
        when {
            route == Screen.Main.route -> home()
            route == Screen.Settings.route -> settings()
            route == SettingsSubPage.Tasks.route -> open(MainActivity.openTasksIntent(context))
            route.startsWith("settings/features/") -> open(MainActivity.openFeatureIntent(context, route.substringAfterLast('/'), settings = true))
            route.startsWith("feature/") -> open(MainActivity.openFeatureIntent(context, route.substringAfter('/')))
            route.startsWith("category/") -> {
                val id = route.substringAfter('/')
                home()
                waitPage(Screen.Main.route)
                clickText(
                    when (id) {
                        Screen.Category.ALL -> str(R.string.all_features)
                        Screen.Category.FAVOURITES -> str(R.string.favourites)
                        Screen.Category.RECENT -> str(R.string.recent)
                        else -> str(FeatureCategory.valueOf(id).titleRes)
                    }
                ) { page == route }
            }
            route.startsWith("settings/") -> {
                // A settings page of its own: from the settings
                settings()
                waitPage(Screen.Settings.route)
                return
            }
            else -> throw AssertionError("Don't know how to get to $route")
        }
        waitPage(route)
    }

    fun log(message: String) {
        Log.i(E2eTest.TAG, message)
    }

    companion object {
        /** A [click] with what it does: tries before it fails, and how long each waits for it. */
        const val TAPS = 3
        const val TAP_TOOK_MS = 5_000L

        fun withId(id: Int) = SemanticsMatcher("id $id") { it.id == id }

        fun hasAnyAncestorDialog() = SemanticsMatcher("in a dialog") { node ->
            generateSequence(node.parent) { it.parent }.any { it.config.contains(SemanticsProperties.IsDialog) }
        }

        /** A choice of an OptionSetting: its label [value], or [value] with tags ("1.21.4 · Latest"). */
        fun choice(value: String) = SemanticsMatcher("choice $value") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> t.text == value || t.text.startsWith("$value · ") }
        }
    }
}
