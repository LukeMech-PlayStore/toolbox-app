package org.lukemech.lmtoolbox.linux

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxScriptsTest {
    @Test
    fun `script folders no tool has anymore go, the tools' own stay`() {
        val features = Files.createTempDirectory("features").toFile().apply { deleteOnExit() }
        File(features, "CurrencyConverter").mkdirs()
        File(features, "CurrencyConverter/ecb.zip").writeText("rates")
        File(features, "qr_gen").mkdirs()
        File(features, "qr_gen/gen.py").writeText("script")
        assertEquals(listOf("CurrencyConverter"), LinuxScripts.removeStale(features, setOf("currencyconv", "qr_gen")))
        assertFalse(File(features, "CurrencyConverter").exists())
        assertTrue(File(features, "qr_gen/gen.py").exists())
    }

    @Test
    fun `nothing installed yet, nothing to remove`() {
        assertEquals(emptyList<String>(), LinuxScripts.removeStale(File("no-such-folder"), emptySet()))
    }
}
