package org.lukemech.lmtoolbox.features.media.pdf_tools

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import org.lukemech.lmtoolbox.helpers.writePicturePdf
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.tasks.AppTasks
import org.lukemech.lmtoolbox.ui.helpers.Images

/**
 * A PDF being worked on (or a picture added, made one: [picture]): where it's from, its name, its copy qpdf,
 * Ghostscript and the page grid read (one without its password or restrictions, once it's opened), and its pages.
 */
data class PdfFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val copy: File,
    val size: Long,
    /** Null while it can't be opened without its [password]. */
    val pages: Int?,
    /** Had a password (typed, [password]) or restrictions: its copy has neither. */
    val encrypted: Boolean,
    val password: String? = null,
    val picture: Boolean = false,
) {
    val needsPassword get() = pages == null
    val baseName get() = name.substringBeforeLast('.').ifBlank { name }
    val input get() = PdfInput(copy.absolutePath)
}

/**
 * Runs qpdf and Ghostscript: [open] copies PDFs in and reads what's in them, [start] does what's picked with
 * them, which goes on (shown in the bottom bar) when the page is left and ends up in Downloads or the folder
 * picked. One at a time, in [state].
 */
object PdfJobs {
    const val MIME_TYPE = "application/pdf"

    /** A picture added is drawn at most this big (pixels on its longer side): an A4 page's width at 300 dpi. */
    private const val PICTURE_SIZE = 2480

    data class Job(
        val files: List<PdfFile>,
        /** The pages made into it, in their order. */
        val pages: List<PageRef>,
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
     * The open files, their pages as arranged and those selected, the new name and what's picked are the page's, kept
     * here: back to them after the page was left, or redrawn afresh (e.g. back from the file picker). [fileName] null:
     * not typed (the output's follows).
     */
    var files by mutableStateOf<List<PdfFile>>(emptyList())
    var pages by mutableStateOf<List<PageRef>>(emptyList())
    var selected by mutableStateOf<Set<Long>>(emptySet())
    var fileName by mutableStateOf<String?>(null)
    var options by mutableStateOf(PdfOptions())

    private val _state = MutableStateFlow<Job?>(null)
    val state: StateFlow<Job?> = _state.asStateFlow()

    private fun inputDir(context: Context) = File(PdfToolsFeature.cacheDir(context), "in")
    private fun workDir(context: Context) = File(PdfToolsFeature.cacheDir(context), "work")
    private fun outputDir(context: Context) = File(PdfToolsFeature.cacheDir(context), "out")

    /**
     * Copies each of [uris] in (qpdf reads files) and reads what's in it, a picture made a page; those it can't read
     * are left out, the bottom bar telling why. Empty when none could be, or the subsystem isn't ready.
     */
    suspend fun open(context: Context, uris: List<Uri>): List<PdfFile> = buildList {
        // Nothing open: what's left of files opened before the app was closed goes
        if (files.isEmpty() && !isBusy()) withContext(Dispatchers.IO) { inputDir(context).deleteRecursively() }
        for (uri in uris) {
            val name = displayName(context, uri) ?: "document.pdf"
            val id = ids.incrementAndGet()
            // A plain name keeps odd characters out of its commands
            val file = File(inputDir(context).apply { mkdirs() }, "$id.pdf")
            if (context.contentResolver.getType(uri)?.startsWith("image/") == true) {
                val made = Images.loadUri(context, uri, PICTURE_SIZE)?.let { picture ->
                    withContext(Dispatchers.IO) { runCatching { file.outputStream().use { writePicturePdf(picture, it) } }.isSuccess }
                } == true
                if (made) add(PdfFile(id, uri, name, file, file.length(), pages = 1, encrypted = false, picture = true))
                else AppTasks.reportFailure(PdfToolsFeature, name, "Couldn't read the picture")
                continue
            }
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                }.isSuccess
            }
            if (!copied) {
                // Not a command's: told in the one place errors are, the bottom bar
                AppTasks.reportFailure(PdfToolsFeature, name, "Couldn't read the file")
                continue
            }
            val result = LinuxTasks.run(context, PdfCommands.inspect(PdfInput(file.absolutePath)), PdfToolsFeature)
            if (result.isEnvNotReady) {
                file.delete()
                break
            }
            val inspection = PdfCommands.parseInspection(result.stdout)
            if (inspection == null) {
                file.delete()
                AppTasks.reportFailure(PdfToolsFeature, name, "Not a PDF qpdf can read", result.output)
                continue
            }
            val opened = PdfFile(id, uri, name, file, file.length(), inspection.pages, inspection.encrypted)
            // Restrictions only: opened without them right away (the grid's pages are drawn by Android, which can't)
            add(if (opened.encrypted && !opened.needsPassword) decrypted(context, opened, null) ?: continue else opened)
        }
    }

    /** [file] opened with [password]: with its pages, or null if that's not its password. */
    suspend fun unlock(context: Context, file: PdfFile, password: String): PdfFile? {
        val result = LinuxTasks.run(context, PdfCommands.inspect(PdfInput(file.copy.absolutePath), password), PdfToolsFeature)
        val pages = PdfCommands.parseInspection(result.stdout)?.takeIf { !it.needsPassword }?.pages ?: return null
        return decrypted(context, file.copy(pages = pages, password = password), password)
    }

    /** [file]'s copy without its password and restrictions, in its place; null (its log in the task list) if it can't be. */
    private suspend fun decrypted(context: Context, file: PdfFile, password: String?): PdfFile? {
        val plain = File(file.copy.parentFile, "${file.id}-open.pdf")
        val result = LinuxTasks.run(context, PdfCommands.unlock(PdfInput(file.copy.absolutePath), password, plain.absolutePath), PdfToolsFeature)
        if (!result.isSuccess || !plain.exists()) {
            plain.delete()
            return null
        }
        file.copy.delete()
        return file.copy(copy = plain, size = plain.length())
    }

    /** [opened] added after the open files, their pages after the pages (a protected one's once it's opened). */
    fun add(opened: List<PdfFile>) {
        files = files + opened
        pages = pages + opened.flatMap(::pagesOf)
    }

    /** [file] opened with its password (see [unlock]): in its place, its pages after the others. */
    fun unlocked(file: PdfFile) {
        files = files.map { if (it.id == file.id) file else it }
        pages = pages + pagesOf(file)
    }

    private fun pagesOf(file: PdfFile) = (0 until (file.pages ?: 0)).map { PageRef(ids.incrementAndGet(), file.id, it) }

    /** A copy of [page] (its own key), for the grid's "duplicate". */
    fun copyOf(page: PageRef) = page.copy(key = ids.incrementAndGet())

    /**
     * The files none of the [pages] are of anymore closed (their copies deleted), but those still to be opened with
     * their passwords; not while a job reads them.
     */
    fun prune() {
        if (isBusy()) return
        val used = pages.mapTo(HashSet()) { it.file }
        val (kept, gone) = files.partition { it.id in used || it.needsPassword }
        files = kept
        close(gone)
    }

    /** [file] closed (one still to be opened with its password: it has no pages). */
    fun remove(file: PdfFile) {
        files = files - file
        pages = pages.filter { it.file != file.id }
        close(listOf(file))
    }

    /** Every open file closed, its pages with it. */
    fun closeAll() {
        close(files)
        files = emptyList()
        pages = emptyList()
        selected = emptySet()
    }

    /** Deletes the copies of [gone] (closed files, not being read now). */
    fun close(gone: List<PdfFile>) = gone.forEach { it.copy.delete() }

    /** The pages [o] is made of: those selected (in their order), or all. */
    fun pagesFor(o: PdfOptions) = if (o.selectedOnly && selected.isNotEmpty()) pages.filter { it.key in selected } else pages

    fun start(context: Context, o: PdfOptions, fileName: String) {
        if (_state.value?.isActive == true) return
        val app = context.applicationContext
        val made = pagesFor(o)
        val used = made.mapTo(HashSet()) { it.file }
        val files = files.filter { it.id in used }
        _state.value = Job(files, made, o, fileName)
        scope.launch {
            val work = workDir(app).apply { deleteRecursively(); mkdirs() }
            val out = outputDir(app).apply { deleteRecursively(); mkdirs() }
            val name = safeFileName(fileName, "document")
            val plan = plan(
                made, files.associate { it.id to it.input }, files.associate { it.id to (it.pages ?: 0) }, o,
                work.absolutePath, out.absolutePath, name, ownerPassword()
            )
            if (plan == null) {
                _state.update { it?.copy(phase = Phase.Failed) }
                AppTasks.reportFailure(PdfToolsFeature, name, "Nothing to do with these pages")
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
                    updated?.taskId?.let { AppTasks.setProgress(it, fraction * 100) }
                }
            )
            val cancelled = AppTasks.isCancelled(_state.value?.taskId)
            // What it made, in order (a split's parts, pictures, by their pages)
            val madeFiles = out.listFiles()?.filter { it.isFile && it.length() > 0 }?.sortedBy { it.name }.orEmpty()
            when {
                cancelled -> _state.update { it?.copy(phase = Phase.Cancelled) }
                // The card says it failed; what qpdf or Ghostscript said is in the task's log
                !result.isSuccess || madeFiles.isEmpty() -> _state.update { it?.copy(phase = Phase.Failed) }
                else -> {
                    _state.update { it?.copy(phase = Phase.Saving, fraction = null, sizeAfter = madeFiles.sumOf { f -> f.length() }) }
                    val saved = runCatching { save(app, madeFiles, name, o.mimeType) }.getOrNull()
                    _state.update {
                        if (saved != null) it?.copy(phase = Phase.Done, saved = saved)
                        else it?.copy(phase = Phase.Failed)
                    }
                    if (saved == null) AppTasks.reportFailure(PdfToolsFeature, name, "Couldn't save the file")
                }
            }
            work.deleteRecursively()
            out.deleteRecursively()
        }
    }

    fun cancel() {
        _state.value?.taskId?.let { AppTasks.cancel(it) }
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
        pages = emptyList()
        selected = emptySet()
        fileName = null
        options = PdfOptions()
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
     * Copies [files] (of [mimeType]) into the folder picked ([output]), else Downloads, several (a split's parts,
     * pictures) together in a folder of their [name] there.
     */
    private suspend fun save(context: Context, files: List<File>, name: String, mimeType: String): List<SavedFile> = withContext(Dispatchers.IO) {
        val downloads = context.getString(R.string.f_Pdf_downloads)
        val picked = output.name(context, downloads)
        val folder = if (files.size > 1) "${Environment.DIRECTORY_DOWNLOADS}/$name" else Environment.DIRECTORY_DOWNLOADS
        files.map { file ->
            output.save(context, file, mimeType)?.let { uri ->
                return@map SavedFile(uri, displayName(context, uri) ?: file.name, mimeType, picked)
            }
            val uri = saveToMediaStore(
                context, MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), folder, file.name, mimeType
            ) { out -> file.inputStream().use { it.copyTo(out) } }
            SavedFile(uri, displayName(context, uri) ?: file.name, mimeType, if (files.size > 1) "$downloads/$name" else downloads)
        }
    }
}

/** What [PdfOptions] makes is saved as. */
val PdfOptions.mimeType get() = if (output == PdfOutput.Images) imageType.mimeType else PdfJobs.MIME_TYPE
