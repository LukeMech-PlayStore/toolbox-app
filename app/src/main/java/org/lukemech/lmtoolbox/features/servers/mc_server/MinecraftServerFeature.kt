package org.lukemech.lmtoolbox.features.servers.mc_server

import android.content.Context
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.ModuleFeature
import org.lukemech.lmtoolbox.helpers.ResourceTile
import org.lukemech.lmtoolbox.linux.parts.Bore
import org.lukemech.lmtoolbox.linux.parts.Tunnel

/**
 * Minecraft server: on the subsystem's core, the Java version each server's Minecraft is made for (jdk8, jdk17, jdk21, jdk25, see
 * Java: installed when a server needs it) and, turned on, Cloudflare's tunnel (cloudflared, see Tunnel)
 * or bore's (bore, see Bore): installed then. Its code is the app's (no part of its own to come with).
 */
object MinecraftServerFeature : ModuleFeature(
    route = "mc_server",
    titleStringResId = R.string.fp_McServer,
    keywordsResId = R.string.fk_McServer,
    descriptionResId = R.string.fd_McServer,
    category = FeatureCategory.Servers,
    moduleIconRes = R.drawable.mc_block,
    linuxExtensions = listOf("core"),
    // Java's versions (Java.JDKS); the tunnels', once a server's is turned on (Tunnel.PART, Bore.PART)
    extraExtensions = listOf("jdk8", "jdk17", "jdk21", "jdk25", "cloudflared", "bore"),
    uiClass = "org.lukemech.lmtoolbox.features.servers.mc_server.MinecraftUi",
    // The servers (their worlds, mods, settings), each in a folder of its own
    scriptDir = "Minecraft",
    // Its players' traffic (and the tunnels') is the network's
    resourceTiles = listOf(ResourceTile.Cpu, ResourceTile.Ram, ResourceTile.Network),
) {
    /** The server opened last, back to it when the page is opened again. */
    const val S_SELECTED = "McServer_selected"

    /** Mojang's snapshots (and pre-releases) offered along with its releases. */
    const val S_SNAPSHOTS = "McServer_snapshots"

    override val preferenceKeys = listOf(S_SELECTED, S_SNAPSHOTS)

    /** A tunnel's part while a server has that tunnel turned on (see ServerRunner.pruneTunnel, pruneBore). */
    override fun usesExtraExtension(context: Context, name: String) = when (name) {
        Tunnel.PART -> Servers.list(context).any { it.tunnelEnabled }
        Bore.PART -> Servers.list(context).any { it.boreEnabled }
        else -> false
    }
}
