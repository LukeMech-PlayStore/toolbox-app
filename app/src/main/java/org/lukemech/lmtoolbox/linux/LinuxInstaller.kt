package org.lukemech.lmtoolbox.linux

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.lukemech.lmtoolbox.helpers.FeatureDelivery
import org.lukemech.lmtoolbox.linux.LinuxParts.Archive
import org.lukemech.lmtoolbox.linux.LinuxParts.CORE
import org.lukemech.lmtoolbox.linux.LinuxParts.archive
import org.lukemech.lmtoolbox.linux.LinuxParts.extensionLib
import org.lukemech.lmtoolbox.linux.LinuxParts.extensionMarker
import org.lukemech.lmtoolbox.linux.LinuxParts.extensions
import org.lukemech.lmtoolbox.linux.LinuxParts.isExtensionUnpacked
import org.lukemech.lmtoolbox.linux.LinuxParts.outdatedParts
import org.lukemech.lmtoolbox.linux.LinuxParts.parts
import org.lukemech.lmtoolbox.linux.LinuxParts.staleExtensions
import org.lukemech.lmtoolbox.linux.LinuxParts.unpackedVersion
import org.lukemech.lmtoolbox.linux.LinuxPaths.linuxDir
import org.tukaani.xz.XZInputStream

/** Unpacks the parts from their Play modules' archives into the subsystem, updates and deletes them. */
object LinuxInstaller {
    private const val TAG = "LinuxEnv"

    /**
     * Deletes everything unpacked (every package). The scripts next to it stay (installed again when changed, see
     * [LinuxScripts.script]), with what features keep by them, e.g. downloaded exchange rates.
     */
    fun remove(context: Context) {
        linuxDir(context).apply { if (exists()) deleteRecursively() }
    }

    /**
     * Updates what's unpacked to what the app ships: each package unpacked in another version (see
     * [LinuxParts.isUpToDate]), as any package is unpacked ([installExtension]), what it builds on first; the
     * others stay as they are. Returns what it did, a line each ("qrcode: 26.10.14 -> 26.10.21"), for
     * the task's log. Throws on failure; cancellable.
     * Use [LinuxTasks.ensureReady] from UI code, which also tracks it as a task and handles locking.
     */
    suspend fun install(context: Context, onProgress: (Float) -> Unit = {}): List<String> = withContext(Dispatchers.IO) {
        val done = mutableListOf<String>()
        // What the app doesn't ship anymore goes first (by its own file list)
        for (name in staleExtensions(context)) {
            removeExtension(context, name)
            done += "$name: removed, no longer shipped"
            Log.i(TAG, "Removed $name: no longer shipped")
        }
        // Each by its own (old) file list first, then unpacked again in the new version. Not the
        // core, everything is on: it's unpacked over the old one (what's in /root, /tmp stays)
        val outdated = outdatedParts(context).map { it to unpackedVersion(context, it.name) }
        outdated.forEach { (part, _) -> if (part.name != CORE) removeExtension(context, part.name) }
        // By size, as one bar
        val total = outdated.sumOf { it.first.size }.coerceAtLeast(1).toFloat()
        var before = 0L
        for ((part, old) in outdated) {
            try {
                installExtension(context, part.name) { p -> onProgress((before + part.size * p / 100) / total * 100) }
                done += "${part.name}: $old -> ${part.version}"
                Log.i(TAG, "Updated ${part.name}: $old -> ${part.version}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing else can be unpacked without the core
                if (!isExtensionUnpacked(context, CORE)) throw e
                // The rest is fine: the tool gets it again on its next use
                done += "${part.name}: $old -> ${part.version} failed, unpacked again on the tool's next use (${e.message})"
                Log.w(TAG, "Couldn't update ${part.name}", e)
            }
            before += part.size
        }
        onProgress(100f)
        done
    }

    /**
     * Unpacks package [name] from its installed Play module ([FeatureDelivery]): an extension over
     * the core, the core (what every other builds on) first of all, onto nothing but an older core.
     * Throws on failure (leaving nothing of it); cancellable.
     */
    suspend fun installExtension(context: Context, name: String, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        if (name == CORE) {
            // Nothing left of an earlier one that didn't finish counts as unpacked once the core is
            if (!isExtensionUnpacked(context, CORE)) remove(context)
        } else if (!isExtensionUnpacked(context, CORE)) {
            throw IllegalStateException("The Linux subsystem's core isn't unpacked")
        }
        val version = parts(context)[name]?.version ?: throw IllegalStateException("The app ships no $name")
        val archive = archive(context, extensionLib(name)) ?: throw FileNotFoundException("${extensionLib(name)} isn't installed (the $name module)")
        try {
            extract(context, archive, onProgress)
        } catch (e: Exception) {
            removeExtension(context, name)
            throw e
        }
        // Last: a half-unpacked one never counts
        extensionMarker(context, name).apply { parentFile?.mkdirs() }.writeText(version)
    }

    /**
     * Deletes package [name]'s files (its own list of them), but those another unpacked extension
     * lists too (moved to it in a newer version), and folders left empty. The core takes everything
     * with it (see [remove]): every other package builds on it.
     */
    fun removeExtension(context: Context, name: String) {
        if (name == CORE) return remove(context)
        val root = linuxDir(context)
        // First: a half-deleted one never counts as there
        extensionMarker(context, name).delete()
        val list = root.child("etc/extensions/$name.files").takeIf { it.exists() } ?: return
        val others = extensions(context).keys.filter { it != name && isExtensionUnpacked(context, it) }
            .flatMap { root.child("etc/extensions/$it.files").takeIf { f -> f.exists() }?.readLines().orEmpty() }
            .toSet()
        val files = list.readLines().filter { it.isNotBlank() && it !in others }.map { File(root, it) }
        files.forEach { it.delete() }
        // Python's bytecode of them, written when they ran (not in the list)
        files.filter { it.name.endsWith(".py") }.mapNotNull { it.parentFile }.distinct()
            .forEach { File(it, "__pycache__").deleteRecursively() }
        files.flatMap { f -> generateSequence(f.parentFile) { it.parentFile }.takeWhile { it != root }.toList() }
            .distinct()
            .sortedByDescending { it.path.length }
            .forEach { if (it.list()?.isEmpty() == true) it.delete() }
        list.delete()
    }

    /** Bytes of a package handed to tar (so written to disk) by the running extraction, for SystemStats. */
    @Volatile
    var unpackedBytes = 0L
        private set

    /** Unpacks [archive] into the subsystem's folder. Throws on failure; cancellable. */
    private suspend fun extract(context: Context, archive: Archive, onProgress: (Float) -> Unit) {
        unpackedBytes = 0L
        val linuxDir = linuxDir(context)


        linuxDir.mkdirs()
        val totalSize = archive.size.coerceAtLeast(1).toFloat()

        // 1. Prepare the TAR command
        val tarCmd = arrayOf("/system/bin/tar", "-x", "-f", "-", "-C", linuxDir.absolutePath, "--exclude", "dev/*")
        val process = withContext(Dispatchers.IO) {
            Runtime.getRuntime().exec(tarCmd)
        }

        // Drain tar's output so it can't block on a full pipe; its errors say why it failed
        val errors = StringBuffer()
        val errorReader = thread {
            try {
                process.errorStream.bufferedReader().use { r -> r.forEachLine { errors.appendLine(it) } }
            } catch (_: Exception) {}
        }
        fun tarFailure(exitCode: Int, cause: Throwable? = null): IOException {
            errorReader.join(1000)
            return IOException("tar failed (exit code $exitCode) unpacking ${archive.name}\n${errors.toString().trim()}", cause)
        }
        thread {
            try {
                process.inputStream.bufferedReader().use { it.forEachLine { /* log */ } }
            } catch (_: Exception) {}
        }

        // 2. Set up the stream chain
        try {
            withContext(Dispatchers.IO) {
                archive.open().use { fis ->
                    BufferedInputStream(fis).use { bis ->

                        // --- Progress Tracker ---
                        val progressStream = object : FilterInputStream(bis) {
                            private var bytesRead = 0L
                            private var lastUpdate = 0L
                            private val updateThreshold = 1024 * 200 // Update UI every 200KB

                            override fun read(b: ByteArray, off: Int, len: Int): Int {
                                val count = super.read(b, off, len)
                                if (count != -1) updateProgress(count.toLong())
                                return count
                            }

                            override fun read(): Int {
                                val byte = super.read()
                                if (byte != -1) updateProgress(1)
                                return byte
                            }

                            private fun updateProgress(increment: Long) {
                                bytesRead += increment
                                if (bytesRead - lastUpdate > updateThreshold || bytesRead.toFloat() == totalSize) {
                                    val percent = (bytesRead / totalSize) * 100
                                    onProgress(percent.coerceAtMost(99f))
                                    lastUpdate = bytesRead
                                }
                            }
                        }

                        XZInputStream(progressStream).use { xzIn ->
                            val buffer = ByteArray(8192)
                            var n = 0
                            try {
                                process.outputStream.use { stdin ->
                                    while (true) {
                                        currentCoroutineContext().ensureActive() // task canceled
                                        n = xzIn.read(buffer)
                                        if (n < 0) break
                                        stdin.write(buffer, 0, n)
                                        unpackedBytes += n
                                    }
                                }
                            } catch (e: IOException) {
                                // tar quits at the end-of-archive marker, before the zeros padding the archive
                                // after it: then what it didn't take (EPIPE, or "Stream closed") is only those.
                                // Else it quit early, its own error says why
                                if (!process.waitFor(2, TimeUnit.SECONDS)) throw e
                                var rest = buffer.take(n.coerceAtLeast(0)).all { it == 0.toByte() }
                                while (rest) {
                                    n = xzIn.read(buffer)
                                    if (n < 0) break
                                    rest = (0 until n).all { buffer[it] == 0.toByte() }
                                }
                                if (!rest) throw tarFailure(process.exitValue(), e)
                            }
                        }
                    }
                }
            }

            // 3. Wait for TAR to finish
            val exitCode = withContext(Dispatchers.IO) {
                process.waitFor()
            }

            // Exit Code 0 = Success
            // Exit Code 1 = Warnings (e.g. timestamps, usually ignorable on Android)
            // Exit Code > 1 = Fatal Error
            if (exitCode > 1) throw tarFailure(exitCode)

            onProgress(100f)
        } catch (e: Exception) {
            // Ensure process doesn't hang if Java crashes
            process.destroy()
            throw e
        } catch (e: OutOfMemoryError) {
            // The xz dictionary (8 MiB, rootfs/pack.sh) on a heap that can't take it: the installation fails, not the app
            process.destroy()
            throw IOException("Not enough memory to unpack ${archive.name} (the app may use " +
                "${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB)", e)
        }
    }
}
