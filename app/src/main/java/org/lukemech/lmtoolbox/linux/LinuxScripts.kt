package org.lukemech.lmtoolbox.linux

import android.content.Context
import java.io.File
import org.lukemech.lmtoolbox.linux.LinuxPaths.localBinDir

/**
 * The tools' scripts (their module's assets, features/<scriptDir>/), installed into the subsystem whenever one runs,
 * each time as the app ships it; what no tool has anymore goes with the subsystem's update ([removeStale]).
 */
object LinuxScripts {
    /** Where the scripts of features/[feature] (assets) go, along with what they store there. */
    fun scriptDir(context: Context, feature: String): File = localBinDir(context).child("features/$feature")

    /**
     * Installs a script shipped in assets (e.g. "features/qr_gen/gen.py") into the environment,
     * rewriting it only when it changed, and returns its path (valid inside the guest too).
     */
    fun script(context: Context, asset: String): String {
        val target = localBinDir(context).child(asset)
        val content = context.assets.open(asset).use { it.readBytes() }
        if (!target.exists() || !target.readBytes().contentEquals(content)) {
            target.parentFile?.mkdirs()
            target.writeBytes(content)
        }
        return target.absolutePath
    }

    /**
     * Deletes the script folders no tool has anymore ([kept]: the tools' scriptDirs), e.g. one an earlier version
     * named otherwise, with what was kept in them; on the subsystem's update (see LinuxTasks). Their names.
     */
    fun removeStale(context: Context, kept: Set<String>): List<String> = removeStale(localBinDir(context).child("features"), kept)

    internal fun removeStale(features: File, kept: Set<String>): List<String> =
        features.listFiles { f -> f.isDirectory && f.name !in kept }.orEmpty()
            .filter { it.deleteRecursively() }
            .map { it.name }
}
