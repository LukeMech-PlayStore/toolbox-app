package org.lukemech.lmtoolbox.features.servers.mc_server

import org.lukemech.lmtoolbox.R
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.lukemech.lmtoolbox.helpers.deleteTree
import org.lukemech.lmtoolbox.helpers.displayName
import org.lukemech.lmtoolbox.tasks.AppTasks

/**
 * A server's files in and out: its world or all of it as a zip (where the user picked), a world
 * from a zip, and its mods or plugins. One at a time ([state]); they go on when the page is left.
 */
object Transfers {
    /** [AddAddons]: its [Transfer.bytes] and total count jars, not bytes. */
    enum class Kind { ExportWorld, ExportServer, ImportWorld, AddAddons }

    data class Transfer(
        val serverId: String,
        val kind: Kind,
        val bytes: Long = 0,
        val total: Long? = null,
        val done: Boolean = false,
        val error: String? = null,
    ) {
        val isActive get() = !done
    }

    private val _state = MutableStateFlow<Transfer?>(null)
    val state: StateFlow<Transfer?> = _state.asStateFlow()

    /** e.g. "My server-world-2026-10-19.zip" */
    fun exportName(profile: ServerProfile, kind: Kind): String {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val what = if (kind == Kind.ExportWorld) "world" else "server"
        return "${profile.name.replace(Regex("""[\\/:*?"<>|]"""), "_")}-$what-$date.zip"
    }

    /**
     * Zips the world (its folders, see [Servers.worldDirs]) or the whole server (but the app's own
     * files: the tunnel's token) into [target]. A running server saves first and doesn't write meanwhile.
     */
    fun export(context: Context, profile: ServerProfile, kind: Kind, target: Uri) = run(context, profile, kind) {
        val app = context.applicationContext
        val dir = Servers.dir(app, profile.id)
        val roots = if (kind == Kind.ExportWorld) Servers.worldDirs(app, profile) else listOf(dir)
        if (roots.isEmpty()) throw IOException(app.getString(R.string.f_Mc_no_world))
        val running = ServerRunner.isRunning(profile.id)
        if (running) {
            ServerRunner.rcon("save-off")
            ServerRunner.rcon("save-all flush")
            // "save-all flush" answers once it's written, on recent versions; older ones a moment after
            delay(2.seconds)
        }
        try {
            val files = roots.flatMap { root -> root.walkTopDown().filter { it.isFile }.map { root to it } }
                .filterNot { (_, f) -> f.parentFile == dir && f.name in Servers.OWN_FILES }
                .filterNot { (_, f) -> f.name.endsWith(".part") || f.name == "session.lock" }
            _state.update { it?.copy(total = files.sumOf { (_, f) -> f.length() }) }
            app.contentResolver.openOutputStream(target)!!.use { out ->
                ZipOutputStream(out.buffered()).use { zip ->
                    var written = 0L
                    for ((root, file) in files) {
                        currentCoroutineContext().ensureActive()
                        // A world as its folder (world/level.dat), the server as what's in its folder
                        val base = if (kind == Kind.ExportWorld) root.parentFile!! else root
                        zip.putNextEntry(ZipEntry(file.relativeTo(base).invariantSeparatorsPath))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        written += file.length()
                        bytes(written)
                    }
                }
            }
        } finally {
            if (running) ServerRunner.rcon("save-on")
        }
    }

    /**
     * Replaces the server's world (the level-name folder) with the one in [source], a zip with a
     * level.dat (in a folder or not); only while the server is stopped. The old world is deleted.
     */
    fun importWorld(context: Context, profile: ServerProfile, source: Uri) = run(context, profile, Kind.ImportWorld) {
        val app = context.applicationContext
        if (ServerRunner.isRunning(profile.id)) throw IOException(app.getString(R.string.f_Mc_stop_first))
        val dir = Servers.dir(app, profile.id)
        val level = Servers.properties(app, profile.id).get("level-name")?.ifBlank { null } ?: "world"
        val temp = File(dir, ".import").apply { deleteTree(); mkdirs() }
        try {
            _state.update { it?.copy(total = size(app, source)) }
            var read = 0L
            app.contentResolver.openInputStream(source)!!.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val entry = zip.nextEntry ?: break
                        val out = File(temp, entry.name)
                        // Nothing outside the folder (no ../ in a path), whatever the zip says
                        if (!out.canonicalPath.startsWith(temp.canonicalPath + File.separator)) continue
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zip.copyTo(it) }
                            read += entry.compressedSize.coerceAtLeast(0)
                            bytes(read)
                        }
                    }
                }
            }
            // The world is the folder with level.dat (the zip's top, or a folder in it)
            val world = temp.walkTopDown().maxDepth(3).firstOrNull { it.isDirectory && File(it, "level.dat").exists() }
                ?: throw IOException(app.getString(R.string.f_Mc_import_no_world))
            val target = File(dir, level)
            target.deleteTree()
            if (!world.renameTo(target)) throw IOException("Couldn't move the world in place")
            // Another world: its seed is told again on the next start
            Servers.setSeed(app, profile.id, null)
        } finally {
            temp.deleteTree()
        }
    }

    /** The transfer's task: it goes on as the page is left, in the bottom bar and the notification with how far it is. */
    private var task: Long? = null

    private fun run(context: Context, profile: ServerProfile, kind: Kind, block: suspend () -> Unit) {
        if (_state.value?.isActive == true) return
        _state.value = Transfer(profile.id, kind)
        AppTasks.launch(context, MinecraftServerFeature, profile.name, onStart = { task = it }) {
            try {
                block()
                _state.update { it?.copy(done = true) }
            } catch (e: Exception) {
                // Told as the task's failure, in the one place errors are, the bottom bar
                _state.update { it?.copy(done = true, error = e.message ?: e.javaClass.simpleName) }
                throw e
            }
        }
    }

    /** [n] bytes of it copied, of its total (if known). */
    private fun bytes(n: Long) {
        _state.update { it?.copy(bytes = n) }
        val total = _state.value?.total?.takeIf { it > 0 }
        task?.let { AppTasks.setProgress(it, total?.let { t -> n * 100f / t }) }
    }

    // Mods (a loader's) and plugins (Paper's): jars in their folder

    fun addonsDir(context: Context, profile: ServerProfile): File? = profile.type.addons?.let { File(Servers.dir(context, profile.id), it) }

    /** Its mods or plugins, by name. */
    fun addons(context: Context, profile: ServerProfile): List<File> =
        addonsDir(context, profile)?.listFiles { f -> f.isFile && (f.name.endsWith(".jar") || f.name.endsWith(".jar.disabled")) }
            .orEmpty().sortedBy { it.name.lowercase() }

    /**
     * Copies the jars picked into its mods or plugins (their own names), one after another as its
     * [state] tells; returns how many (none while another transfer runs).
     */
    suspend fun addAddons(context: Context, profile: ServerProfile, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val dir = addonsDir(context, profile)?.apply { mkdirs() } ?: return@withContext 0
        if (_state.value?.isActive == true) return@withContext 0
        _state.value = Transfer(profile.id, Kind.AddAddons, total = uris.size.toLong())
        try {
            uris.withIndex().count { (i, uri) ->
                runCatching {
                    val name = displayName(context, uri)?.replace(Regex("""[\\/:*?"<>|]"""), "_")
                        ?.takeIf { it.endsWith(".jar", ignoreCase = true) } ?: return@runCatching false
                    context.contentResolver.openInputStream(uri)!!.use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
                    true
                }.getOrDefault(false).also { _ -> _state.update { it?.copy(bytes = i + 1L) } }
            }
        } finally {
            _state.update { it?.copy(done = true) }
        }
    }

    /** Switches a mod or plugin off (the loader skips .jar.disabled) or on again; returns its new file. */
    fun toggle(file: File): File {
        val target = if (file.name.endsWith(".disabled")) File(file.parentFile, file.name.removeSuffix(".disabled"))
        else File(file.parentFile, file.name + ".disabled")
        return if (file.renameTo(target)) target else file
    }

    private fun size(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
    }.getOrNull()
}
