package org.lukemech.lmtoolbox.features.servers.mc_server

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performSemanticsAction
import java.net.ServerSocket
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
 * started, its console used, its options changed (and taken on a restart), its loader changed, its world
 * exported, stopped and deleted. Driven through the UI; how it went read from the tool's own state.
 */
class MinecraftServerDriver(private val ui: Ui, private val linux: Linux) {
    private val context get() = ui.context
    private fun s(id: Int, vararg args: Any) = ui.str(id, *args)

    /**
     * No server left from an earlier test (one that failed half-way): none running (its Java gone, its port free for
     * the next), none there.
     */
    fun cleanUp() {
        if (ServerRunner.state.value?.isActive == true) ServerRunner.kill(delete = false)
        Device.waitFor("the server left running to end", 2 * Device.MINUTE) { ServerRunner.state.value?.isActive != true }
        Device.waitFor("port $PORT freed by the server left running (its Java still there)", 2 * Device.MINUTE) {
            runCatching { ServerSocket(PORT).close() }.isSuccess
        }
        // A server made that failed (or wasn't opened) is kept as a draft the new server's page starts from, its
        // EULA accepted: the next one's tap would take that back
        Installer.clear()
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


    // An OptionSetting's choice: its radio button alone, no text (the server page's tabs are radio buttons too, named)
    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton) and
        SemanticsMatcher.keyNotDefined(SemanticsProperties.Text)

    /**
     * Picks [value] in the OptionSetting titled [title], once its choices have loaded, until [taken] (by default:
     * the setting shows it).
     */
    private fun choose(title: String, value: String, taken: () -> Boolean = { ui.exists(hasText(title) and Ui.choice(value)) }) {
        val row = hasText(title) and hasClickAction()
        val target = Ui.choice(value) and hasClickAction() and isEnabled()
        runCatching {
            ui.compose.waitUntil(3 * Device.MINUTE) {
                when {
                    taken() -> return@waitUntil true
                    // Opened (a tap), unless its choices are open already; while they load, the tap does nothing
                    !ui.exists(radio) -> runCatching { ui.click(row, timeoutMs = 5_000) }
                    // Its own click, not a tap at its middle: in a box scrolling inside the page, that may land
                    // past the box's edge (a tap that picked nothing)
                    ui.scrollTo(target) -> runCatching {
                        ui.compose.onAllNodes(target)[0].performSemanticsAction(SemanticsActions.OnClick)
                        ui.compose.waitForIdle()
                    }
                }
                taken()
            }
        }.onFailure {
            Device.screenshot("mc-not-chosen-$value")
            throw AssertionError("$value not picked in $title after 3 min")
        }
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
        when (r.phase) {
            ServerRunner.Phase.Crashed -> "the server crashed: ${r.error}\n${logTail()}"
            ServerRunner.Phase.Stopped -> r.error?.let { "the server stopped: $it\n${logTail()}" }
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
            ui, "mc-${profile.name}", maxActions = 400, depth = 0,
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
        // Asked first: the setting changes once it's installed
        choose(s(R.string.f_Mc_loader_version, profile.type.label), Installer.loaderLabel(profile.type, other.version)) { ui.dialogShown }
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

    companion object {
        /** Minecraft's own, every test's server's (what changes it is dropped, see [exploreOptions]). */
        const val PORT = 25565
    }
}
