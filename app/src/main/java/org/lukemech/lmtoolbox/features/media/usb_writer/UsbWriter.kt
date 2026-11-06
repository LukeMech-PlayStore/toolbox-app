package org.lukemech.lmtoolbox.features.media.usb_writer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lukemech.lmtoolbox.features.media.usb_writer.windows.WindowsMedia
import org.lukemech.lmtoolbox.helpers.displayName
import org.lukemech.lmtoolbox.tasks.AppTasks
import org.lukemech.lmtoolbox.tasks.TaskRun

/**
 * The USB drive maker's state, kept while its page is left (the writing goes on, a task): the image picked, the drive,
 * how far the writing is. An image's written as it is ([Mode.Raw]: Linux ISOs, which are hybrid, Raspberry Pi
 * images...) or, a Windows installation ISO, made into a drive UEFI boots from ([Mode.Windows], see [WindowsMedia]).
 */
object UsbWriter {
    private const val TAG = "UsbWriter"

    enum class Mode { Raw, Windows }

    /** A picked image: its [name], [size], how it's written, and for Windows' ISOs what [WindowsMedia] tells of it. */
    data class Image(
        val uri: Uri,
        val name: String,
        val size: Long,
        val mode: Mode,
        /** The ISO's volume name (Windows'). */
        val label: String? = null,
        /** Windows': install.wim is split for FAT32. */
        val splitsWim: Boolean = false,
        /**
         * It starts with a partition table (MBR): a hybrid ISO, a disk image, which boots written as it is; null: not
         * known (its provider can't be read anywhere in it, so it wasn't looked into).
         */
        val bootable: Boolean? = null,
        /** Windows': the CPUs it boots on (x64, ARM64...), by its UEFI bootloaders. */
        val architectures: List<String> = emptyList(),
        /** Windows': the parts install.wim's split into (0: it isn't). */
        val wimParts: Int = 0,
        /** Windows': a file over 4 GiB that can't be split (e.g. install.esd), so it can't be written. */
        val tooLarge: String? = null,
    )

    enum class Phase { Idle, Preparing, Writing, Verifying, Done, Failed }

    var image by mutableStateOf<Image?>(null)
    var driveKey by mutableStateOf<String?>(null)
    /** The drive written last (or being written): its name, for the page and the task. */
    var driveName by mutableStateOf<String?>(null)
    var verify by mutableStateOf(true)
    /** What the chosen drive said it is, and its size (see [probe]). */
    var driveInfo by mutableStateOf<DriveInfo?>(null)
    /** A drive's being looked at ([probe]): one at a time (two would take the drive from each other). */
    var probing by mutableStateOf(false)
        private set

    class DriveInfo(val description: String, val size: Long)

    val run = TaskRun(UsbWriterFeature)
    var phase by mutableStateOf(Phase.Idle)
    var done by mutableLongStateOf(0L)
    var total by mutableLongStateOf(0L)

    val working get() = run.running

    /** [uri] as an image: its name and size, and how it's to be written. */
    suspend fun inspect(context: Context, uri: Uri): Image = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = displayName(context, uri) ?: uri.lastPathSegment ?: "image"
        val size = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
            }
        }.getOrNull() ?: 0L
        // A file that can be read anywhere in it (most can: a file's descriptor) is looked into
        val looked = runCatching {
            openRandom(context, uri).use { (_, source) ->
                val mbr = ByteArray(512).also { if (source.size >= 512) source.readFully(0, it) }
                val bootable = mbr[510] == 0x55.toByte() && mbr[511] == 0xAA.toByte()
                val windows = runCatching { WindowsMedia.inspect(source) }.onFailure { Log.w(TAG, "Not read as UDF", it) }.getOrNull()
                Image(
                    uri, name, source.size, if (windows != null) Mode.Windows else Mode.Raw,
                    label = windows?.label, splitsWim = windows?.splitsWim == true, bootable = bootable,
                    architectures = windows?.architectures.orEmpty().map { it.label },
                    wimParts = windows?.wimParts ?: 0,
                    tooLarge = windows?.tooLarge
                )
            }
        }.onFailure { Log.w(TAG, "Couldn't look into $uri", it) }.getOrNull()
        looked ?: Image(uri, name, size, Mode.Raw)
    }

    /** [uri]'s descriptor, read at any position. */
    private fun openRandom(context: Context, uri: Uri): RandomFile {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("Can't open $uri")
        val channel = FileInputStream(pfd.fileDescriptor).channel
        return RandomFile(pfd, ChannelSource(channel))
    }

    private data class RandomFile(val pfd: ParcelFileDescriptor, val source: RandomSource) : AutoCloseable {
        override fun close() = pfd.close()
    }

    /**
     * What [drive] is (its INQUIRY names) and its size, it opened (Android lets go of it: it's unmounted) and closed:
     * a card reader without a card fails here, not halfway through writing. Its failure is a failed task's.
     */
    suspend fun probe(context: Context, drive: UsbDrive): DriveInfo? {
        if (probing) return null
        probing = true
        try {
            return probeNow(context, drive)
        } finally {
            probing = false
        }
    }

    private suspend fun probeNow(context: Context, drive: UsbDrive): DriveInfo? = withContext(Dispatchers.IO) {
        try {
            UsbDrives.open(context, drive).use { DriveInfo(runCatching { it.inquiry() }.getOrDefault(""), it.size) }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't open ${drive.name}", e)
            val message = if (e is NoMediumException) context.getString(org.lukemech.lmtoolbox.R.string.f_UsbWriter_no_medium)
            else e.message ?: e.javaClass.simpleName
            AppTasks.reportFailure(UsbWriterFeature, drive.name, message, e.stackTraceToString())
            null
        }
    }

    /** [image] onto [drive] (all on it erased), then read back if [verify]: a task, going on as the page is left. */
    fun start(context: Context, image: Image, drive: UsbDrive, verify: Boolean) {
        if (run.running) return
        val app = context.applicationContext
        driveName = drive.name
        done = 0
        total = 0
        phase = Phase.Preparing
        run.start(app, "${image.name} → ${drive.name}") {
            val wakeLock = (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LMToolbox::UsbWriter")
            wakeLock.acquire(6 * 60 * 60 * 1000L)
            try {
                withContext(Dispatchers.IO) { write(app, image, drive, verify) }
                phase = Phase.Done
            } catch (e: CancellationException) {
                phase = Phase.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Writing failed", e)
                phase = Phase.Failed
                throw e
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    private suspend fun write(context: Context, image: Image, drive: UsbDrive, verify: Boolean) {
        (if (image.mode == Mode.Windows) openRandom(context, image.uri) else null).use { random ->
            UsbDrives.open(context, drive).use { device ->
                closedOnCancel(device) { writeTo(context, device, image, random?.source, verify) }
            }
        }
    }

    /**
     * [block], [device] closed as soon as it's stopped (the page's or the notification's ✕): that ends the transfer
     * it's in (blocking, deaf to the coroutine's cancellation) at once, instead of after its timeout.
     */
    private suspend fun closedOnCancel(device: BlockDevice, block: suspend () -> Unit) = coroutineScope {
        val closer = launch(Dispatchers.IO) {
            device.use { awaitCancellation() }
        }
        try {
            block()
        } catch (e: Exception) {
            // Stopped: the transfer failed because the drive was closed under it (by the closer), which isn't an
            // error. The cancellation goes on, not the failure (the task's canceled, not failed)
            ensureActive()
            throw e
        } finally {
            closer.cancel()
        }
    }

    private suspend fun writeTo(context: Context, device: BlockDevice, image: Image, iso: RandomSource?, verify: Boolean) {
        val plan = when (image.mode) {
            Mode.Raw -> DiskPlan(listOf(Extent(0, StreamContent(image.size) {
                context.contentResolver.openInputStream(image.uri) ?: throw IOException("Can't open ${image.name}")
            })))
            Mode.Windows -> {
                val inspection = WindowsMedia.inspect(iso!!) ?: throw IOException("${image.name} isn't a Windows ISO any more")
                WindowsMedia.plan(inspection, device.size, device.blockSize).plan
            }
        }
        var last = 0L
        val progress = DiskWriter.Progress { d, t ->
            // The page and the task's bar: a few times a second
            val now = System.nanoTime()
            if (d == t || now - last > 200_000_000L) {
                last = now
                done = d
                total = t
                run.progress(if (t > 0) d.toFloat() / t else null)
            }
        }
        phase = Phase.Writing
        DiskWriter.write(plan, device, progress)
        if (verify) {
            phase = Phase.Verifying
            DiskWriter.verify(plan, device, progress)
        }
    }
}
