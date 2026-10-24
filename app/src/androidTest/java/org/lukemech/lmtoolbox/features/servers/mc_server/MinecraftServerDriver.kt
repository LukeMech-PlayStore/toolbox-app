package org.lukemech.lmtoolbox.features.servers.mc_server

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.e2e.Device
import org.lukemech.lmtoolbox.e2e.Explorer
import org.lukemech.lmtoolbox.e2e.Linux
import org.lukemech.lmtoolbox.e2e.Pickers
import org.lukemech.lmtoolbox.e2e.Ui
import org.lukemech.lmtoolbox.linux.parts.Java

/**
 * The Minecraft server tool, as a user goes about it: a server made on its page (its kind, version, loader),
 * started, its console used, its options changed (and taken on a restart), plugins or mods added, its world
 * exported, stopped and deleted. Driven through the UI; how it went read from the tool's own state.
 */
class MinecraftServerDriver(private val ui: Ui, private val linux: Linux) {
    private val context get() = ui.context
    private fun s(id: Int, vararg args: Any) = ui.str(id, *args)

    /** No server left from an earlier test (one that failed half-way): none running, none there. */
    fun cleanUp() {
        if (ServerRunner.state.value?.isActive == true) ServerRunner.kill(delete = false)
        Servers.list(context).forEach { Servers.delete(context, it.id) }
    }

    /** The tool's page, its core installed (from its installation page). */
    fun open() {
        ui.openTool(MinecraftServerFeature)
        linux.install(MinecraftServerFeature)
    }

    /**
     * The newest release of [type] Minecraft made for Java [jdk] (e.g. 1.16.5 for Java 8 on Paper); null when
     * [type] has none. Mojang's metadata (javaNeeds) decides, its version's number first narrowing it down.
     */
    fun newestFor(type: ServerType, jdk: Java.Jdk): String? = runBlocking {
        Installer.versions(type).filter { Java.madeFor(Installer.javaFor(it)) == jdk }
            .take(5).firstOrNull { Java.madeFor(Installer.javaNeeds(it)) == jdk }
    }

    /** [type]'s newest release this device has a Java for (no Java 25 on x86). */
    fun newestRunnable(type: ServerType): String = runBlocking {
        Installer.versions(type).first { Java.forNeeds(context, Installer.javaNeeds(it)) != null }
    }

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    /** Picks [value] in the OptionSetting titled [title], once its choices have loaded. */
    private fun choose(title: String, value: String) {
        val row = hasText(title) and hasClickAction()
        val target = Ui.choice(value) and hasClickAction()
        ui.compose.waitUntil(3 * Device.MINUTE) {
            // Opened (a tap), unless its choices are open already; while they load, the tap does nothing
            if (!ui.exists(radio)) runCatching { ui.click(row, timeoutMs = 5_000) }
            ui.exists(radio) && ui.scrollTo(target)
        }
        ui.click(target, what = "$value in $title")
    }

    /** A new server from the page's form: [name], [type], Minecraft [version] and the loader's [loader] (its recommended one if null). */
    fun create(name: String, type: ServerType, version: String, loader: String? = null): ServerProfile {
        ui.log("Creating $name: $type $version ${loader ?: ""}")
        ui.clickText(R.string.f_Mc_new)
        ui.type(s(R.string.f_Mc_name), name)
        ui.clickText(type.label)
        choose(s(R.string.f_Mc_version), version)
        if (loader != null) choose(s(R.string.f_Mc_loader_version, type.label), Installer.loaderLabel(type, loader))
        // Mojang's EULA: accepted, else it won't be made
        ui.clickText(R.string.f_Mc_eula)
        Device.screenshot("mc-new-$name")
        // Enabled once the Java its version needs is known
        ui.click(hasText(s(R.string.f_Mc_create)) and hasClickAction(), timeoutMs = 2 * Device.MINUTE, what = "Create")
        Device.waitFor("$name to be made", 40 * Device.MINUTE, intervalMs = 2000, failFast = {
            Installer.state.value?.takeIf { it.stage == Installer.Stage.Failed }?.let { "it failed: ${it.error}\n${linux.failure().orEmpty()}" }
                ?: linux.failure()
        }) {
            // Java, if it isn't there yet: asked first
            linux.confirmPrompt()
            Servers.list(context).any { it.name == name }
        }
        val profile = Servers.list(context).first { it.name == name }
        assertEquals(type, profile.type)
        assertEquals(version, profile.version)
        if (loader != null) assertEquals(loader, profile.loader)
        // Straight to it
        ui.waitShown(hasText(s(R.string.f_Mc_start)) and hasClickAction(), timeoutMs = 60_000, what = "$name's page")
        Device.screenshot("mc-created-$name")
        return profile
    }

    fun tab(id: Int) = ui.clickText(id)

    private val run get() = ServerRunner.state.value

    private fun logTail(lines: Int = 80) = ServerRunner.log.value.takeLast(lines).joinToString("\n")

    /** Fails the wait when the server's not getting there anymore: crashed, or stopped by itself (saying why). */
    private fun crashed(): String? = run?.let { r ->
        when {
            r.phase == ServerRunner.Phase.Crashed -> "the server crashed: ${r.error}\n${logTail()}"
            r.phase == ServerRunner.Phase.Stopped && r.error != null -> "the server stopped: ${r.error}\n${logTail()}"
            else -> null
        }
    } ?: linux.failure()

    /** Started from its status tab, until it's ready (Minecraft's "Done"). */
    fun start(profile: ServerProfile) {
        tab(R.string.f_Mc_tab_status)
        ui.click(hasText(s(R.string.f_Mc_start)) and hasClickAction(), what = "Start")
        waitRunning(profile)
        ui.waitShown(hasText(s(R.string.f_Mc_phase_running)), what = "\"running\" on its status")
        Device.screenshot("mc-running-${profile.name}")
    }

    private fun waitRunning(profile: ServerProfile, after: Long? = null) {
        Device.waitFor("${profile.name} to run", 30 * Device.MINUTE, intervalMs = 2000, failFast = ::crashed) {
            linux.confirmPrompt()
            run?.let { it.serverId == profile.id && it.phase == ServerRunner.Phase.Running && (after == null || (it.readyAt ?: 0) > after) } == true
        }
    }

    /** Its console: a quick command, then one typed. */
    fun console() {
        tab(R.string.f_Mc_tab_console)
        ui.clickText("/list")
        Device.waitFor("/list's answer", 60_000) { ServerRunner.log.value.any { "players online" in it || "There are" in it } }
        ui.type(s(R.string.f_Mc_command), "say e2e-hello")
        ui.click(hasContentDescription(s(R.string.f_Mc_send)) and hasClickAction())
        Device.waitFor("the server to say it", 60_000) { ServerRunner.log.value.any { "e2e-hello" in it } }
        Device.screenshot("mc-console")
    }

    /** An option changed while it runs (PvP off): marked, taken on the restart its bar offers. */
    fun changeAndRestart(profile: ServerProfile) {
        tab(R.string.f_Mc_tab_options)
        ui.clickText(R.string.f_Mc_pvp)
        Device.waitFor("PvP to wait for a restart", 30_000) { run?.pending?.contains("pvp") == true }
        assertEquals("false", Servers.properties(context, profile.id).get("pvp"))
        Device.screenshot("mc-pending")
        val readyAt = run?.readyAt ?: 0
        ui.clickText(R.string.f_Mc_restart)
        waitRunning(profile, after = readyAt)
        assertTrue("Still waiting for a restart: ${run?.pending}", run?.pending.isNullOrEmpty())
    }

    /** Every option tried (but those that'd stop, delete or move it): what's changed dropped after. */
    fun exploreOptions(profile: ServerProfile) {
        tab(R.string.f_Mc_tab_options)
        Explorer(
            ui, "mc-${profile.name}", maxActions = 80, depth = 0,
            extraSkips = listOf(
                s(R.string.f_Mc_start), s(R.string.f_Mc_stop), s(R.string.f_Mc_restart), s(R.string.f_Mc_export_world),
                s(R.string.f_Mc_export_server), s(R.string.f_Mc_import_world), s(R.string.f_Mc_add_plugins), s(R.string.f_Mc_add_mods),
                s(R.string.f_Mc_update),
            ),
            check = ::crashed
        ).explore()
        // Back as it started (its port, its mode...): what waits for a restart dropped
        if (run?.pending?.isNotEmpty() == true) ui.click(hasContentDescription(s(R.string.f_Mc_discard)) and hasClickAction())
        Device.waitFor("its changes dropped", 30_000) { run?.pending.isNullOrEmpty() }
    }

    /** Its loader changed (stopped) from its options to another of its versions, confirmed, and reinstalled. */
    fun changeLoader(profile: ServerProfile) {
        val other = runBlocking { Installer.loaders(profile.type, profile.version) }.firstOrNull { it.version != profile.loader }
            ?: return ui.log("${profile.type} ${profile.version} has a single loader: not changed")
        tab(R.string.f_Mc_tab_options)
        choose(s(R.string.f_Mc_loader_version, profile.type.label), Installer.loaderLabel(profile.type, other.version))
        ui.clickInDialog(s(R.string.f_Mc_install))
        Device.waitFor("its loader changed to ${other.version}", 30 * Device.MINUTE, intervalMs = 2000, failFast = {
            Installer.state.value?.takeIf { it.stage == Installer.Stage.Failed }?.let { "it failed: ${it.error}\n${linux.failure().orEmpty()}" }
                ?: linux.failure()
        }) {
            linux.confirmPrompt()
            Servers.load(context, profile.id)?.loader == other.version
        }
        Device.screenshot("mc-loader-${other.version}")
    }

    /** Adds [jar] (a plugin or a mod) from its options, as picked in the system's picker. */
    fun addAddon(profile: ServerProfile, jar: File) {
        tab(R.string.f_Mc_tab_options)
        Pickers.pick(context, jar)
        ui.clickText(if (profile.type.hasMods) R.string.f_Mc_add_mods else R.string.f_Mc_add_plugins)
        Device.waitFor("${jar.name} added", 60_000) { Transfers.addons(context, profile).any { it.name == jar.name } }
        ui.waitShown(hasText(jar.name.removeSuffix(".jar")), what = "${jar.name} listed")
        Device.screenshot("mc-addon-${jar.name}")
    }

    /** Stopped, then started again from its status tab (e.g. for what was added to load). */
    fun stopAndStart(profile: ServerProfile) {
        stop(profile)
        start(profile)
    }

    fun stop(profile: ServerProfile) {
        tab(R.string.f_Mc_tab_status)
        ui.click(hasText(s(R.string.f_Mc_stop)) and hasClickAction(), what = "Stop")
        Device.waitFor("${profile.name} to stop", 5 * Device.MINUTE) { run?.isActive != true }
        assertTrue("It didn't stop cleanly: ${run?.phase} ${run?.error}\n${logTail()}", run?.phase != ServerRunner.Phase.Crashed)
    }

    /** Its world out as a zip (through the system's "save as", answered by a file of the test's). */
    fun exportWorld(profile: ServerProfile) {
        tab(R.string.f_Mc_tab_options)
        Pickers.created.clear()
        ui.clickText(R.string.f_Mc_export_world)
        Device.waitFor("the world exported", 10 * Device.MINUTE, failFast = {
            Transfers.state.value?.takeIf { !it.isActive && it.error != null }?.error
        }) { Pickers.created.isNotEmpty() && Transfers.state.value?.let { !it.isActive && it.serverId == profile.id } == true }
        val zip = Pickers.created.single()
        val magic = ByteArray(2).also { b -> zip.inputStream().use { it.read(b) } }
        assertTrue("${zip.name} isn't a zip (${zip.length()} bytes)", zip.length() > 1000 && magic.contentEquals(byteArrayOf(0x50, 0x4B)))
    }

    /** Deleted from its options (confirmed), gone from the list. */
    fun delete(profile: ServerProfile) {
        tab(R.string.f_Mc_tab_options)
        ui.clickText(R.string.f_Mc_delete_server)
        ui.clickInDialog(s(R.string.f_Mc_delete))
        Device.waitFor("${profile.name} deleted", 60_000) { Servers.load(context, profile.id) == null }
        ui.waitShown(hasText(s(R.string.f_Mc_new)) and hasClickAction(), what = "the server list")
    }

    /** Lines of its log, for checks. */
    fun logHas(text: String) = ServerRunner.log.value.any { text in it }

    fun waitLog(what: String, timeoutMs: Long = 2 * Device.MINUTE, matches: (String) -> Boolean) =
        Device.waitFor(what, timeoutMs, failFast = ::crashed) { ServerRunner.log.value.any(matches) }
}
