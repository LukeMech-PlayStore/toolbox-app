package org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.mc_server.Installer
import org.lukemech.lmtoolbox.features.servers.mc_server.MinecraftServerFeature
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerProfile
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerRunner
import org.lukemech.lmtoolbox.features.servers.mc_server.Servers
import org.lukemech.lmtoolbox.features.servers.mc_server.Transfers
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.ErrorText
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.StepWithButtons
import org.lukemech.lmtoolbox.ui.components.Steps
import org.lukemech.lmtoolbox.ui.components.InputField

private enum class Tab(val title: Int, val icon: ImageVector) {
    Status(R.string.f_Mc_tab_status, Icons.Default.Dashboard),
    Console(R.string.f_Mc_tab_console, Icons.Default.Terminal),
    Options(R.string.f_Mc_tab_options, Icons.Default.Tune),
}

/**
 * A server's page, its tabs at the bottom: how it's doing (what it takes, who's on, how to join it),
 * its console, and its options (settings, the tunnel, its files). Starting and stopping it under them.
 */
@Composable
internal fun ServerPage(profile: ServerProfile, onBack: () -> Unit, onChanged: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.Status) }
    // The options opened at the tunnel's (from "from anywhere", while it isn't set up)
    var toTunnel by remember { mutableStateOf(false) }
    // Changed as what waits for a restart is dropped: the options read again
    var discarded by remember { mutableIntStateOf(0) }
    val state by ServerRunner.state.collectAsState()
    val run = state?.takeIf { it.serverId == profile.id }
    val active = run?.isActive == true
    // Another server running: this one can't start meanwhile
    val other = state?.takeIf { it.isActive && it.serverId != profile.id }
    // Its version changing: it can't start meanwhile
    val install by Installer.state.collectAsState()
    val changing = install?.let { it.existing && it.id == profile.id && it.isActive } == true
    val transferState by Transfers.state.collectAsState()
    var command by rememberSaveable { mutableStateOf("") }

    fun send() {
        val c = command
        if (c.isBlank()) return
        command = ""
        scope.launch {
            // Not sent: told in the one place errors are, the bottom bar
            ServerRunner.console(c).onFailure {
                LinuxTasks.reportFailure(MinecraftServerFeature, profile.name, "Couldn't send /$c: ${it.message ?: it.javaClass.simpleName}")
            }
        }
    }

    StepWithButtons(
        content = {
            // Its version changing, or a transfer of its files: as steps under the title, wherever it's scrolled to
            val change = install?.takeIf { it.existing && it.id == profile.id && it.isActive }
            val transfer = transferState?.takeIf { it.serverId == profile.id && it.isActive }
            PageHeading(
                title = profile.name, onBack = onBack,
                steps = installSteps(change) ?: transfer?.let { t ->
                    Steps(
                        1, 0, loadingNext = false, working = true,
                        progress = t.total?.takeIf { it > 0 }?.let { (t.bytes.toFloat() / it).coerceIn(0f, 1f) },
                        caption = transferText(t.kind),
                        detail = transferAmount(t)
                    )
                }
            ) {
                when (tab) {
                    Tab.Status -> StatusTab(profile, run, onTunnelSettings = { tab = Tab.Options; toTunnel = true })
                    Tab.Console -> ConsoleTab(run) { c -> command = c; send() }
                    // Read again once what waited for a restart is dropped (its settings as they were)
                    Tab.Options -> key(discarded) { OptionsTab(profile, run, onChanged, onDeleted, toTunnel) { toTunnel = false } }
                }
            }
        },
        buttons = {
            when (tab) {
                Tab.Console -> InputField(
                    value = command,
                    onValueChange = { command = it },
                    enabled = run?.phase == ServerRunner.Phase.Running,
                    placeholder = { Text(stringResource(R.string.f_Mc_command)) },
                    prefix = { Text("/") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = ::send, enabled = command.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Default.Send, contentDescription = stringResource(R.string.f_Mc_send))
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Tab.Status -> {
                    ErrorText(other?.let { stringResource(R.string.f_Mc_other_running, it.name) })
                    if (active) {
                        BigButton(Icons.Default.Stop, stringResource(R.string.f_Mc_stop), enabled = run.phase != ServerRunner.Phase.Stopping) {
                            ServerRunner.stop()
                        }
                    } else {
                        BigButton(Icons.Default.PlayArrow, stringResource(R.string.f_Mc_start), enabled = other == null && !changing) {
                            Servers.load(context, profile.id)?.let { ServerRunner.start(context, it) }
                        }
                    }
                }
                // What waits for a restart, over the tabs
                Tab.Options -> {
                    val pending = run?.takeIf { active && it.pending.isNotEmpty() }
                    // The last one while it folds away (restarted, or dropped)
                    val last = remember { arrayOfNulls<ServerRunner.Run>(1) }
                    if (pending != null) last[0] = pending
                    Reveal(visible = pending != null) {
                        last[0]?.let { r ->
                            PendingBar(profile, r) {
                                // A field being edited saved first (its text, as focus leaves it), so it's dropped
                                // with the rest: else it saved itself as the tab is made again, waiting once more
                                focusManager.clearFocus()
                                ServerRunner.discard(context, profile.id)
                                discarded++
                                onChanged()
                            }
                        }
                    }
                }
            }
            // The tabs, under it all
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Tab.entries.forEach { t ->
                    SegmentedButton(
                        selected = tab == t,
                        onClick = { tab = t; focusManager.clearFocus() },
                        shape = SegmentedButtonDefaults.itemShape(t.ordinal, Tab.entries.size),
                        icon = { Icon(t.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    ) { Text(stringResource(t.title), maxLines = 1) }
                }
            }
        }
    )
}
