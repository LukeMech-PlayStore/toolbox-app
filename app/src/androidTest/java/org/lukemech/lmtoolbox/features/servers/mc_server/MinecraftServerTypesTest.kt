package org.lukemech.lmtoolbox.features.servers.mc_server

import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.lukemech.lmtoolbox.e2e.E2eTest
import org.lukemech.lmtoolbox.e2e.Linux

/**
 * Every other type of server (MinecraftServerTest has Paper, on every Java), each on the newest Minecraft it has that
 * this device has a Java for: Vanilla, Fabric with a mod (Lithium) and its loader changed, Forge and NeoForge (their
 * installers run on the phone).
 */
@RunWith(Parameterized::class)
class MinecraftServerTypesTest(private val typeName: String) : E2eTest() {
    private val linux by lazy { Linux(ui) }
    private val mc by lazy { MinecraftServerDriver(ui, linux) }

    @Before
    fun clean() = mc.cleanUp()

    @After
    fun cleanAfter() = mc.cleanUp()

    @Test
    fun server() {
        val type = ServerType.valueOf(typeName)
        val version = mc.newestRunnable(type)
        mc.open()
        val server = mc.create("e2e-${type.name.lowercase()}", type, version)
        mc.start(server)
        mc.console()

        if (type == ServerType.Fabric) {
            val mod = Modrinth.download("lithium", "fabric", version, File(context.cacheDir, "e2e-addons"))
            if (mod != null) {
                mc.addAddon(server, mod)
                mc.stopAndStart(server)
                mc.waitLog("Lithium to load") { "lithium" in it.lowercase() }
            }
            mc.stop(server)
            mc.changeLoader(server)
            mc.start(server)
            mc.stop(server)
        } else {
            mc.stop(server)
        }
        mc.delete(server)
        linux.failure()?.let { throw AssertionError(it) }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun types(): List<String> =
            listOf(ServerType.Vanilla, ServerType.Fabric, ServerType.Forge, ServerType.NeoForge).map { it.name }
    }
}
