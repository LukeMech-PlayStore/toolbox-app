package org.lukemech.lmtoolbox.features.network.net_tools

import java.io.File
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible

/** A line of Android's ping (iputils), as [PingParser] reads it. */
sealed interface PingLine {
    /** "PING example.com (93.184.215.14) 56(84) bytes of data." */
    data class Header(val host: String, val address: String) : PingLine
    /** An echo reply: from [from], probe [seq], after [timeMs]. */
    data class Reply(val from: String, val seq: Int, val ttl: Int?, val timeMs: Double?, val bytes: Int) : PingLine
    /** A router on the way answering instead, e.g. "Time to live exceeded" (what traceroute's made of), "Destination Host Unreachable". */
    data class Error(val from: String, val seq: Int, val message: String) : PingLine
    /** "4 packets transmitted, 3 received, +1 errors, 25% packet loss, time 3004ms" */
    data class Summary(val transmitted: Int, val received: Int, val errors: Int, val lossPercent: Double) : PingLine
    /** "rtt min/avg/max/mdev = 11.981/12.349/12.876/0.345 ms" */
    data class Rtt(val min: Double, val avg: Double, val max: Double) : PingLine
    /** ping itself failing: "ping: unknown host x", "ping: socket: Permission denied", "connect: Network is unreachable". */
    data class Failure(val message: String) : PingLine
}

/** Android's ping's output, line by line: what it says, or null for what it says nothing of (blank, a "DUP!"'s header...). */
object PingParser {
    private val HEADER = Regex("""^PING6?\s+(\S+)\s+\(([^)\s]+)\)""")
    // The sender's name with its address in brackets, or the address alone (an IPv6 one has colons: up to ": icmp_")
    private val REPLY = Regex("""^(\d+) bytes from (.+?):? icmp_[rs]eq=(\d+)(?: ttl=(\d+))?(?: time=([\d.]+) ?ms)?""")
    private val ERROR = Regex("""^From (.+?):?\s+icmp_seq=(\d+)\s+(.+)$""")
    private val TRANSMITTED = Regex("""^(\d+) packets transmitted, (\d+) (?:packets )?received""")
    private val ERRORS = Regex("""\+(\d+) errors""")
    private val LOSS = Regex("""([\d.]+)% packet loss""")
    private val RTT = Regex("""(?:rtt|round-trip) min/avg/max(?:/\w+)? = ([\d.]+)/([\d.]+)/([\d.]+)""")
    private val FAILURE = Regex("""^(?:ping6?|connect|socket|sendmsg|bind): (.+)$""")

    fun parse(line: String): PingLine? {
        val l = line.trim()
        if (l.isEmpty()) return null
        HEADER.find(l)?.let { return PingLine.Header(it.groupValues[1], it.groupValues[2]) }
        REPLY.find(l)?.let { m ->
            return PingLine.Reply(
                address(m.groupValues[2]), m.groupValues[3].toInt(), m.groupValues[4].toIntOrNull(),
                m.groupValues[5].toDoubleOrNull(), m.groupValues[1].toInt()
            )
        }
        ERROR.find(l)?.let { m -> return PingLine.Error(address(m.groupValues[1]), m.groupValues[2].toInt(), m.groupValues[3].trim()) }
        TRANSMITTED.find(l)?.let { m ->
            val sent = m.groupValues[1].toInt()
            val received = m.groupValues[2].toInt()
            val loss = LOSS.find(l)?.groupValues?.get(1)?.toDoubleOrNull() ?: if (sent > 0) (sent - received) * 100.0 / sent else 0.0
            return PingLine.Summary(sent, received, ERRORS.find(l)?.groupValues?.get(1)?.toInt() ?: 0, loss)
        }
        RTT.find(l)?.let { m -> return PingLine.Rtt(m.groupValues[1].toDouble(), m.groupValues[2].toDouble(), m.groupValues[3].toDouble()) }
        // "ping: x: Name or service not known": what's after the host
        FAILURE.find(l)?.let { return PingLine.Failure(it.groupValues[1].substringAfterLast(": ").trim()) }
        return null
    }

    /** "router.lan (192.168.1.1)" → 192.168.1.1; an address alone as it is. */
    private fun address(from: String): String = from.substringAfterLast('(').substringBefore(')').trim()
}

/** A probe's row: its reply's time, or an error a router sent back; neither: lost. */
data class PingRow(val seq: Int, val timeMs: Double? = null, val ttl: Int? = null, val from: String? = null, val error: String? = null) {
    val isReply get() = timeMs != null
}

/**
 * A ping's results as they come ([add] each [PingLine]): a row per probe, the lost ones filled in once a later one
 * (or the summary) shows they got no answer, and the statistics of them all.
 */
data class PingResult(
    val address: String? = null,
    val rows: List<PingRow> = emptyList(),
    /** As ping's summary counts them (0 until it's printed). */
    val transmitted: Int = 0,
    /** Why ping couldn't (e.g. the host's unknown), as it said. */
    val failure: String? = null,
) {
    val sent get() = maxOf(transmitted, rows.maxOfOrNull { it.seq } ?: 0)
    val received get() = rows.count { it.isReply }
    val lossPercent: Double? get() = if (sent == 0) null else (sent - received) * 100.0 / sent
    private val times get() = rows.mapNotNull { it.timeMs }
    val min get() = times.minOrNull()
    val avg get() = times.takeIf { it.isNotEmpty() }?.average()
    val max get() = times.maxOrNull()

    fun add(line: PingLine): PingResult = when (line) {
        is PingLine.Header -> copy(address = line.address)
        is PingLine.Reply -> withRow(PingRow(line.seq, line.timeMs, line.ttl, line.from))
        is PingLine.Error -> withRow(PingRow(line.seq, from = line.from, error = line.message))
        is PingLine.Summary -> copy(transmitted = line.transmitted, rows = lostUpTo(line.transmitted + 1))
        is PingLine.Rtt -> this
        is PingLine.Failure -> copy(failure = line.message)
    }

    /** A probe's answer: in its place (a lost one's, if it came late); a second one (a "DUP!") isn't counted. */
    fun withRow(row: PingRow): PingResult {
        val there = rows.firstOrNull { it.seq == row.seq }
        return when {
            there == null -> copy(rows = (lostUpTo(row.seq) + row).sortedBy { it.seq })
            !there.isReply && there.error == null -> copy(rows = rows.map { if (it.seq == row.seq) row else it })
            else -> this
        }
    }

    /** The rows with every probe before [seq] that got nothing back as lost. */
    private fun lostUpTo(seq: Int): List<PingRow> {
        val seen = rows.map { it.seq }.toSet()
        // ping's probes count from 1
        return (rows + (1 until seq).filter { it !in seen }.map { PingRow(it) }).sortedBy { it.seq }
    }
}

/**
 * Ping: Android's own (/system/bin/ping, ping6 for IPv6), which apps may run without root (ICMP's datagram
 * sockets); where it isn't there (or can't open its socket), Java's [InetAddress.isReachable] instead: ICMP's
 * echo if the app may send it, else a TCP connection to port 7, timed by the app.
 */
object Ping {
    data class Options(
        val count: Int = 4,
        /** Seconds between probes; 0.2 at least, the least ping allows without root. */
        val interval: Double = 1.0,
        /** Bytes of data in each probe (ping's -s). */
        val size: Int = 56,
        /** Seconds a probe waits for its reply. */
        val timeout: Int = 2,
    )

    const val MIN_INTERVAL = 0.2
    const val MAX_SIZE = 65_507

    private const val PING = "/system/bin/ping"
    private const val PING6 = "/system/bin/ping6"

    /** Whether Android's ping is there to run (traceroute is made of it). */
    fun hasNative() = File(PING).canExecute()

    /** The ping binary for [address]'s IP version, if the system has it. */
    internal fun binary(address: InetAddress): String? =
        (if (address is Inet6Address) PING6 else PING).takeIf { File(it).canExecute() }

    /** The results of pinging [host] as they come; whether the system's ping ran ([native]) is told by [onNative]. */
    fun run(host: String, options: Options, onNative: (Boolean) -> Unit = {}): Flow<PingResult> = channelFlow {
        val address = Hosts.resolve(host) ?: run {
            send(PingResult(failure = "Unknown host"))
            return@channelFlow
        }
        var result = PingResult(address = address.hostAddress)
        send(result)
        val binary = binary(address)
        if (binary != null) {
            onNative(true)
            val cmd = listOf(
                binary, "-n", "-c", "${options.count}", "-i", format(options.interval.coerceAtLeast(MIN_INTERVAL)),
                "-s", "${options.size}", "-W", "${options.timeout}", address.hostAddress!!
            )
            val exit = Commands.lines(cmd) { line ->
                PingParser.parse(line)?.let { result = result.add(it); trySend(result) }
            }
            // Its socket refused (an Android without ping sockets for apps): Java's way, unless it got anywhere
            val refused = result.failure?.let { f -> DENIED.any { it in f } } == true
            if (!refused || result.rows.isNotEmpty()) {
                if (exit != 0 && result.rows.isEmpty() && result.failure == null) send(result.copy(failure = "ping exited with $exit"))
                else send(result)
                return@channelFlow
            }
            result = PingResult(address = address.hostAddress)
        }
        onNative(false)
        reachable(address, options) { result = result.withRow(it); send(result) }
        send(result.copy(transmitted = options.count))
    }.conflate().flowOn(Dispatchers.IO)

    /** [count] probes by isReachable, timed here: a reply's time, or lost. */
    private suspend fun reachable(address: InetAddress, options: Options, onRow: suspend (PingRow) -> Unit) {
        for (seq in 1..options.count) {
            val start = System.nanoTime()
            val ok = runInterruptible { runCatching { address.isReachable(options.timeout * 1000) }.getOrDefault(false) }
            val ms = (System.nanoTime() - start) / 1_000_000.0
            onRow(if (ok) PingRow(seq, ms, from = address.hostAddress) else PingRow(seq))
            if (seq < options.count) delay((options.interval * 1000 - if (ok) ms else 0.0).coerceAtLeast(0.0).toLong().milliseconds)
        }
    }

    /** ping's -i takes a dot, whatever the phone's language. */
    internal fun format(seconds: Double) = String.format(Locale.ROOT, "%.1f", seconds)

    /** What ping says when apps may not open its socket. */
    private val DENIED = listOf("Permission denied", "Operation not permitted")
}
