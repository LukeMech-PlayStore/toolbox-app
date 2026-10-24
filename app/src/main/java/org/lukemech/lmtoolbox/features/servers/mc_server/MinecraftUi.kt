package org.lukemech.lmtoolbox.features.servers.mc_server

import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.helpers.FeatureUi
import org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server.MinecraftServerPage

/** The Minecraft server's pages (see [MinecraftServerFeature]); its settings are each server's own, on its page. */
@Suppress("unused") // Loaded by its name, MinecraftServerFeature.uiClass
object MinecraftUi : FeatureUi {
    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        MinecraftServerPage()
    }

    /** Its servers' files go: the one running stops at once (what it writes as it dies goes after them), an installation too. */
    override fun onClearData() {
        Installer.cancel()
        ServerRunner.kill(delete = true)
    }
}
