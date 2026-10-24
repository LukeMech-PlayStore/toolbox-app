package org.lukemech.lmtoolbox.features.servers.mc_server

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftServerTest {
    @Test
    fun neoForgeVersionsMapToMinecraft() {
        assertEquals("1.21.1", Installer.neoForgeMinecraft("21.1.77"))
        assertEquals("1.21", Installer.neoForgeMinecraft("21.0.167"))
        assertEquals("1.20.4", Installer.neoForgeMinecraft("20.4.237-beta"))
        // From 2026 on, Minecraft's own numbers
        assertEquals("26.3", Installer.neoForgeMinecraft("26.3.0.94"))
        assertEquals("26.1.1", Installer.neoForgeMinecraft("26.1.1.12"))
        // April Fools' and snapshot builds
        assertNull(Installer.neoForgeMinecraft("0.25w14craftmine.3-beta"))
        assertNull(Installer.neoForgeMinecraft("snapshot"))
    }

    @Test
    fun propertiesKeepTheFileAsWritten() {
        val file = File.createTempFile("server", ".properties").apply { deleteOnExit() }
        file.writeText("#Minecraft server properties\nmotd=A Minecraft Server\nlevel-name=world\\:one\nmax-players=20\n")
        val props = ServerProperties(file)
        assertEquals("world:one", props.get("level-name"))
        props.set("max-players", "5").set("enable-rcon", "true").set("motd", " Mine \\ yours").save()

        assertEquals(
            "#Minecraft server properties\nmotd=\\ Mine \\\\ yours\nlevel-name=world\\:one\nmax-players=5\nenable-rcon=true\n",
            file.readText()
        )
        val again = ServerProperties(file)
        assertEquals(" Mine \\ yours", again.get("motd"))
        assertEquals(listOf("motd", "level-name", "max-players", "enable-rcon"), again.all.map { it.first })

        // As every server reads it (1.7.10's as ISO-8859-1), and back
        again.set("level-name", "Mój świat").save()
        assertTrue("level-name=M\\u00F3j \\u015Bwiat\n" in file.readText())
        assertEquals("Mój świat", ServerProperties(file).get("level-name"))
    }

    @Test
    fun javaAsMojangHasIt() {
        assertEquals(8, Installer.javaFor("1.2.5"))
        assertEquals(8, Installer.javaFor("1.16.5"))
        assertEquals(16, Installer.javaFor("1.17.1"))
        assertEquals(17, Installer.javaFor("1.18"))
        assertEquals(17, Installer.javaFor("1.20.4"))
        assertEquals(21, Installer.javaFor("1.20.5"))
        assertEquals(21, Installer.javaFor("1.21.11"))
        assertEquals(25, Installer.javaFor("26.1"))
        assertEquals(25, Installer.javaFor("26.3"))
        // Snapshots by their year and week, pre-releases by their version
        assertEquals(8, Installer.javaFor("13w16a"))
        assertEquals(16, Installer.javaFor("21w19a"))
        assertEquals(17, Installer.javaFor("23w51b"))
        assertEquals(21, Installer.javaFor("24w14a"))
        assertEquals(25, Installer.javaFor("26w01a"))
        assertEquals(21, Installer.javaFor("1.20.5-rc1"))
    }

    @Test
    fun hostnamesAsTyped() {
        assertEquals("mc.example.com", Network.hostnameOf(" https://MC.example.com:25565/x "))
        assertEquals("mc.example.com", Network.hostnameOf("mc.example.com."))
    }
}
