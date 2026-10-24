package org.lukemech.lmtoolbox.helpers

import java.io.File
import java.nio.file.Files

/**
 * Deletes this file or folder with all under it, as [File.deleteRecursively] does, but what goes
 * meanwhile is fine: another deleting it too, or a program still writing as it's stopped (e.g. a server
 * a tool's data goes with). deleteRecursively's walk fails then (an AssertionError, crashing the app).
 * Links aren't followed, only deleted. Whether it's gone.
 */
fun File.deleteTree(): Boolean {
    if (isDirectory && !Files.isSymbolicLink(toPath())) listFiles()?.forEach { it.deleteTree() }
    return delete() || !exists()
}
