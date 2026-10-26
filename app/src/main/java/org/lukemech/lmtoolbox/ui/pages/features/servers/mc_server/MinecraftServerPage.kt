package org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server

import org.lukemech.lmtoolbox.ui.components.Reveal
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlin.math.roundToInt
import org.lukemech.lmtoolbox.features.servers.mc_server.Installer
import org.lukemech.lmtoolbox.features.servers.mc_server.Memory
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerProfile
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerRunner
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerType
import org.lukemech.lmtoolbox.features.servers.mc_server.Servers
import org.lukemech.lmtoolbox.helpers.ResourceScope
import org.lukemech.lmtoolbox.linux.parts.Java
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.LinuxTask
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.ui.components.PackageTable
import org.lukemech.lmtoolbox.ui.components.PackageTableRow
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.ResourceTiles
import org.lukemech.lmtoolbox.ui.components.SectionTitle
import org.lukemech.lmtoolbox.ui.components.StepWithButtons
import org.lukemech.lmtoolbox.ui.components.Steps
import org.lukemech.lmtoolbox.ui.components.ToolIntro
import org.lukemech.lmtoolbox.features.servers.mc_server.MinecraftServerFeature
import org.lukemech.lmtoolbox.ui.components.InputField
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import kotlin.coroutines.cancellation.CancellationException

private const val LIST = "list"
private const val NEW = "new"

/**
 * Minecraft servers on the phone: the list of them, a new one (its kind and version, installed from
 * each project's own servers), and a server's page ([ServerPage]): how it's doing, its console, its
 * settings and files. One runs at a time, going on when the page is left (see [ServerRunner]).
 */
@Composable
fun MinecraftServerPage() {
    val context = LocalContext.current
    val feature = MinecraftServerFeature
    // Changed when a server is made or deleted: the list is read again
    var generation by remember { mutableIntStateOf(0) }
    val servers = remember(generation) { Servers.list(context) }
    val running by ServerRunner.state.collectAsState()

    // Back on the page: the running server, or the one opened last
    var screen by rememberSaveable {
        mutableStateOf(
            ServerRunner.state.value?.takeIf { it.isActive }?.serverId
                ?: Installer.state.value?.takeIf { it.isActive }?.let { if (it.existing) it.id else NEW }
                ?: PreferenceHelper.get(context, feature.S_SELECTED, "").takeIf { it.isNotEmpty() && Servers.load(context, it) != null }
                ?: LIST
        )
    }
    fun open(id: String) {
        PreferenceHelper.put(context, feature.S_SELECTED, id)
        screen = id
    }
    fun toList() {
        PreferenceHelper.remove(context, feature.S_SELECTED)
        generation++
        screen = LIST
    }

    BackHandler(enabled = screen != LIST) { toList() }

    when (screen) {
        LIST -> ServerList(servers, running, onOpen = ::open, onNew = { screen = NEW })
        NEW -> NewServer(onBack = ::toList, onCreated = { id -> generation++; open(id) })
        else -> {
            val profile = remember(screen, generation) { Servers.load(context, screen) }
            if (profile == null) {
                LaunchedEffect(Unit) { toList() }
            } else {
                ServerPage(
                    profile,
                    onBack = ::toList,
                    onChanged = { generation++ },
                    onDeleted = ::toList
                )
            }
        }
    }
}

/** The servers made so far, each with how it's doing; or, before the first, what the tool does. */
@Composable
private fun ServerList(servers: List<ServerProfile>, running: ServerRunner.Run?, onOpen: (String) -> Unit, onNew: () -> Unit) {
    StepWithButtons(
        content = {
            val title = stringResource(R.string.fp_McServer)
            if (servers.isEmpty()) {
                PageHeading(title = title, verticalArrangement = Arrangement.Center) {
                    ToolIntro(
                        ImageVector.vectorResource(R.drawable.mc_block), stringResource(R.string.f_Mc_intro_title),
                        listOf(
                            Icons.Default.Extension to stringResource(R.string.f_Mc_intro_1),
                            Icons.Default.Wifi to stringResource(R.string.f_Mc_intro_2),
                            Icons.Default.Public to stringResource(R.string.f_Mc_intro_3),
                        ),
                        stringResource(R.string.f_Mc_intro_tip) + "\n\n" + stringResource(R.string.f_Mc_disclaimer)
                    )
                }
            } else {
                PageHeading(title = title) {
                    Column(verticalArrangement = RowArrangement) {
                        // What the servers take in all while one runs: its run's processes and its tunnels'
                        // (not the app's others, e.g. an encoding meanwhile)
                        // (the last run's while they fold away)
                        val lastRun = remember { arrayOfNulls<ServerRunner.Run>(1) }
                        if (running?.isActive == true) lastRun[0] = running
                        Reveal(visible = running?.isActive == true) {
                            val tunnel by ServerRunner.tunnel.collectAsState()
                            val bore by ServerRunner.bore.collectAsState()
                            val ids = setOfNotNull(
                                lastRun[0]?.taskId, tunnel?.takeIf { it.isActive }?.state?.taskId, bore?.takeIf { it.isActive }?.state?.taskId
                            )
                            ResourceTiles(MinecraftServerFeature, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), ResourceScope.Tasks(ids))
                        }
                        servers.forEach { s ->
                            val run = running?.takeIf { it.serverId == s.id }
                            FeatureButton(
                                title = s.name,
                                subtitle = s.description + " · " + phaseText(run),
                                icon = ImageVector.vectorResource(R.drawable.mc_block),
                                buttonContent = { PhaseDot(run) },
                                onClick = { onOpen(s.id) }
                            )
                        }
                    }
                }
            }
        },
        buttons = {
            BigButton(Icons.Default.Add, stringResource(R.string.f_Mc_new), enabled = true, onClick = onNew)
        }
    )
}

/** How a server is doing in words; [run]: its run, if it's the one running (or last ran). */
@Composable
internal fun phaseText(run: ServerRunner.Run?): String = stringResource(
    when (run?.phase) {
        ServerRunner.Phase.Starting -> R.string.f_Mc_phase_starting
        ServerRunner.Phase.Running -> R.string.f_Mc_phase_running
        ServerRunner.Phase.Stopping -> R.string.f_Mc_phase_stopping
        ServerRunner.Phase.Crashed -> R.string.f_Mc_phase_crashed
        else -> R.string.f_Mc_phase_stopped
    }
)

/** A dot in the color of how it's doing: green running, amber on its way, red crashed, gray stopped. */
@Composable
internal fun PhaseDot(run: ServerRunner.Run?, size: androidx.compose.ui.unit.Dp = 14.dp) {
    val color = when (run?.phase) {
        ServerRunner.Phase.Running -> androidx.compose.ui.graphics.Color(0xFF2E9E4F)
        ServerRunner.Phase.Starting, ServerRunner.Phase.Stopping -> androidx.compose.ui.graphics.Color(0xFFE0A100)
        ServerRunner.Phase.Crashed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Icon(Icons.Default.Circle, contentDescription = null, tint = color, modifier = Modifier.size(size))
}

/**
 * A new server: its name, kind and Minecraft version (as its project offers them), its memory, and
 * Mojang's EULA accepted; then it's installed ([Installer]), which shows here as it goes.
 */
@Composable
private fun NewServer(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val installState by Installer.state.collectAsState()
    // A server being made (not one changing its version)
    val install = installState?.takeIf { !it.existing }
    val defaultName = stringResource(R.string.f_Mc_default_name)
    var name by rememberSaveable { mutableStateOf(install?.draft?.name ?: defaultName) }
    var type by rememberSaveable { mutableStateOf(install?.draft?.type ?: ServerType.Paper) }
    var version by rememberSaveable { mutableStateOf(install?.draft?.version) }
    val maxMemory = remember { Memory.max(context) }
    var memory by rememberSaveable { mutableIntStateOf(install?.draft?.memoryMb ?: Memory.default(context)) }
    var eula by rememberSaveable { mutableStateOf(install != null) }
    // The Java the version needs (Mojang's metadata): null while it's looked up; it runs on the one made for that
    var javaNeeds by remember(version) { mutableStateOf(install?.draft?.takeIf { it.version == version }?.javaNeeds) }
    LaunchedEffect(version) {
        javaNeeds = Installer.javaNeeds(version ?: return@LaunchedEffect)
    }
    val jdk = javaNeeds?.let { Java.forNeeds(context, it) }

    // The kind's versions from its project (with snapshots or not): loading, the list, or why not
    var snapshots by remember { mutableStateOf(snapshotsShown(context)) }
    var versions by remember(type, snapshots) { mutableStateOf<List<String>?>(null) }
    var versionsError by remember(type, snapshots) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(type, retry, snapshots) {
        versionsError = false
        runCatching { Installer.versions(type, snapshots) }
            .onSuccess { list ->
                versions = list
                if (version !in list) version = list.firstOrNull()
            }
            .onFailure { e ->
                // Left (the page, or this kind for another) before they came: nothing failed
                if (e is CancellationException) throw e
                versionsError = true
                // Told in the one place errors are, the bottom bar
                LinuxTasks.reportFailure(MinecraftServerFeature, type.label, "Couldn't get the versions: ${e.message ?: e.javaClass.simpleName}")
            }
    }

    // The loader's versions for it (none for Vanilla), one picked or (null) its recommended
    var loaders by remember(type, version) { mutableStateOf<List<Installer.Loader>?>(null) }
    // Asked for and not answered yet (else, none: Vanilla's, or they couldn't be had: its recommended then)
    var loadersLoading by remember(type, version) { mutableStateOf(type != ServerType.Vanilla) }
    var loader by rememberSaveable(type, version) { mutableStateOf(install?.draft?.takeIf { it.type == type && it.version == version }?.loader) }
    LaunchedEffect(type, version) {
        val v = version ?: return@LaunchedEffect
        loaders = runCatching { Installer.loaders(type, v) }.getOrNull()
        loadersLoading = false
    }

    // Made: straight to it
    LaunchedEffect(install?.stage) {
        val i = install ?: return@LaunchedEffect
        if (i.stage == Installer.Stage.Done) {
            Installer.clear()
            onCreated(i.id)
        }
    }

    val busy = install?.isActive == true
    StepWithButtons(
        content = {
            PageHeading(
                title = stringResource(R.string.f_Mc_new),
                onBack = { if (!busy) { Installer.clear(); onBack() } },
                // As it installs: its stages as steps, under the title
                steps = installSteps(install)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    InputField(
                        value = name,
                        onValueChange = { name = it.take(48) },
                        enabled = !busy,
                        label = { Text(stringResource(R.string.f_Mc_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.f_Mc_type), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ServerType.entries.forEach { t ->
                                FilterChip(
                                    selected = type == t,
                                    enabled = !busy,
                                    onClick = { type = t },
                                    label = { Text(t.label) }
                                )
                            }
                        }
                        Text(
                            stringResource(typeDescription(type)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                }
                // A settings row (as the start page's), as wide as in the settings
                Spacer(modifier = Modifier.height(16.dp))
                VersionSetting(versions, version, versionsError, enabled = !busy, onRetry = { retry++ }) { version = it }
                Spacer(modifier = Modifier.height(6.dp))
                val all = loaders.orEmpty()
                val showLoader = type != ServerType.Vanilla && (loadersLoading || all.isNotEmpty())
                // The kind it was for while it folds away (switched to Vanilla: not "Vanilla version")
                var loaderType by remember { mutableStateOf(type) }
                if (showLoader) loaderType = type
                Reveal(visible = showLoader) { Column {
                    val preferred = Installer.preferred(all)
                    val tag = stringResource(R.string.f_Mc_java_recommended)
                    OptionSetting(
                        title = stringResource(R.string.f_Mc_loader_version, loaderType.label),
                        icon = Icons.Default.Extension,
                        selected = loader ?: preferred?.version,
                        options = all.map { l ->
                            l.version to Installer.loaderLabel(loaderType, l.version) + (if (l == preferred) " · $tag" else "")
                        }.ifEmpty { listOf(null to "–") },
                        enabled = !busy,
                        // Grayed out with a spinner while they load, as the Minecraft versions'
                        loading = loadersLoading,
                        onSelect = { if (!busy) loader = it.takeIf { v -> v != preferred?.version } }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                } }
                SnapshotsSetting(snapshots, enabled = !busy) { snapshots = it }
                Spacer(modifier = Modifier.height(6.dp))
                // Once there's a version to run
                Reveal(visible = version != null) { Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    JavaSetting(javaNeeds)
                } }
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.f_Mc_memory), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text(memoryText(memory), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        MemorySlider(memory, maxMemory, enabled = !busy) { memory = it }
                        Text(
                            stringResource(R.string.f_Mc_memory_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Mojang's terms: the server won't start without them accepted (eula.txt)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { eula = !eula }
                    ) {
                        Checkbox(checked = eula, onCheckedChange = { eula = it }, enabled = !busy)
                        Text(stringResource(R.string.f_Mc_eula), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://aka.ms/MinecraftEULA".toUri())) }
                        }) { Text(stringResource(R.string.f_Mc_eula_read)) }
                    }

                }
            }
        },
        buttons = {
            // How it goes is the steps' bar; a failure, the bottom bar's red line (errors are told there alone)
            BigButton(
                Icons.Default.Download, stringResource(R.string.f_Mc_create),
                enabled = installState?.isActive != true && name.isNotBlank() && version != null && eula && !versionsError &&
                    jdk != null && javaNeeds != null
            ) {
                focusManager.clearFocus()
                Installer.start(context, Installer.Draft(name.trim(), type, version!!, memory, jdk!!, javaNeeds!!, loader))
            }
        }
    )
}

private fun typeDescription(type: ServerType) = when (type) {
    ServerType.Vanilla -> R.string.f_Mc_type_vanilla
    ServerType.Paper -> R.string.f_Mc_type_paper
    ServerType.Fabric -> R.string.f_Mc_type_fabric
    ServerType.Forge -> R.string.f_Mc_type_forge
    ServerType.NeoForge -> R.string.f_Mc_type_neoforge
}

/**
 * The Minecraft version, as the settings' choices are picked (the start page's): the one picked, a
 * tap opening [versions] (newest first) under it; while they load, or when they couldn't be.
 */
@Composable
private fun VersionSetting(
    versions: List<String>?, selected: String?, failed: Boolean, enabled: Boolean,
    onRetry: () -> Unit, onSelect: (String) -> Unit
) {
    val title = stringResource(R.string.f_Mc_version)
    when {
        // Couldn't be had (the bottom bar tells why): again a tap away
        failed -> FeatureButton(
            title = title,
            icon = Icons.Default.Update,
            buttonContent = { TextButton(onClick = onRetry) { Text(stringResource(R.string.f_Mc_retry)) } },
            onClick = onRetry
        )
        // Grayed out, a spinner for its arrow, as the currency converter's lists while they load
        versions == null -> OptionSetting(
            title = title,
            icon = Icons.Default.Update,
            selected = selected.orEmpty(),
            options = listOf(selected.orEmpty() to (selected ?: "–")),
            loading = true,
            onSelect = {}
        )
        else -> {
            val newest = stringResource(R.string.f_Mc_latest)
            OptionSetting(
                title = title,
                icon = Icons.Default.Update,
                selected = selected.orEmpty(),
                options = versions.mapIndexed { i, v -> v to if (i == 0) "$v · $newest" else v },
                enabled = enabled,
                onSelect = { if (enabled) onSelect(it) }
            )
        }
    }
}

/** Mojang's snapshots (and pre-releases) shown in the versions or not, the same for every server. */
internal fun snapshotsShown(context: Context) = PreferenceHelper.get(context, MinecraftServerFeature.S_SNAPSHOTS, false)

/** Whether its versions list Mojang's snapshots too ([snapshotsShown]), remembered as it's switched. */
@Composable
internal fun SnapshotsSetting(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    SwitchButton(
        stringResource(R.string.f_Mc_snapshots), Icons.Default.Science, checked,
        subtitle = stringResource(R.string.f_Mc_snapshots_desc), enabled = enabled
    ) {
        PreferenceHelper.put(context, MinecraftServerFeature.S_SNAPSHOTS, it)
        onChange(it)
    }
}

/**
 * The Java a server runs on, as the installed packages' table shows packages: the one its version is
 * made for ([needs], Mojang's metadata: null while that's looked up, see Java.forNeeds), as it's on the
 * phone or what getting it takes; none (e.g. Java 25's need on x86): that it can't run here. Installing
 * (the tool's installation, for a server made or started), it shows how it's doing as the installation
 * page's packages do: as it was when it started, each step waiting, under way or done.
 */
@Composable
internal fun JavaSetting(needs: Int?) {
    val context = LocalContext.current
    // Again once it's installed or removed
    val tasks by LinuxTasks.tasks.collectAsState()
    val removed by LinuxTasks.extensionsRemoved.collectAsState()
    val jdk = remember(needs) { needs?.let { Java.forNeeds(context, it) } }
    val onPhone = remember(tasks, removed) { LinuxParts.installedParts(context) }
    val states by LinuxTasks.packages.collectAsState()
    val installing = tasks.firstOrNull { it.isActive && it.type == LinuxTask.Type.Extension && it.source == MinecraftServerFeature }
        ?.takeIf { jdk != null && jdk.part in it.packages }
    // As on the phone, or what getting it takes; while it installs, as it was when that started (see LinuxTask.packages)
    val row = jdk?.let { LinuxParts.parts(context)[it.part] }?.let { ext ->
        val there = onPhone.firstOrNull { it.part.name == ext.name }?.takeIf { it.unpacked }
        when {
            installing != null -> PackageTableRow(ext, archive = ext.name in installing.downloads, state = states[ext.name])
            there != null -> PackageTableRow(ext, archive = true, unpacked = true, version = there.version)
            else -> PackageTableRow(ext, archive = !LinuxParts.isExtensionDownloaded(context, ext.name))
        }
    }
    // Kept while it folds away (another version's need looked up)
    val lastRow = remember { arrayOfNulls<PackageTableRow>(1) }
    if (row != null) lastRow[0] = row
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Coffee, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.f_Mc_java), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        }
        // While what it needs is looked up, a bar; then the one it runs on, or that it can't here (the need it
        // was for kept while that folds away)
        val lastNeeds = remember { intArrayOf(0) }
        if (needs != null) lastNeeds[0] = needs
        Reveal(visible = needs == null) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        Reveal(visible = needs != null && row == null) {
            Text(
                stringResource(R.string.f_Mc_java_none, lastNeeds[0]),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Reveal(visible = needs != null && row != null) { lastRow[0]?.let { PackageTable(listOf(it)) } }
    }
}

/** Memory in steps of [Memory.STEP], from [Memory.MIN] to [max]. */
@Composable
internal fun MemorySlider(value: Int, max: Int, enabled: Boolean = true, onChange: (Int) -> Unit) {
    val steps = ((max - Memory.MIN) / Memory.STEP - 1).coerceAtLeast(0)
    Slider(
        value = value.coerceIn(Memory.MIN, max).toFloat(),
        // To its nearest mark, as the thumb shows it (not the one under it)
        onValueChange = { onChange(Memory.snap(it.roundToInt(), max)) },
        valueRange = Memory.MIN.toFloat()..max.coerceAtLeast(Memory.MIN + Memory.STEP).toFloat(),
        steps = steps,
        enabled = enabled
    )
}

/** e.g. "2 GB", "1.5 GB", "768 MB" */
internal fun memoryText(mb: Int): String =
    if (mb >= 1024) (if (mb % 1024 == 0) "${mb / 1024} GB" else "%.1f GB".format(mb / 1024f)) else "$mb MB"

/**
 * An installation (a server made or changed) as its page's steps: one per stage, the current one
 * filling as it goes; what it's at under them, cancelling it (and the loader's installer's log) at their end.
 */
@Composable
internal fun installSteps(i: Installer.Install?): Steps? {
    // Java installing first: its question and its table tell of it, no steps yet
    val active = i?.takeIf { it.isActive && it.stage != Installer.Stage.InstallingJava } ?: return null
    return Steps(
        active.stages.size, active.stages.indexOf(active.stage).coerceAtLeast(0), loadingNext = false, working = true,
        progress = active.progress,
        caption = stageText(active.stage),
        onCancel = Installer::cancel
    )
}

/** What an installation's at, in words. */
@Composable
internal fun stageText(stage: Installer.Stage): String = stringResource(
    when (stage) {
        Installer.Stage.InstallingJava -> R.string.f_Mc_installing_java
        Installer.Stage.BackingUp -> R.string.f_Mc_backing_up
        Installer.Stage.Installing -> R.string.f_Mc_installing_loader
        else -> R.string.f_Mc_downloading
    }
)

/** A section's title, as the settings' (for the server's own pages). */
@Composable
internal fun Section(title: String) {
    SectionTitle(title, modifier = Modifier.padding(top = 8.dp))
}
