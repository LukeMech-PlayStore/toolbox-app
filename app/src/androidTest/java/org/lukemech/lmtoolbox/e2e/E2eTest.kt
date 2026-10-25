package org.lukemech.lmtoolbox.e2e

import android.Manifest
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.intent.Intents
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.lukemech.lmtoolbox.MainActivity
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.ui.helpers.PREF_NOTIFICATIONS_LATER

/**
 * Every end-to-end test: the app started fresh on its home page (as from the launcher), its permissions
 * granted first, what it hands to other apps (pickers, browser, share sheet) answered here ([Pickers]),
 * and a screenshot of how it looked when a test failed (pulled by the workflow, see tests.yml).
 */
abstract class E2eTest {
    @get:Rule(order = 0)
    val setUp: TestRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                grantPermissions()
                Device.dismissSystemDialogs()
                // As a user who has said "Not now" to the notifications bar (on API < 33 there's nothing to grant):
                // it'd cover the bottom of every page
                PreferenceHelper.put(context, PREF_NOTIFICATIONS_LATER, true)
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val compose: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> = createAndroidComposeRule<MainActivity>()

    /** Inside the activity's life, so it's still on screen when one fails. Each test's start in the job's log too. */
    @get:Rule(order = 2)
    val screenshots = object : TestWatcher() {
        override fun starting(description: Description) {
            Log.i(TAG, "Test ${description.className.substringAfterLast('.')}.${description.methodName}")
        }

        override fun failed(e: Throwable?, description: Description) {
            Device.screenshot("FAILED-${description.className.substringAfterLast('.')}.${description.methodName}")
        }
    }

    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    val ui by lazy { Ui(compose) }

    @Before
    fun stubOtherApps() {
        Intents.init()
        Pickers.install(context)
        Device.clock = compose.mainClock
    }

    @After
    fun releaseStubs() {
        Device.clock = null
        Intents.release()
    }

    private fun grantPermissions() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.forEach { permission ->
            runCatching { automation.grantRuntimePermission(pkg, permission) }
                .onFailure { Log.w(TAG, "Couldn't grant $permission", it) }
        }
    }

    companion object {
        const val TAG = "E2E"
    }
}
