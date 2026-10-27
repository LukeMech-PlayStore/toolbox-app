package org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerProfile
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerRunner
import org.lukemech.lmtoolbox.features.servers.mc_server.Servers
import org.lukemech.lmtoolbox.linux.InstallResult
import org.lukemech.lmtoolbox.linux.parts.Bore
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.features.servers.mc_server.MinecraftServerFeature
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.BoreServerSettings
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.PartPackage

/**
 * bore's tunnel, turned on or off: on, bore is installed (its row as the Java's table has them) and its
 * settings unfold, and it starts if the server runs; off, they fold away, its tunnel stopped (bore
 * uninstalled once no server has it on, see ServerRunner.pruneBore). Nothing to set up: `bore.pub`, or the user's own bore server (its secret).
 */
@Composable
internal fun BoreSection(profile: ServerProfile, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember(profile.id) { mutableStateOf(profile.boreEnabled) }
    fun save(on: Boolean) {
        enabled = on
        Servers.load(context, profile.id)?.let { Servers.save(context, it.copy(boreEnabled = on)) }
        onChanged()
    }
    Column(verticalArrangement = RowArrangement) {
        SwitchButton(stringResource(R.string.f_Srv_bore_enable), Icons.Default.SettingsEthernet, enabled) { on ->
            save(on)
            // At once, the server running or not: on, installed (asked first; not wanted, it's off again) and
            // started if it runs; off, stopped and uninstalled
            if (on) scope.launch { if (ServerRunner.boreOn(context, profile.id) == InstallResult.Declined) save(false) }
            else ServerRunner.boreOff(context, profile.id)
        }
        RevealSection(enabled) {
            PartPackage(MinecraftServerFeature, Bore.PART)
            BoreSettings(profile, onChanged)
        }
        // What it's for: under its switch, its settings unfolding above it
        Note(stringResource(R.string.f_Mc_bore_desc))
    }
}

/** bore's: the bore server (bore.pub, or one's own) and its secret. */
@Composable
private fun BoreSettings(profile: ServerProfile, onChanged: () -> Unit) {
    val context = LocalContext.current
    val bore = remember(profile.id) { ServerRunner.boreOf(context, profile) }
    var server by remember(profile.id) { mutableStateOf(profile.boreServer) }
    BoreServerSettings(bore, server) {
        server = it
        // Another server: another port
        Servers.load(context, profile.id)?.let { p -> Servers.save(context, p.copy(boreServer = server, borePort = 0)) }
        onChanged()
    }
}
