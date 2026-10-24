package org.lukemech.lmtoolbox.linux

import android.content.Context
import java.io.File
import org.lukemech.lmtoolbox.linux.LinuxParts.isExtensionUnpacked
import org.lukemech.lmtoolbox.linux.LinuxPaths.linuxDir

/**
 * Which tools use the parts, by their routes, kept with what's unpacked (gone with the core): a part goes once
 * no tool uses it (see LinuxTasks.removeExtension).
 */
object PartUsers {
    /** Which tools use the parts (one part can serve several, e.g. pypkgs): a file each, by its route. */
    private fun usersDir(ctx: Context): File = linuxDir(ctx).child("etc/features")

    /**
     * Tools another tool's installation unlocked (all their parts came with it, e.g. the video encoder with
     * the media downloader's FFmpeg): there to use, but not users ([isUsing]): they keep no part.
     */
    private fun unlockedDir(ctx: Context): File = linuxDir(ctx).child("etc/features-unlocked")

    private fun setMarker(marker: File, on: Boolean) {
        if (on) {
            if (!marker.exists()) marker.apply { parentFile?.mkdirs() }.writeText("")
        } else {
            marker.delete()
        }
    }

    /** Records whether the tool of [route] uses its part (got it, or ran on it); gone with the core. A user is unlocked no more. */
    fun setUsing(context: Context, route: String, using: Boolean) {
        setMarker(usersDir(context).child(route), using)
        if (using) setUnlocked(context, route, false)
    }

    /** Records whether the tool of [route] is unlocked by another's installation (see [unlockedDir]). */
    fun setUnlocked(context: Context, route: String, unlocked: Boolean) = setMarker(unlockedDir(context).child(route), unlocked)

    /** Whether the tool of [route] is recorded as unlocked (its parts there or not). */
    fun isUnlocked(context: Context, route: String): Boolean = unlockedDir(context).child(route).exists()

    /** Whether the tool of [route] is recorded as using its part (unpacked or not: e.g. its part was replaced). */
    fun isUser(context: Context, route: String): Boolean = usersDir(context).child(route).exists()

    /**
     * Whether the tool of [route] uses [extension]: it's unpacked, and the tool got it or ran on it
     * (an installation from before tools were recorded: every tool on it does).
     */
    fun isUsing(context: Context, route: String, extension: String): Boolean =
        isExtensionUnpacked(context, extension) && usersDir(context).let { !it.exists() || it.child(route).exists() }
}
