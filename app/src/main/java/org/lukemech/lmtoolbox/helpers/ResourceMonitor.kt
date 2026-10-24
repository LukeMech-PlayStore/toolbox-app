package org.lukemech.lmtoolbox.helpers

import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.File
import org.lukemech.lmtoolbox.linux.LinuxEnv

/**
 * What a tool's work takes, as its page shows it in tiles (see ui.components.ResourceTiles): which
 * of them a tool shows is its own ([AppFeature.resourceTiles]).
 */
enum class ResourceTile {
    /** Of all the phone's cores. */
    Cpu,
    /** Resident memory. */
    Ram,
    /** Read from and written to storage. */
    Disk,
    /** Downloaded and uploaded (the app's, all of it: Android counts it by app, not by process). */
    Network,
}

/** Whose processes are measured. */
sealed interface ResourceScope {
    /** The app's own and everything it runs (the subsystem's programs, PRoot tracing them). */
    data object App : ResourceScope

    /**
     * What a task (or service) runs, by its [id] (see LinuxTasks; null: none yet): the processes with
     * it in their environment (LinuxEnv.TASK_ENV: its PRoot, and what PRoot runs, unless it cleared
     * its environment) and whatever they started (whatever their environment).
     */
    data class Task(val id: Long?) : ResourceScope

    /** What several tasks (or services) run, as [Task] each: e.g. a server and its tunnel. */
    data class Tasks(val ids: Set<Long>) : ResourceScope
}

/** Totals so far: CPU time (clock ticks), what storage gave and got, the network's; memory as it is now. */
data class ResourceCounters(
    val at: Long,
    val cpuTicks: Map<Int, Long>,
    /** Null: none of its processes runs (yet). */
    val rssBytes: Long?,
    val readBytes: Map<Int, Long>?,
    val writtenBytes: Map<Int, Long>?,
    val rxBytes: Long?,
    val txBytes: Long?,
)

/** What a scope took between two samples: CPU 0..100, memory now, the rest per second; null where unknown. */
data class ResourceSample(
    val cpuPercent: Float?,
    val rssBytes: Long?,
    val readPerSec: Long?,
    val writePerSec: Long?,
    val rxPerSec: Long?,
    val txPerSec: Long?,
)

/**
 * Reads what the app's processes take from /proc (Android shows an app its own processes alone)
 * and the network from TrafficStats (by the app's uid). Each process counted from its own totals
 * between two samples, so one starting or ending meanwhile doesn't throw the sums off.
 */
object ResourceMonitor {
    private val CLOCK_TICKS = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrDefault(100L).coerceAtLeast(1)
    private val CORES = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    private val PAGE_SIZE = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrDefault(4096L).coerceAtLeast(1)

    /** A process as /proc has it: its parent, when it started (its id's own: ids come again), CPU time and memory. */
    private class Proc(val pid: Int, val ppid: Int, val start: Long, val ticks: Long, val rssBytes: Long)

    /**
     * The app's processes (its uid's): each one's stat alone, read once it's known to be the app's (a
     * stat() of its folder; where /proc shows others too, they cost nothing more).
     */
    private fun processes(): List<Proc> {
        val uid = Process.myUid()
        return File("/proc").list().orEmpty().mapNotNull { name ->
            if (name.isEmpty() || !name.all(Char::isDigit)) return@mapNotNull null
            runCatching {
                if (Os.stat("/proc/$name").st_uid != uid) return@runCatching null
                // After the name (which may hold spaces): state [0], the parent [1], utime [11], stime [12],
                // its start [19], resident pages [21]
                val fields = File("/proc/$name/stat").readText().substringAfterLast(") ").trim().split(' ')
                Proc(name.toInt(), fields[1].toInt(), fields[19].toLong(), fields[11].toLong() + fields[12].toLong(), fields[21].toLong() * PAGE_SIZE)
            }.getOrNull()
        }
    }

    /** [scope]'s processes among [all]; none while its program isn't running. */
    private fun select(all: List<Proc>, scope: ResourceScope): List<Proc> = when (scope) {
        ResourceScope.App -> all
        is ResourceScope.Task -> tasks(all, setOfNotNull(scope.id))
        is ResourceScope.Tasks -> tasks(all, scope.ids)
    }

    /** The processes of the tasks [ids] (see ResourceScope.Task). */
    private fun tasks(all: List<Proc>, ids: Set<Long>): List<Proc> {
        if (ids.isEmpty()) return emptyList()
        val tags = ids.map { "${LinuxEnv.TASK_ENV}=$it" }
        // The app itself has none: what it runs gets it alone
        val roots = all.filter { p -> p.pid != Process.myPid() && taskOf(p)?.let { it in tags } == true }
        // Those gone: their ids may come again for others
        val alive = all.mapTo(HashSet()) { it.pid to it.start }
        synchronized(taskTags) { taskTags.keys.retainAll(alive) }
        return trees(all, roots)
    }

    /** Each process's [LinuxEnv.TASK_ENV] (its NAME=value; "" without), by its id and start: it doesn't change. */
    private val taskTags = HashMap<Pair<Int, Long>, String>()

    private fun taskOf(p: Proc): String? = synchronized(taskTags) {
        taskTags.getOrPut(p.pid to p.start) { environment(p.pid).firstOrNull { it.startsWith("${LinuxEnv.TASK_ENV}=") }.orEmpty() }
    }.ifEmpty { null }

    /** [roots] and what they started; each process once. */
    private fun trees(all: List<Proc>, roots: List<Proc>): List<Proc> {
        val children = all.groupBy { it.ppid }
        val seen = mutableSetOf<Int>()
        val queue = ArrayDeque(roots)
        val tree = mutableListOf<Proc>()
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            if (!seen.add(p.pid)) continue
            tree += p
            children[p.pid]?.let(queue::addAll)
        }
        return tree
    }

    /** A process's environment, as it started, each NAME=value. */
    private fun environment(pid: Int): Set<String> = runCatching {
        File("/proc/$pid/environ").readBytes().toString(Charsets.UTF_8).split('\u0000').toSet()
    }.getOrDefault(emptySet())

    /** /proc/<pid>/io's read_bytes and write_bytes (what storage gave and got); null where it can't be read. */
    private fun io(pid: Int): Pair<Long, Long>? = runCatching {
        var read = -1L
        var written = -1L
        File("/proc/$pid/io").forEachLine { line ->
            when {
                line.startsWith("read_bytes:") -> read = line.substringAfter(':').trim().toLong()
                line.startsWith("write_bytes:") -> written = line.substringAfter(':').trim().toLong()
            }
        }
        if (read < 0 || written < 0) null else read to written
    }.getOrNull()

    /** [scope]'s totals now ([tiles]: only what they show is read). Off the main thread: it reads /proc. */
    fun counters(scope: ResourceScope, tiles: Collection<ResourceTile>): ResourceCounters {
        val procs = select(processes(), scope)
        val ios = if (ResourceTile.Disk in tiles) procs.mapNotNull { p -> io(p.pid)?.let { p.pid to it } }.toMap() else null
        val net = ResourceTile.Network in tiles
        val uid = Process.myUid()
        return ResourceCounters(
            at = SystemClock.elapsedRealtime(),
            cpuTicks = procs.associate { it.pid to it.ticks },
            rssBytes = procs.takeIf { it.isNotEmpty() }?.sumOf { it.rssBytes },
            readBytes = ios?.takeIf { it.isNotEmpty() || procs.isEmpty() }?.mapValues { it.value.first },
            writtenBytes = ios?.takeIf { it.isNotEmpty() || procs.isEmpty() }?.mapValues { it.value.second },
            rxBytes = if (net) TrafficStats.getUidRxBytes(uid).takeIf { it >= 0 } else null,
            txBytes = if (net) TrafficStats.getUidTxBytes(uid).takeIf { it >= 0 } else null,
        )
    }

    /** What was taken from [before] to [now]: each process's own difference (one new since counts from 0). */
    fun sample(before: ResourceCounters, now: ResourceCounters): ResourceSample {
        val seconds = (now.at - before.at) / 1000.0
        if (seconds <= 0) return ResourceSample(null, now.rssBytes, null, null, null, null)
        fun delta(b: Map<Int, Long>?, n: Map<Int, Long>?): Long? =
            if (b == null || n == null) null else n.entries.sumOf { (pid, v) -> (v - (b[pid] ?: 0)).coerceAtLeast(0) }
        fun perSec(bytes: Long?) = bytes?.let { (it / seconds).toLong() }
        fun total(b: Long?, n: Long?) = if (b == null || n == null) null else (n - b).coerceAtLeast(0)
        // Known before: a process new meanwhile counts from 0 (its whole time is within this sample, near enough)
        val ticks = delta(before.cpuTicks, now.cpuTicks) ?: 0
        return ResourceSample(
            cpuPercent = (ticks / (seconds * CLOCK_TICKS * CORES) * 100).toFloat().coerceIn(0f, 100f),
            rssBytes = now.rssBytes,
            readPerSec = perSec(delta(before.readBytes, now.readBytes)),
            writePerSec = perSec(delta(before.writtenBytes, now.writtenBytes)),
            rxPerSec = perSec(total(before.rxBytes, now.rxBytes)),
            txPerSec = perSec(total(before.txBytes, now.txBytes)),
        )
    }
}
