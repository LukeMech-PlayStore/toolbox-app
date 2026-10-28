package org.lukemech.lmtoolbox.features.servers.folder_share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import org.lukemech.lmtoolbox.features.servers.folder_share.FolderShareFeature as F
import org.lukemech.lmtoolbox.helpers.PreferenceHelper

/**
 * How the folder is shared, as set on its page (the app's preferences, see FolderShareFeature's keys). What
 * the server reads as it answers (uploads, the password) counts at once; the folder and the port with the next
 * start.
 */
data class ShareSettings(
    val folder: Uri?,
    val port: Int = F.DEFAULT_PORT,
    val uploads: Boolean = false,
    val auth: Boolean = false,
    val user: String = "",
    val password: String = "",
    val idleMinutes: Int = 0,
    val bore: Boolean = false,
    val boreServer: String = "",
    val borePort: Int = 0,
    val cloudflare: Boolean = false,
) {
    /** Asked for a password: on, with one set (on without one, nobody would get in). */
    val protected get() = auth && password.isNotEmpty()

    companion object {
        /** The ports it can listen on: an app can't have those under 1024. */
        val PORTS = 1024..65535

        /** Choices of [idleMinutes], in minutes (0: never). */
        val IDLE_CHOICES = listOf(0, 15, 30, 60, 120)

        fun load(context: Context): ShareSettings {
            fun <T> get(key: String, default: T) = PreferenceHelper.get(context, key, default)
            return ShareSettings(
                folder = get(F.S_FOLDER, "").takeIf { it.isNotEmpty() }?.toUri(),
                port = get(F.S_PORT, F.DEFAULT_PORT).takeIf { it in PORTS } ?: F.DEFAULT_PORT,
                uploads = get(F.S_UPLOADS, false),
                auth = get(F.S_AUTH, false),
                user = get(F.S_USER, ""),
                password = get(F.S_PASSWORD, ""),
                idleMinutes = get(F.S_IDLE, 0),
                bore = get(F.S_BORE, false),
                boreServer = get(F.S_BORE_SERVER, ""),
                borePort = get(F.S_BORE_PORT, 0),
                cloudflare = get(F.S_CLOUDFLARE, false),
            )
        }

        fun put(context: Context, key: String, value: Any) = PreferenceHelper.put(context, key, value)

        /**
         * [uri] (a tree the system's picker gave) is the folder shared from now on: allowed for good (reading,
         * and writing for uploads), the one before let go.
         */
        fun setFolder(context: Context, uri: Uri) {
            val resolver = context.contentResolver
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { resolver.takePersistableUriPermission(uri, flags) }
                // A provider that grants no writing: reading at least
                .onFailure { runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            load(context).folder?.takeIf { it != uri }?.let { release(context, it) }
            put(context, F.S_FOLDER, uri.toString())
        }

        /** No folder shared anymore: the one there was let go (the tool's data deleted). */
        fun forgetFolder(context: Context) {
            load(context).folder?.let { release(context, it) }
            PreferenceHelper.remove(context, F.S_FOLDER)
        }

        private fun release(context: Context, uri: Uri) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }

        /** Whether the folder can still be read (allowed: it may have been taken back, or the folder deleted). */
        fun isAllowed(context: Context, uri: Uri) =
            context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }
}
