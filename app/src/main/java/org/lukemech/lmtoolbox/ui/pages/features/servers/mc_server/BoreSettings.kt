package org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.mc_server.Network
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerProfile
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerRunner
import org.lukemech.lmtoolbox.features.servers.mc_server.Servers
import org.lukemech.lmtoolbox.linux.InstallResult
import org.lukemech.lmtoolbox.linux.parts.Bore
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import org.lukemech.lmtoolbox.ui.components.RowArrangement

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
        SwitchButton(stringResource(R.string.f_Mc_bore_enable), Icons.Default.SettingsEthernet, enabled, subtitle = stringResource(R.string.f_Mc_bore_enable_desc)) { on ->
            save(on)
            // At once, the server running or not: on, installed (asked first; not wanted, it's off again) and
            // started if it runs; off, stopped and uninstalled
            if (on) scope.launch { if (ServerRunner.boreOn(context, profile.id) == InstallResult.Declined) save(false) }
            else ServerRunner.boreOff(context, profile.id)
        }
        RevealSection(enabled) {
            PartPackage(Bore.PART)
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
    var hasSecret by remember(profile.id) { mutableStateOf(bore.hasSecret) }

    Column(verticalArrangement = RowArrangement) {
        TextSetting(stringResource(R.string.f_Mc_bore_server), server.ifEmpty { Bore.SERVER }, Icons.Default.Dns, subtitle = stringResource(R.string.f_Mc_bore_server_desc)) {
            // bore.pub typed: as nothing typed
            server = Network.hostnameOf(it).takeIf { h -> h != Bore.SERVER }.orEmpty()
            // Another server: another port
            Servers.load(context, profile.id)?.let { p -> Servers.save(context, p.copy(boreServer = server, borePort = 0)) }
            onChanged()
        }
        // One's own server's (bore server --secret): as the tunnel's token, hidden as it's typed
        Reveal(visible = server.isNotEmpty()) {
            var editing by remember { mutableStateOf(false) }
            FeatureButton(
                title = stringResource(R.string.f_Mc_bore_secret),
                subtitle = stringResource(if (hasSecret) R.string.f_Mc_token_saved else R.string.f_Mc_bore_secret_desc),
                icon = Icons.Default.Key,
                buttonContent = if (hasSecret) {
                    {
                        IconButton(onClick = {
                            bore.setSecret(null)
                            hasSecret = false
                        }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.f_Mc_token_remove), tint = MaterialTheme.colorScheme.error) }
                    }
                } else null,
                onClick = { editing = true }
            )
            if (editing) EditDialog(
                stringResource(R.string.f_Mc_bore_secret), "", stringResource(R.string.f_Mc_bore_secret_desc), KeyboardType.Password,
                secret = true, valid = { it.isNotBlank() }, onDismiss = { editing = false }
            ) {
                editing = false
                bore.setSecret(it)
                hasSecret = bore.hasSecret
            }
        }
    }
}
