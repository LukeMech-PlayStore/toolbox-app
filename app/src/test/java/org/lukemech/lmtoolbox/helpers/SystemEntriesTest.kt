package org.lukemech.lmtoolbox.helpers

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SystemEntriesTest {
    /** The manifest's activity-alias names (tests run in the module's folder). */
    private val manifestAliases: Set<String> =
        Regex("""<activity-alias\s[^>]*?android:name="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
            .findAll(File("src/main/AndroidManifest.xml").readText())
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun `every entry has its manifest alias and every alias its entry`() {
        val entries = SystemEntries.byFeature.values.flatten().map { it.aliasName }.toSet()
        assertEquals(manifestAliases, entries)
    }

    @Test
    fun `every entry opens a tool`() {
        SystemEntries.byFeature.keys.forEach { assertNotNull(it, FeatureRegistry.byRoute(it)) }
        SystemEntries.byFeature.values.flatten().forEach { assertNotNull(it.aliasName, SystemEntries.featureOf(it.aliasName)) }
    }

    @Test
    fun `tool routes are unique`() {
        val routes = FeatureRegistry.features.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }
}
