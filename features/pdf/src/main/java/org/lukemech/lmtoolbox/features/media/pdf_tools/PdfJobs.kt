package org.lukemech.lmtoolbox.features.media.pdf_tools

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lukemech.lmtoolbox.features.pdf.R
import org.lukemech.lmtoolbox.helpers.OutputFolder
import org.lukemech.lmtoolbox.helpers.SavedFile
import org.lukemech.lmtoolbox.helpers.displayName
import org.lukemech.lmtoolbox.helpers.safeFileName
import org.lukemech.lmtoolbox.helpers.saveToMediaStore
import org.lukemech.lmtoolbox.linux.LinuxTasks

/** A PDF being worked on: where it's from, its name, its copy qpdf and Ghostscript read, and what's in it. */
data class PdfFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val copy: File,
    val size: Long,
    /** Null while it can't be opened without its [password]. */
    val pages: Int?,
    /** With a password or restrictions (see [PdfOptions.actions]). */
    val encrypted: Boolean,
    val password: String? = null,
) {
    val needsPassword get() = pages == null
    val baseName get() = name.substringBeforeLast('.').ifBlank { name }
    val input get() = PdfInput(copy.absolutePath, password)
}

/**
 * Runs qpdf and Ghostscript: [open] copies PDFs in and reads what's in them, [start] does what's picked with
 * them, which goes on (shown in the bottom bar) when the page is left and ends up in Downloads or the folder
 * picked. One at a time, in [state].
 */
object PdfJobs {
    const val MIME_TYPE = "application/pdf"

    data class Job(
        val files: List<PdfFile>,
        val options: PdfOptions,
        val fileName: String,
        val taskId: Long? = null,
        val phase: Phase = Phase.Starting,
        /** 0..1 (Ghostscript's pages), null while unknown or for qpdf's (moments). */
        val fraction: Float? = null,
        val saved: List<SavedFile> = emptyList(),
        /** The files' size, and what was made of them: shown for a compression. */
        val sizeBefore: Long = files.sumOf { it.size },
        val sizeAfter: Long = 0,
    ) {
        val isActive get() = phase == Phase.Starting || phase == Phase.Working || phase == Phase.Saving
    }

    enum class Phase { Starting, Working, Saving, Done, Failed, Cancelled }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ids = AtomicLong()

    /**
     * The open files, the new name and what's picked: back to them after the page was left, or redrawn afresh
     * (e.g. back from the file picker). No [options] yet: the defaults.
     */
    var files: List<PdfFile> = emptyList()
    var fileName: String = ""
    var options: PdfOptions? = null

    private val _state = MutableStateFlow<Job?>(null)
    val state: StateFlow<Job?> = _state.asStateFlow()

    private fun inputDir(context: Context) = File(PdfToolsFeature.cacheDir(context), "in")
    private fun workDir(context: Context) = File(PdfToolsFeature.cacheDir(context), "work")
    private fun outputDir(context: Context) = File(PdfToolsFeature.cacheDir(context), "out")

    /**
     * Copies each of [uris] in (qpdf reads files) and reads what's in it: those it can't read are left out, the
     * bottom bar telling why. Empty when none could be, or the subsystem isn't ready.
     */
    suspend fun open(context: Context, uris: List<Uri>): List<PdfFile> = buildList {
        for (uri in uris) {
            val name = displayName(context, uri) ?: "document.pdf"
            val id = ids.incrementAndGet()
            // A plain name keeps odd characters out of its commands
            val copy = withContext(Dispatchers.IO) {
                runCatching {
                    File(inputDir(context).apply { mkdirs() }, "$id.pdf").also { file ->
                        context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                    }
                }.getOrNull()
            }
            if (copy == null) {
                // Not a command's: told in the one place errors are, the bottom bar
                LinuxTasks.reportFailure(PdfToolsFeature, name, "Couldn't read the file")
                continue
            }
            val result = LinuxTasks.run(context, PdfCommands.inspect(PdfInput(copy.absolutePath)), PdfToolsFeature)
            if (result.isEnvNotReady) {
                copy.delete()
                break
            }
            val inspection = PdfCommands.parseInspection(result.stdout)
            if (inspection == null) {
                copy.delete()
                LinuxTasks.reportFailure(PdfToolsFeature, name, "Not a PDF qpdf can read", result.output)
                continue
            }
            add(PdfFile(id, uri, name, copy, copy.length(), inspection.pages, inspection.encrypted))
        }
    }

    /** [file] opened with [password]: with its pages, or null if that's not its password. */
    suspend fun unlock(context: Context, file: PdfFile, password: String): PdfFile? {
        val result = LinuxTasks.run(context, PdfCommands.inspect(PdfInput(file.copy.absolutePath, password)), PdfToolsFeature)
        val pages = PdfCommands.parseInspection(result.stdout)?.takeIf { !it.needsPassword }?.pages ?: return null
        return file.copy(pages = pages, password = password)
    }

    /** Deletes the copies of [gone] (closed files, not being read now). */
    fun close(gone: List<PdfFile>) = gone.forEach { it.copy.delete() }

    fun start(context: Context, files: List<PdfFile>, options: PdfOptions, fileName: String) {
        if (_state.value?.isActive == true) return
        val app = context.applicationContext
        val o = options.effective(files.size, files.any { it.encrypted })
        _state.value = Job(files, o, fileName)
        scope.launch {
            val work = workDir(app).apply { deleteRecursively(); mkdirs() }
            val out = outputDir(app).apply { deleteRecursively(); mkdirs() }
            val name = safeFileName(fileName, "document")
            val plan = plan(
                files.map { it.input }, files.sumOf { it.pages ?: 0 }, o, work.absolutePath, out.absolutePath, name, ownerPassword()
            )
            if (plan == null) {
                _state.update { it?.copy(phase = Phase.Failed) }
                LinuxTasks.reportFailure(PdfToolsFeature, name, "Nothing to do with these pages")
                return@launch
            }
            val result = LinuxTasks.runInBackground(
                app, PdfCommands.script(plan.steps), PdfToolsFeature,
                label = "$name.pdf",
                timeoutMs = Long.MAX_VALUE,
                onStart = { id -> _state.update { it?.copy(taskId = id, phase = Phase.Working) } },
                onLine = { line ->
                    val fraction = PdfCommands.progress(line, plan.pages) ?: return@runInBackground
                    val updated = _state.updateAndGet { it?.copy(fraction = fraction) }
                    updated?.taskId?.let { LinuxTasks.setProgress(it, fraction * 100) }
                }
            )
            val cancelled = LinuxTasks.isCancelled(_state.value?.taskId)
            // What it made, in order (a split's parts by their pages)
            val made = out.listFiles()?.filter { it.isFile && it.length() > 0 }?.sortedBy { it.name }.orEmpty()
            when {
                cancelled -> _state.update { it?.copy(phase = Phase.Cancelled) }
                // The card says it failed; what qpdf or Ghostscript said is in the task's log
                !result.isSuccess || made.isEmpty() -> _state.update { it?.copy(phase = Phase.Failed) }
                else -> {
                    _state.update { it?.copy(phase = Phase.Saving, fraction = null, sizeAfter = made.sumOf { f -> f.length() }) }
                    val saved = runCatching { save(app, made, name) }.getOrNull()
                    _state.update {
                        if (saved != null) it?.copy(phase = Phase.Done, saved = saved)
                        else it?.copy(phase = Phase.Failed)
                    }
                    if (saved == null) LinuxTasks.reportFailure(PdfToolsFeature, name, "Couldn't save the file")
                }
            }
            work.deleteRecursively()
            out.deleteRecursively()
        }
    }

    fun cancel() {
        _state.value?.taskId?.let { LinuxTasks.cancel(it) }
    }

    /** Forgets a finished job (e.g. to make another). */
    fun clear() {
        if (_state.value?.isActive != true) _state.value = null
    }

    /** Whether a job reads the open files' copies now: they can't be closed meanwhile. */
    fun isBusy() = _state.value?.isActive == true

    /** Its data deleted (the copies with it): nothing's open anymore. */
    fun forget() {
        cancel()
        files = emptyList()
        fileName = ""
        options = null
        _state.value = null
    }

    /** A protected file's owner password: what lifts restrictions it doesn't have, so no one needs to know it. */
    private fun ownerPassword(): String {
        val chars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (1..32).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    /** Where new files go: a folder picked by the user, else Downloads (see [save]). */
    val output = OutputFolder(PdfToolsFeature.S_OUTPUT)

    /**
     * Copies [files] into the folder picked ([output]), else Downloads, a split's parts together in a folder of
     * their [name] there.
     */
    private suspend fun save(context: Context, files: List<File>, name: String): List<SavedFile> = withContext(Dispatchers.IO) {
        val downloads = context.getString(R.string.f_Pdf_downloads)
        val picked = output.name(context, downloads)
        val folder = if (files.size > 1) "${Environment.DIRECTORY_DOWNLOADS}/$name" else Environment.DIRECTORY_DOWNLOADS
        files.map { file ->
            output.save(context, file, MIME_TYPE)?.let { uri ->
                return@map SavedFile(uri, displayName(context, uri) ?: file.name, MIME_TYPE, picked)
            }
            val uri = saveToMediaStore(
                context, MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), folder, file.name, MIME_TYPE
            ) { out -> file.inputStream().use { it.copyTo(out) } }
            SavedFile(uri, displayName(context, uri) ?: file.name, MIME_TYPE, if (files.size > 1) "$downloads/$name" else downloads)
        }
    }
}
