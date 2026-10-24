package org.lukemech.lmtoolbox.linux

import android.content.Context
import java.io.File

/** Where the subsystem lives in the app's files: next to files/, as Termux's prefix would be. */
internal object LinuxPaths {
    fun prefixDir(ctx: Context): File = ctx.filesDir.parentFile!!

    fun localDir(ctx: Context): File = prefixDir(ctx).child("local")
    fun linuxDir(ctx: Context): File = localDir(ctx).child("linux")
    fun localBinDir(ctx: Context): File {
        return localDir(ctx).child("bin").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
    }
    fun tempDir(ctx: Context): File {
        val tmp = prefixDir(ctx).child("tmp")
        if (!tmp.exists()) {
            tmp.mkdir()
        }
        return tmp
    }
}

/** [fileName] in this folder. */
internal fun File.child(fileName: String): File = File(this, fileName)
