package org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.mc_server.Memory
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerProfile
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerRunner
import org.lukemech.lmtoolbox.features.servers.mc_server.Servers
import org.lukemech.lmtoolbox.ui.components.ExpandableButton
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.listBox
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.TextSetting
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.copy

/**
 * Its options: its settings (server.properties, memory, the config file itself), the tunnels, and its
 * files. Written right away; while it runs, those changed are marked until it's restarted.
 * [toTunnel]: scrolled to the tunnels', then [onAtTunnel].
 */
@Composable
internal fun OptionsTab(
    profile: ServerProfile, run: ServerRunner.Run?, onChanged: () -> Unit, onDeleted: () -> Unit,
    toTunnel: Boolean, onAtTunnel: () -> Unit
) {
    val context = LocalContext.current
    val running = run?.isActive == true
    var version by remember { mutableIntStateOf(0) }
    val props = remember(profile.id, version) { Servers.properties(context, profile.id) }
    fun prop(key: String, default: String) = props.get(key) ?: default
    fun set(key: String, value: String) {
        props.set(key, value).save()
        ServerRunner.changed(profile.id, key, value)
        version++
    }
    fun bool(key: String, default: Boolean) = prop(key, default.toString()).toBoolean()
    @Composable
    fun m(key: String) = mark(running && key in run.pending)

    val tunnelAt = remember { BringIntoViewRequester() }
    LaunchedEffect(toTunnel) {
        if (toTunnel) {
            // Its title at the top, not just in sight (at the bottom): a rect from it taller than the page
            // is brought in at its top. Again once what's above has loaded (the version's), moving it down
            repeat(2) {
                delay(if (it == 0) 100.milliseconds else 600.milliseconds)
                tunnelAt.bringIntoView(Rect(0f, 0f, 1f, 100_000f))
            }
            onAtTunnel()
        }
    }

    Column(verticalArrangement = RowArrangement) {
        Section(stringResource(R.string.f_Mc_section_version))
        VersionSettings(profile, run, onChanged)

        Section(stringResource(R.string.f_Mc_section_server))
        TextSetting(stringResource(R.string.f_Mc_motd), prop("motd", ""), Icons.Default.Badge, mark = m("motd")) { set("motd", it) }
        NumberSetting(stringResource(R.string.f_Mc_max_players), prop("max-players", "20"), Icons.Default.Group, 1..1000, m("max-players")) { set("max-players", it) }
        OptionSetting(
            stringResource(R.string.f_Mc_gamemode), Icons.Default.SportsKabaddi, prop("gamemode", "survival"),
            listOf(
                "survival" to stringResource(R.string.f_Mc_gm_survival), "creative" to stringResource(R.string.f_Mc_gm_creative),
                "adventure" to stringResource(R.string.f_Mc_gm_adventure), "spectator" to stringResource(R.string.f_Mc_gm_spectator)
            ),
            colors = m("gamemode")
        ) { set("gamemode", it) }
        OptionSetting(
            stringResource(R.string.f_Mc_difficulty), Icons.Default.Tune, prop("difficulty", "easy"),
            listOf(
                "peaceful" to stringResource(R.string.f_Mc_diff_peaceful), "easy" to stringResource(R.string.f_Mc_diff_easy),
                "normal" to stringResource(R.string.f_Mc_diff_normal), "hard" to stringResource(R.string.f_Mc_diff_hard)
            ),
            colors = m("difficulty")
        ) { set("difficulty", it) }
        Toggle(stringResource(R.string.f_Mc_online_mode), Icons.Default.VerifiedUser, bool("online-mode", true), m("online-mode"), stringResource(R.string.f_Mc_online_mode_desc)) {
            set("online-mode", it.toString())
        }
        Toggle(stringResource(R.string.f_Mc_whitelist), Icons.Default.Shield, bool("white-list", false), m("white-list"), stringResource(R.string.f_Mc_whitelist_desc)) {
            // One setting: enforcing it goes along (not a change of its own waiting for a restart)
            props.set("enforce-whitelist", it.toString())
            set("white-list", it.toString())
        }
        Toggle(stringResource(R.string.f_Mc_pvp), Icons.Default.SportsKabaddi, bool("pvp", true), m("pvp")) { set("pvp", it.toString()) }
        Toggle(stringResource(R.string.f_Mc_flight), Icons.Default.Flight, bool("allow-flight", false), m("allow-flight"), stringResource(R.string.f_Mc_flight_desc)) {
            set("allow-flight", it.toString())
        }
        Toggle(stringResource(R.string.f_Mc_hardcore), Icons.Default.HeartBroken, bool("hardcore", false), m("hardcore")) { set("hardcore", it.toString()) }
        SliderSetting(stringResource(R.string.f_Mc_view_distance), Icons.Default.Visibility, prop("view-distance", "10").toIntOrNull() ?: 10, 3..32, m("view-distance")) {
            set("view-distance", it.toString())
        }
        SliderSetting(stringResource(R.string.f_Mc_simulation_distance), Icons.Default.Terrain, prop("simulation-distance", "10").toIntOrNull() ?: 10, 3..32, m("simulation-distance")) {
            set("simulation-distance", it.toString())
        }
        NumberSetting(stringResource(R.string.f_Mc_port), prop("server-port", "25565"), Icons.Default.Dns, 1024..65535, m("server-port")) { set("server-port", it) }
        // The seed: settable until the world is made, then the world's (as the server told it)
        val worldMade = remember(profile.id, run?.phase) { Servers.worldDirs(context, profile).isNotEmpty() }
        Reveal(visible = worldMade) {
            val seed = remember(profile.id, run?.phase) { Servers.seed(context, profile) }
            FeatureButton(
                title = stringResource(R.string.f_Mc_seed),
                subtitle = seed ?: stringResource(R.string.f_Mc_seed_unknown),
                icon = Icons.Default.Landscape,
                buttonContent = if (seed != null) {
                    { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.f_Srv_copy), tint = MaterialTheme.colorScheme.primary) }
                } else null,
                onClick = { seed?.let { copy(context, it) } }
            )
        }
        Reveal(visible = !worldMade) {
            TextSetting(stringResource(R.string.f_Mc_seed), prop("level-seed", ""), Icons.Default.Landscape, subtitle = stringResource(R.string.f_Mc_seed_desc), mark = m("level-seed")) {
                set("level-seed", it)
            }
        }

        // Its memory (the profile's, not a property)
        var memory by remember(profile.id) { mutableIntStateOf(profile.memoryMb) }
        val max = remember { Memory.max(context) }
        val memoryMark = m(ServerRunner.MEMORY)
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = memoryMark.background,
            contentColor = memoryMark.content,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.f_Mc_memory), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(memoryText(memory), color = MaterialTheme.colorScheme.primary)
                }
                MemorySlider(memory, max) {
                    memory = it
                    Servers.load(context, profile.id)?.let { p -> Servers.save(context, p.copy(memoryMb = it)) }
                    ServerRunner.changed(profile.id, ServerRunner.MEMORY, it.toString())
                    onChanged()
                }
            }
        }

        // The file itself, as the server keeps it (but the app's console's): some 60 fields, scrolling in a box
        // of its own (as the versions' list)
        ExpandableButton(stringResource(R.string.f_Mc_config_file), Icons.Default.Description) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.listBox(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("server.properties", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val all = props.all.filterNot { (k, _) -> k.startsWith("rcon.") || k == "enable-rcon" }
                if (all.size < 10) Text(stringResource(R.string.f_Mc_config_file_first), style = MaterialTheme.typography.bodySmall)
                all.forEach { (key, value) -> PropertyField(key, value, changed = running && key in run.pending) { set(key, it) } }
            }
        }

        // From anywhere: bore first (nothing to set up), then Cloudflare's
        Box(modifier = Modifier.bringIntoViewRequester(tunnelAt)) { Section(stringResource(R.string.f_Srv_internet)) }
        BoreSection(profile, onChanged)
        TunnelSection(profile, onChanged)

        Section(stringResource(R.string.f_Mc_section_files))
        Files(profile, running, onDeleted)
    }
}

/**
 * How many settings wait for a restart, dropping them ([onDiscard]) and restarting it: under the
 * options, in sight wherever they're scrolled to.
 */
@Composable
internal fun PendingBar(profile: ServerProfile, run: ServerRunner.Run, onDiscard: () -> Unit) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Icon(Icons.Default.RestartAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                pluralStringResource(R.plurals.f_Mc_pending, run.pending.size, run.pending.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { ServerRunner.restart(context, profile.id) }, enabled = run.phase == ServerRunner.Phase.Running) {
                Text(stringResource(R.string.f_Mc_restart))
            }
            // Last and red, as deleting is elsewhere (recent, favourites)
            IconButton(onClick = onDiscard) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.f_Mc_discard), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
