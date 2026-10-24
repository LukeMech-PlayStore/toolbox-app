package org.lukemech.lmtoolbox.features.servers.mc_server

import java.io.File
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.lukemech.lmtoolbox.e2e.E2eTest
import org.lukemech.lmtoolbox.e2e.Linux
import org.lukemech.lmtoolbox.linux.parts.Java

/**
 * A Paper server on every Java the subsystem has (each on the newest Minecraft made for it: 1.16.5 on 8, 1.20.4
 * on 17, the newest 1.21 on 21, the newest of all on 25): made, its Java installed along, started, its console
 * and options used, a plugin added (Chunky, from Modrinth) and loaded on a restart, its world exported, stopped
 * and deleted.
 */
@RunWith(Parameterized::class)
class MinecraftServerTest(private val jdkPart: String) : E2eTest() {
    private val linux by lazy { Linux(ui) }
    private val mc by lazy { MinecraftServerDriver(ui, linux) }

    @Before
    fun clean() = mc.cleanUp()

    @After
    fun cleanAfter() = mc.cleanUp()

    @Test
    fun paperServer() {
        val jdk = Java.byPart(jdkPart)!!
        assumeTrue("$jdkPart isn't built for this device's ABI", jdk in Java.available(context))
        val version = mc.newestFor(ServerType.Paper, jdk)
        assumeTrue("Paper has no Minecraft made for $jdkPart", version != null)

        mc.open()
        val server = mc.create("e2e-paper-${jdk.version}", ServerType.Paper, version!!)
        mc.start(server)
        mc.console()
        mc.changeAndRestart(server)
        mc.exploreOptions(server)

        // A plugin, as a user downloads one and adds it
        val plugin = Modrinth.download("chunky", "paper", version, File(context.cacheDir, "e2e-addons"))
        if (plugin != null) {
            mc.addAddon(server, plugin)
            mc.stopAndStart(server)
            mc.waitLog("Chunky to load") { "Chunky" in it && ("Enabling" in it || "Loading" in it) }
        } else {
            ui.log("No Chunky for Paper $version on Modrinth: no plugin tried")
        }

        mc.stop(server)
        mc.exportWorld(server)
        mc.delete(server)
        linux.failure()?.let { throw AssertionError(it) }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun jdks(): List<String> = Java.JDKS.map { it.part }
    }
}
