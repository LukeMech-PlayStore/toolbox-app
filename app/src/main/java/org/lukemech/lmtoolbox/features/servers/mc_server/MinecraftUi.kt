package org.lukemech.lmtoolbox.features.servers.mc_server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.lukemech.lmtoolbox.helpers.FeatureUi
import org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server.MinecraftServerPage
import org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server.SnapshotsSetting
import org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server.snapshotsShown

/** The Minecraft server's pages (see [MinecraftServerFeature]); its settings are each server's own, on its page,
 * but snapshots offered or not (as on the page making one, and each server's options). */
@Suppress("unused") // Loaded by its name, MinecraftServerFeature.uiClass
object MinecraftUi : FeatureUi {
    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        MinecraftServerPage()
    }

    override val hasSettingsContent get() = true

    @Composable
    override fun SettingsContent() {
        val context = LocalContext.current
        var snapshots by remember { mutableStateOf(snapshotsShown(context)) }
        SnapshotsSetting(snapshots, enabled = true) { snapshots = it }
    }

    /** Its servers' files go: the one running stops at once (what it writes as it dies goes after them), an installation too. */
    override fun onClearData() {
        Installer.cancel()
        ServerRunner.kill(delete = true)
    }
}
