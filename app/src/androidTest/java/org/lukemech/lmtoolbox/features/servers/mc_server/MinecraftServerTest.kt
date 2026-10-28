package org.lukemech.lmtoolbox.features.servers.mc_server

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
 * Every type of server once, together on every Java the subsystem has ([SERVERS]), each on the newest Minecraft of
 * that type made for its Java (e.g. Paper 1.16.5 on Java 8, Fabric's newest 1.21 on Java 21): made (its Java
 * installed along), started, its console and options used, a setting taken on a restart, its loader changed, its
 * world exported, deleted. A Java this device's ABI hasn't (25 on x86): the newest older one it has instead.
 */
@RunWith(Parameterized::class)
class MinecraftServerTest(private val typeName: String, private val jdkPart: String) : E2eTest() {
    private val linux by lazy { Linux(ui) }
    private val mc by lazy { MinecraftServerDriver(ui, linux) }

    @Before
    fun clean() = mc.cleanUp()

    @After
    fun cleanAfter() = mc.cleanUp()

    @Test
    fun server() {
        val type = ServerType.valueOf(typeName)
        val wanted = Java.byPart(jdkPart)!!
        val jdk = Java.available(context).lastOrNull { it.version <= wanted.version }
        assumeTrue("No Java up to ${wanted.version} is built for this device's ABI", jdk != null)
        val version = mc.newestFor(type, jdk!!)
        assumeTrue("$type has no Minecraft made for Java ${jdk.version}", version != null)

        mc.open()
        val server = mc.create("e2e-${type.name.lowercase()}-${jdk.version}", type, version!!)
        mc.start(server)
        mc.console()
        mc.changeAndRestart(server)
        mc.exploreOptions(server)
        mc.stop(server)
        // Another version of its loader (Paper's build, Fabric's, Forge's...), installed in its place, and run
        if (type != ServerType.Vanilla) {
            mc.changeLoader(server)
            mc.start(server)
            mc.stop(server)
        }
        mc.exportWorld(server)
        mc.delete(server)
        linux.failure()?.let { throw AssertionError(it) }
    }

    companion object {
        /** A Java for each type, so that five servers cover every one; NeoForge's start at 1.20.2, past Java 8's. */
        private val SERVERS = mapOf(
            ServerType.Paper to "jdk8",
            ServerType.Forge to "jdk17",
            ServerType.Fabric to "jdk21",
            ServerType.NeoForge to "jdk21",
            ServerType.Vanilla to "jdk25",
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}")
        fun servers(): List<Array<String>> = SERVERS.map { (type, jdk) -> arrayOf(type.name, jdk) }
    }
}
