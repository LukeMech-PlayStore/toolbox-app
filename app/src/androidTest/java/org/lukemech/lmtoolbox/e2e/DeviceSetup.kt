package org.lukemech.lmtoolbox.e2e

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Not a test: run by run-tests.sh before them (and left out of their run). From Android 11 only an app makes its
 * own external files folder (Android/data/<it>), which the app does when it asks for it; bundletool puts the Play
 * modules there for local testing, so the app asks for it here, in its process, before they're put there.
 */
class DeviceSetup {
    @Test
    fun externalFilesFolder() {
        assertNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null))
    }
}
