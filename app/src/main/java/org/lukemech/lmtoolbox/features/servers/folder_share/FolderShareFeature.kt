package org.lukemech.lmtoolbox.features.servers.folder_share

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.runtime.Composable
import java.io.File
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.ResourceTile
import org.lukemech.lmtoolbox.linux.parts.Bore
import org.lukemech.lmtoolbox.linux.parts.Tunnel
import org.lukemech.lmtoolbox.ui.pages.features.servers.folder_share.FolderSharePage

/**
 * Folder share: a folder picked on the phone (the system's picker, kept allowed) served over HTTP, its own
 * server in the app (FolderShareServer, no Linux part), for anyone on the same network, the tailnet, or from
 * anywhere through bore's or a Cloudflare quick tunnel (installed when turned on, as the Minecraft server's).
 */
object FolderShareFeature : AppFeature {
    override val route = "folder_share"
    override val titleStringResId: Int = R.string.fp_FolderShare
    override val keywordsResId: Int = R.string.fk_FolderShare
    override val descriptionResId: Int = R.string.fd_FolderShare
    override val category = FeatureCategory.Servers
    override val icon @Composable get() = Icons.Default.FolderShared
    // Its tunnels, once turned on (ServerTunnels)
    override val extraExtensions = listOf(Tunnel.PART, Bore.PART)
    // What it sends and gets is the network's
    override val resourceTiles = listOf(ResourceTile.Network)

    /** The folder shared (its tree's URI, allowed for good). */
    const val S_FOLDER = "FolderShare_folder"
    const val S_PORT = "FolderShare_port"
    /** Others may put files into it (off: just read). */
    const val S_UPLOADS = "FolderShare_uploads"
    /** Asked for a name and password (HTTP Basic), for all of it. */
    const val S_AUTH = "FolderShare_auth"
    const val S_USER = "FolderShare_user"
    const val S_PASSWORD = "FolderShare_password"
    /** Stopped after this long with nobody asking for anything; 0: never. */
    const val S_IDLE = "FolderShare_idleMinutes"
    const val S_BORE = "FolderShare_bore"
    /** The bore server; empty: bore.pub. */
    const val S_BORE_SERVER = "FolderShare_boreServer"
    /** The bore server's port it had last, asked for again (the same address). */
    const val S_BORE_PORT = "FolderShare_borePort"
    /** A Cloudflare quick tunnel. */
    const val S_CLOUDFLARE = "FolderShare_cloudflare"

    const val DEFAULT_PORT = 8080

    override val preferenceKeys = listOf(
        S_FOLDER, S_PORT, S_UPLOADS, S_AUTH, S_USER, S_PASSWORD, S_IDLE, S_BORE, S_BORE_SERVER, S_BORE_PORT, S_CLOUDFLARE
    )

    /** Its own files: bore's secret. */
    fun dir(context: Context) = File(context.applicationContext.filesDir, route)

    override fun dataFiles(context: Context) = listOf(dir(context))

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        FolderSharePage()
    }

    /** A tunnel's part while its tunnel is turned on (see ServerTunnels.prune). */
    override fun usesExtraExtension(context: Context, name: String) = when (name) {
        Tunnel.PART -> ShareSettings.load(context).cloudflare
        Bore.PART -> ShareSettings.load(context).bore
        else -> false
    }

    /** Its settings go: the share stops first, the folder it was allowed to read let go. */
    override fun onClearData() {
        FolderShare.stop()
        FolderShare.app?.let { ShareSettings.forgetFolder(it) }
    }
}
