package org.lukemech.lmtoolbox.linux.parts

import org.lukemech.lmtoolbox.linux.InstallResult
import android.app.ActivityManager
import android.content.Context
import android.os.Process
import java.io.File
import org.lukemech.lmtoolbox.linux.ExecResult
import org.lukemech.lmtoolbox.linux.LinuxEnv
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.helpers.AppFeature

/**
 * Java in the Linux subsystem for the tools on it: a part per version ([JDKS], each a Play module of
 * just its archive), which a tool installs when it needs one ([install]: e.g. the Minecraft server, for
 * a server's version), so it lists them in its extraExtensions. In the app, not a version's module: no
 * tool should have to bring along a version it doesn't use. Its command lines, and runs as LinuxTasks runs any command ([run],
 * [runInBackground], [runService]).
 */
object Java {
    /** A Java version: its part (and Play module), and its program in the subsystem (jdk.sh links it). */
    data class Jdk(val part: String, val version: Int) {
        val program get() = "java-$version"
    }

    /**
     * Every version the subsystem has, oldest first: each Minecraft version runs on the one it's made for
     * ([forNeeds]). One may not be built for this device's ABI (25 for x86, see [available]).
     */
    val JDKS = listOf(Jdk("jdk8", 8), Jdk("jdk17", 17), Jdk("jdk21", 21), Jdk("jdk25", 25))

    fun byPart(part: String?): Jdk? = JDKS.firstOrNull { it.part == part }

    /** The versions shipped for this device's ABI (e.g. no 25 for x86). */
    fun available(context: Context): List<Jdk> = JDKS.filter { it.part in LinuxParts.parts(context) }

    fun isInstalled(context: Context, jdk: Jdk) = LinuxParts.isExtensionInstalled(context, jdk.part)

    /** Of [JDKS], the one what needs Java [needs] is made for (see [forNeeds]), whether this device has it or not. */
    fun madeFor(needs: Int): Jdk? = (if (needs == 16) 17 else needs).let { v -> JDKS.firstOrNull { it.version == v } }

    /**
     * What needs Java [needs] (a Minecraft version, as Mojang's metadata says: e.g. 17 for 1.18) runs on:
     * that version, never a newer one (a server's loader may refuse it: Paper 1.17 runs on 16 alone); for 16
     * (Minecraft 1.17's, which no one builds for Android), 17. Null when this device has none (25 on x86).
     */
    fun forNeeds(context: Context, needs: Int): Jdk? = madeFor(needs)?.takeIf { it in available(context) }

    /** Installs [jdk] (asked first, see InstallPrompt; downloaded and unpacked as a task of the tool behind [source]): how it went. */
    suspend fun install(context: Context, source: AppFeature, jdk: Jdk): InstallResult =
        LinuxTasks.installParts(context, source, listOf(jdk.part))

    /** For every run: no performance data files in /tmp (mapped through PRoot), no display. */
    private val BASE = listOf("-XX:-UsePerfData", "-Djava.awt.headless=true")

    /** Java's heap: at most [maxMb] (-Xmx), from [initialMb] (-Xms) when given. */
    data class Heap(val maxMb: Int, val initialMb: Int? = null)

    /**
     * [jdk]'s java with [heap] and [options] (before what's run, e.g. -D...), then [args] (e.g. "-jar",
     * "server.jar", "nogui"), run in [dir] when given (relative paths in [args] are from there), its
     * process id written to [pidFile] when given.
     */
    fun command(
        jdk: Jdk, args: List<String>, dir: File? = null, heap: Heap? = null, options: List<String> = emptyList(), pidFile: File? = null
    ): List<String> {
        val java = buildList {
            add(jdk.program)
            heap?.let { h ->
                h.initialMb?.let { add("-Xms${it.coerceAtMost(h.maxMb)}M") }
                add("-Xmx${h.maxMb}M")
            }
            addAll(BASE)
            addAll(options)
            addAll(args)
        }
        return if (dir != null || pidFile != null) inDir(dir ?: File("/"), java, pidFile) else java
    }

    /**
     * [cmd] run in [dir] (the subsystem's commands start in its /): the shell changes there and becomes
     * it, writing its process id to [pidFile] first when given (PRoot keeps the real ones).
     */
    fun inDir(dir: File, cmd: List<String>, pidFile: File? = null): List<String> =
        if (pidFile == null) listOf("sh", "-c", $$"cd \"$0\" && exec \"$@\"", dir.absolutePath) + cmd
        else listOf("sh", "-c", $$"cd \"$0\" && echo $$ > \"$1\" && shift && exec \"$@\"", dir.absolutePath, pidFile.absolutePath) + cmd

    /**
     * The biggest heap a run should ask for, in MB: 60% of the phone's memory (Android and the other
     * apps need the rest), up to what Java addresses (~1 GB on 32-bit ABIs).
     */
    fun maxHeapMb(context: Context): Int {
        val am = context.getSystemService(ActivityManager::class.java)
        val totalMb = am?.let { ActivityManager.MemoryInfo().also(it::getMemoryInfo).totalMem / (1024 * 1024) } ?: 4096
        return (totalMb * 6 / 10).toInt().coerceAtMost(if (Process.is64Bit()) 16384 else 1024)
    }

    /** A run bound to its page (see [LinuxTasks.run]), e.g. a program's version. */
    suspend fun run(
        context: Context, source: AppFeature, jdk: Jdk, args: List<String>,
        dir: File? = null, heap: Heap? = null, options: List<String> = emptyList(),
        timeoutMs: Long = LinuxEnv.DEFAULT_TIMEOUT_MS,
    ): ExecResult = LinuxTasks.run(context, command(jdk, args, dir, heap, options), source, timeoutMs = timeoutMs)

    /** A run going on when its page is left, as a task (see [LinuxTasks.runInBackground]), e.g. an installer. */
    suspend fun runInBackground(
        context: Context, source: AppFeature, jdk: Jdk, args: List<String>,
        dir: File? = null, heap: Heap? = null, options: List<String> = emptyList(),
        label: String = args.joinToString(" "),
        timeoutMs: Long = LinuxEnv.DEFAULT_TIMEOUT_MS,
        maxOutput: Int = LinuxEnv.DEFAULT_MAX_OUTPUT,
        onStart: (id: Long) -> Unit = {},
        onLine: ((String) -> Unit)? = null,
    ): ExecResult = LinuxTasks.runInBackground(
        context, command(jdk, args, dir, heap, options), source,
        label = label, timeoutMs = timeoutMs, maxOutput = maxOutput, onStart = onStart, onLine = onLine
    )

    /**
     * A program running until it's stopped, e.g. a server: a service, not a task (see
     * [LinuxTasks.runService]); its process id in [pidFile].
     */
    suspend fun runService(
        context: Context, source: AppFeature, jdk: Jdk, args: List<String>, label: String,
        dir: File? = null, heap: Heap? = null, options: List<String> = emptyList(), pidFile: File? = null,
        onStart: (id: Long) -> Unit = {},
        onLine: ((String) -> Unit)? = null,
    ): ExecResult = LinuxTasks.runService(
        context, command(jdk, args, dir, heap, options, pidFile), source, label, onStart = onStart, onLine = onLine
    )
}
