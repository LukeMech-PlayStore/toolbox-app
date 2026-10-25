package org.lukemech.lmtoolbox.linux

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.system.Os
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.lukemech.lmtoolbox.linux.LinuxParts.CORE
import org.lukemech.lmtoolbox.linux.LinuxParts.isExtensionUnpacked
import org.lukemech.lmtoolbox.linux.LinuxParts.nativeLib
import org.lukemech.lmtoolbox.linux.LinuxPaths.linuxDir
import org.lukemech.lmtoolbox.linux.LinuxPaths.localBinDir
import org.lukemech.lmtoolbox.linux.LinuxPaths.localDir
import org.lukemech.lmtoolbox.linux.LinuxPaths.prefixDir
import org.lukemech.lmtoolbox.linux.LinuxPaths.tempDir

/**
 * Runs commands inside the subsystem, through PRoot, as Termux's programs expect (its prefix, Android's
 * environment). What's there to run on is [LinuxParts]', unpacked by [LinuxInstaller].
 */
object LinuxEnv {
    private const val TAG = "LinuxEnv"
    const val DEFAULT_TIMEOUT_MS = 120_000L
    /** The id of the task (or service) a command runs for, in its environment (see [exec]'s task). */
    const val TASK_ENV = "LMTOOLBOX_TASK"
    private const val EXIT_TIMEOUT = 124

    /** Where Termux binaries expect their prefix; the subsystem's usr/ is bound there. */
    @SuppressLint("SdCardPath")
    private const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"

    private const val GUEST_PATH = "$TERMUX_PREFIX/bin:/system/bin:/system/xbin"

    /**
     * How much of a command's stdout is kept unless asked otherwise: room for what's parsed whole
     * (yt-dlp's -J, the largest) while a chatty command (an installer, an encode) can't fill the heap.
     */
    const val DEFAULT_MAX_OUTPUT = 2 * 1024 * 1024
    /** Of stderr only its end is ever read (the last line, a task's log), see [LinuxTasks]. */
    private const val MAX_STDERR = 64 * 1024

    /**
     * Runs [cmd] (argv, no shell involved) inside the Linux environment. If canceled, the
     * process is killed and [onCancelled] gets its output until then. [onLine] gets each line it
     * prints (stdout and stderr) as it comes, e.g. for progress. Of its output only the last
     * [maxOutput] characters of stdout (and at most [MAX_STDERR] of stderr) are kept, e.g. for a
     * server printing for days.
     * [task]: its task's (or service's) id, in its environment as [TASK_ENV] (what it takes is
     * measured by it, see ResourceScope.Task). Use [LinuxTasks.run] from UI code, which tracks it as a
     * task and handles updates.
     */
    suspend fun exec(
        context: Context,
        cmd: List<String>,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onLine: ((String) -> Unit)? = null,
        maxOutput: Int = DEFAULT_MAX_OUTPUT,
        task: Long? = null,
        onCancelled: (partial: ExecResult) -> Unit = {}
    ): ExecResult = withContext(Dispatchers.IO) {
        if (!isExtensionUnpacked(context, CORE)) {
            return@withContext ExecResult(-1, "", "Linux environment is not installed")
        }
        prepareRuntime(context)

        val process = ProcessBuilder(prootCommand(context, cmd))
            .directory(linuxDir(context))
            .apply {
                environment().clear()
                environment().putAll(environment(context))
                task?.let { environment()[TASK_ENV] = it.toString() }
            }
            .start()
        process.outputStream.close()

        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val readers = listOf(
            drain(process.inputStream, stdout, onLine, maxOutput),
            drain(process.errorStream, stderr, onLine, minOf(maxOutput, MAX_STDERR))
        )

        fun output(exitCode: Int): ExecResult {
            readers.forEach { it.join(1_000) }
            return ExecResult(exitCode, synchronized(stdout) { stdout.toString().trim() }, synchronized(stderr) { stderr.toString().trim() })
        }

        val exitCode = try {
            withTimeoutOrNull(timeoutMs.milliseconds) { runInterruptible { process.waitFor() } }
        } catch (e: CancellationException) {
            // Hand over what it printed until then (for the canceled task's log); passed out
            // here since withContext rethrows its own cancellation, not this block's exception
            process.destroy()
            onCancelled(output(-1))
            throw e
        } finally {
            // --kill-on-exit takes the guest processes down too
            process.destroy()
        }

        output(exitCode ?: EXIT_TIMEOUT).also {
            if (exitCode == null) Log.w(TAG, "Timed out after ${timeoutMs}ms: $cmd")
            else if (!it.isSuccess) Log.w(TAG, "Exit $exitCode: $cmd\n${it.stderr}")
        }
    }

    private fun drain(stream: InputStream, into: StringBuilder, onLine: ((String) -> Unit)?, maxOutput: Int) = thread(isDaemon = true) {
        try {
            stream.bufferedReader().use { reader ->
                val buf = CharArray(8192)
                // Carriage returns end lines too (progress bars redraw with them)
                val line = StringBuilder()
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    synchronized(into) {
                        into.appendRange(buf, 0, n)
                        // Cut back once it's twice over, not on every read
                        if (into.length / 2 > maxOutput) into.delete(0, into.length - maxOutput)
                    }
                    if (onLine == null) continue
                    for (i in 0 until n) {
                        val c = buf[i]
                        if (c == '\n' || c == '\r') {
                            if (line.isNotEmpty()) onLine(line.toString())
                            line.clear()
                        } else line.append(c)
                    }
                }
                if (onLine != null && line.isNotEmpty()) onLine(line.toString())
            }
        } catch (_: IOException) {
        }
    }

    @SuppressLint("SdCardPath") // binds the host's /sdcard into the guest at the same path
    private fun prootCommand(context: Context, cmd: List<String>): List<String> {
        val proot = nativeLib(context, "libproot.so") ?: throw FileNotFoundException("libproot.so isn't installed (the $CORE module)")
        val prefix = prefixDir(context).absolutePath
        val binds = mutableListOf<String>()

        for (mnt in listOf(
            "/apex", "/odm", "/product", "/system", "/system_ext", "/vendor",
            "/plat_property_contexts", "/property_contexts",
            // The linker's config, for Termux binaries (apps can read it, but not stat its dir)
            "/linkerconfig/ld.config.txt"
        )) {
            val f = File(mnt)
            if (f.exists()) binds += f.canonicalPath
        }
        binds += listOf("/sdcard", "/storage", "/dev", "/data", "/dev/urandom:/dev/random", "/proc", prefix)
        binds += listOf(
            "${localDir(context).child("stat").absolutePath}:/proc/stat",
            "${localDir(context).child("vmstat").absolutePath}:/proc/vmstat"
        )
        if (File("/proc/self/fd").exists()) binds += "/proc/self/fd:/dev/fd"
        listOf("stdin", "stdout", "stderr").forEachIndexed { fd, name ->
            if (File("/proc/self/fd/$fd").exists()) binds += "/proc/self/fd/$fd:/dev/$name"
        }
        binds += "/sys"
        binds += "${linuxDir(context).child("tmp").absolutePath}:/dev/shm"
        // Over the /data bind above: proot picks the most specific one
        binds += "${linuxDir(context).child("usr").absolutePath}:$TERMUX_PREFIX"

        return buildList {
            // Executed straight from where the core module's libraries are: no copy, no linker trick needed
            add(proot.absolutePath)
            add("--kill-on-exit")
            add("-w"); add("/")
            binds.forEach { add("-b"); add(it) }
            add("-r"); add(linuxDir(context).absolutePath)
            add("-0")
            add("--link2symlink")
            add("--sysvipc")
            add("-L")
            addAll(cmd)
        }
    }

    private fun environment(context: Context): Map<String, String> {
        val env = mutableMapOf(
            "PATH" to GUEST_PATH,
            "PREFIX" to TERMUX_PREFIX,
            "HOME" to "/root",
            "LANG" to "C.UTF-8",
            "TMPDIR" to "/tmp",
            // Python output as it's printed, not in blocks (progress read line by line)
            "PYTHONUNBUFFERED" to "1",
            "PROOT_TMP_DIR" to tempDir(context).child("session").also { it.mkdirs() }.absolutePath,
        )
        nativeLib(context, "libproot-loader.so")?.let { env["PROOT_LOADER"] = it.absolutePath }
        nativeLib(context, "libproot-loader32.so")?.let { env["PROOT_LOADER_32"] = it.absolutePath }
        // Needed by Android (and Termux) binaries
        for (name in listOf(
            "ANDROID_ART_ROOT", "ANDROID_DATA", "ANDROID_I18N_ROOT", "ANDROID_ROOT",
            "ANDROID_RUNTIME_ROOT", "ANDROID_TZDATA_ROOT", "BOOTCLASSPATH",
            "DEX2OATBOOTCLASSPATH", "EXTERNAL_STORAGE"
        )) {
            System.getenv(name)?.let { env[name] = it }
        }
        return env
    }

    /** Host-side files the guest expects. Cheap to re-check, only written when missing or stale. */
    private fun prepareRuntime(context: Context) {
        localDir(context).mkdirs()
        writeIfChanged(localDir(context).child("stat"), FakeProc.STAT)
        writeIfChanged(localDir(context).child("vmstat"), FakeProc.VMSTAT)

        linuxDir(context).child("tmp").takeIf { !it.exists() }?.let {
            it.mkdirs()
            Os.chmod(it.absolutePath, "1777".toInt(8))
        }
        // DNS servers for what doesn't ask Android (Go programs' own resolver, e.g. cloudflared's SRV
        // lookups; Termux's resolv-conf isn't shipped): the network's, as it is when a command starts
        writeIfChanged(
            linuxDir(context).child("usr/etc/resolv.conf").apply { parentFile?.mkdirs() },
            dnsServers(context).joinToString("") { "nameserver $it\n" }
        )
        // Leftovers of the old shell-script launcher
        listOf("proot", "init", "init-host").forEach { localBinDir(context).child(it).delete() }
    }

    /** The active network's DNS servers (plain ones, IPv4 first), public ones after them as a fallback. */
    private fun dnsServers(context: Context): List<String> {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val own = runCatching { cm?.getLinkProperties(cm.activeNetwork)?.dnsServers.orEmpty() }.getOrDefault(emptyList())
            .sortedBy { it !is Inet4Address }
            .mapNotNull { it.hostAddress?.substringBefore('%') }
        return (own + listOf("1.1.1.1", "8.8.8.8")).distinct()
    }

    private fun writeIfChanged(file: File, content: String) {
        if (!file.exists() || file.readText() != content) file.writeText(content)
    }
}
