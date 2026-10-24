package org.lukemech.lmtoolbox.linux.parts

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class JavaTest {
    private val jdk = Java.Jdk("jdk25", 25)

    @Test
    fun commandLine() {
        assertEquals(
            listOf("java-25", "-Xms1024M", "-Xmx2048M", "-XX:-UsePerfData", "-Djava.awt.headless=true", "-Dx=1", "-jar", "server.jar", "nogui"),
            Java.command(jdk, listOf("-jar", "server.jar", "nogui"), heap = Java.Heap(2048, initialMb = 1024), options = listOf("-Dx=1"))
        )
        // The initial heap never above the most
        assertEquals("-Xms512M", Java.command(jdk, emptyList(), heap = Java.Heap(512, initialMb = 1024))[1])
    }

    @Test
    fun runsInItsFolder() {
        val dir = File("/data/servers/s1")
        val cmd = Java.command(jdk, listOf("-version"), dir = dir)
        assertEquals(listOf("sh", "-c", $$"cd \"$0\" && exec \"$@\"", dir.absolutePath, "java-25"), cmd.take(5))
        assertEquals("-version", cmd.last())
    }

    @Test
    fun writesItsProcessId() {
        val cmd = Java.command(jdk, listOf("-version"), dir = File("/data/s1"), pidFile = File("/data/s1/.pid"))
        assertEquals(listOf("sh", "-c", $$"cd \"$0\" && echo $$ > \"$1\" && shift && exec \"$@\"", File("/data/s1").absolutePath, File("/data/s1/.pid").absolutePath, "java-25"), cmd.take(6))
    }

    @Test
    fun versionsByPart() {
        assertEquals(21, Java.byPart("jdk21")?.version)
        assertEquals("java-21", Java.byPart("jdk21")?.program)
        assertEquals(null, Java.byPart("jdk"))
    }

    @Test
    fun eachVersionOnTheOneItsMadeFor() {
        // Minecraft 1.17's 16: 17 (no one builds 16 for Android); never a newer one than that
        assertEquals("jdk17", Java.madeFor(16)?.part)
        assertEquals("jdk17", Java.madeFor(17)?.part)
        assertEquals("jdk21", Java.madeFor(21)?.part)
        assertEquals("jdk25", Java.madeFor(25)?.part)
        assertEquals(null, Java.madeFor(26))
        // Older Minecraft's 8: its own, not 17
        assertEquals("jdk8", Java.madeFor(8)?.part)
        assertEquals("java-8", Java.madeFor(8)?.program)
    }
}
