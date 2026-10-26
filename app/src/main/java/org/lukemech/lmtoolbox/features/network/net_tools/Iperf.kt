package org.lukemech.lmtoolbox.features.network.net_tools

import android.content.Context
import org.json.JSONObject
import org.lukemech.lmtoolbox.linux.ExecResult
import org.lukemech.lmtoolbox.linux.InstallResult
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.LinuxTasks

/** What iperf3 tells as it measures, as [IperfParser] reads it (from its JSON stream, or a server's text). */
sealed interface IperfEvent {
    /** Connected to the server (a client), or a client connected (a server: a new test). */
    data class Connected(val peer: String) : IperfEvent
    /** A server waiting for a client. */
    data class Listening(val port: Int) : IperfEvent
    /** A second's (or so) transfer: [bitsPerSecond] over [start]..[end] seconds of the test. */
    data class Interval(val start: Double, val end: Double, val bitsPerSecond: Double, val bytes: Long) : IperfEvent
    /**
     * The test's totals: what was sent and what arrived (TCP; one of them for a server, which sees its own side),
     * TCP's retransmits, UDP's jitter and loss.
     */
    data class Summary(
        val sentBitsPerSecond: Double?,
        val receivedBitsPerSecond: Double?,
        val bytes: Long?,
        val retransmits: Int? = null,
        val jitterMs: Double? = null,
        val lostPercent: Double? = null,
    ) : IperfEvent
    data class Error(val message: String) : IperfEvent
}

/**
 * iperf3's output, line by line. A client's is `--json-stream` (3.17+: an event per line, as it comes); a
 * server's is its text (`--forceflush`), since its JSON comes only once a test is over.
 */
object IperfParser {
    /** A `--json-stream` line: {"event": "start" | "interval" | "end" | "error", "data": ...}. */
    fun json(line: String): IperfEvent? {
        val o = runCatching { JSONObject(line.trim()) }.getOrNull() ?: return null
        return when (o.optString("event")) {
            "start" -> o.optJSONObject("data")?.optJSONArray("connected")?.optJSONObject(0)?.optString("remote_host")
                ?.takeIf { it.isNotEmpty() }?.let { IperfEvent.Connected(it) }
            "interval" -> o.optJSONObject("data")?.optJSONObject("sum")?.let { sum ->
                IperfEvent.Interval(sum.optDouble("start"), sum.optDouble("end"), sum.optDouble("bits_per_second"), sum.optLong("bytes"))
            }
            "end" -> o.optJSONObject("data")?.let { d ->
                val sent = d.optJSONObject("sum_sent")
                val received = d.optJSONObject("sum_received")
                // UDP's: its loss and jitter in "sum" (seen by the receiving side)
                val udp = d.optJSONObject("sum")?.takeIf { it.has("jitter_ms") }
                IperfEvent.Summary(
                    sent?.optDouble("bits_per_second")?.takeIf { !it.isNaN() } ?: udp?.optDouble("bits_per_second"),
                    received?.optDouble("bits_per_second")?.takeIf { !it.isNaN() },
                    (sent ?: udp)?.optLong("bytes"),
                    sent?.takeIf { it.has("retransmits") }?.optInt("retransmits"),
                    udp?.optDouble("jitter_ms"),
                    udp?.optDouble("lost_percent"),
                )
            }
            "error" -> IperfEvent.Error(o.optString("data").removePrefix("error - ").trim())
            else -> null
        }
    }

    /**
     * A server's text, a test after another: its streams' lines counted as they connect, so that with several
     * only their [SUM] counts.
     */
    class Text {
        private var streams = 0

        fun parse(line: String): IperfEvent? {
            val l = line.trim()
            LISTENING.find(l)?.let { return IperfEvent.Listening(it.groupValues[1].toInt()) }
            ACCEPTED.find(l)?.let {
                streams = 0
                return IperfEvent.Connected(it.groupValues[1])
            }
            if (STREAM.containsMatchIn(l)) {
                streams++
                return null
            }
            INTERVAL.find(l)?.let { m ->
                val sum = m.groupValues[1] == "SUM"
                if (sum != streams > 1) return null
                val rate = m.groupValues[6].toDouble() * prefix(m.groupValues[7], 1000.0)
                val bytes = (m.groupValues[4].toDouble() * prefix(m.groupValues[5], 1024.0)).toLong()
                val end = m.groupValues[3].toDouble()
                return when (m.groupValues[8].trim().substringAfterLast(' ')) {
                    "sender" -> IperfEvent.Summary(rate, null, bytes)
                    "receiver" -> IperfEvent.Summary(null, rate, bytes)
                    else -> IperfEvent.Interval(m.groupValues[2].toDouble(), end, rate, bytes)
                }
            }
            ERROR.find(l)?.let { return IperfEvent.Error(it.groupValues[1].trim()) }
            return null
        }

        private fun prefix(p: String, base: Double) = when (p) {
            "K" -> base
            "M" -> base * base
            "G" -> base * base * base
            "T" -> base * base * base * base
            else -> 1.0
        }

        private companion object {
            val LISTENING = Regex("""^Server listening on (\d+)""")
            val ACCEPTED = Regex("""^Accepted connection from ([^,\s]+)""")
            val STREAM = Regex("""^\[\s*\d+]\s+local\s+\S+\s+port\s+\d+\s+connected""")
            // "[  5]   0.00-1.00   sec  10.9 MBytes  91.3 Mbits/sec", "[SUM] ... receiver", UDP's jitter and loss after it
            val INTERVAL = Regex("""^\[\s*(\d+|SUM)]\s+([\d.]+)-([\d.]+)\s+sec\s+([\d.]+)\s+([KMGT]?)Bytes\s+([\d.]+)\s+([KMGT]?)bits/sec(.*)$""")
            val ERROR = Regex("""^iperf3: (?:error - )?(.+)$""")
        }
    }
}

/** A test's state as its events come ([add]): its peer, its intervals, its totals; failed, why. */
data class IperfState(
    val peer: String? = null,
    val listening: Int? = null,
    val intervals: List<IperfEvent.Interval> = emptyList(),
    val summary: IperfEvent.Summary? = null,
    val error: String? = null,
) {
    /** The latest second's rate. */
    val now get() = intervals.lastOrNull()?.bitsPerSecond

    fun add(event: IperfEvent): IperfState = when (event) {
        // A server's next client: its own test
        is IperfEvent.Connected -> if (listening != null) IperfState(peer = event.peer, listening = listening) else copy(peer = event.peer)
        is IperfEvent.Listening -> copy(listening = event.port)
        is IperfEvent.Interval -> copy(intervals = (intervals + event).takeLast(MAX_INTERVALS))
        // A server's sender and receiver lines come one by one
        is IperfEvent.Summary -> copy(
            summary = summary?.let { s ->
                s.copy(
                    sentBitsPerSecond = event.sentBitsPerSecond ?: s.sentBitsPerSecond,
                    receivedBitsPerSecond = event.receivedBitsPerSecond ?: s.receivedBitsPerSecond,
                    bytes = event.bytes ?: s.bytes,
                )
            } ?: event
        )
        is IperfEvent.Error -> copy(error = event.message)
    }

    private companion object {
        /** What the chart shows: a long test's last minutes. */
        const val MAX_INTERVALS = 300
    }
}

/**
 * iperf3, from the iperf3 part (the subsystem's): a client measuring to a server, or a server for another device's
 * client. The network tools' other tabs need nothing of Linux; this one installs the part when it's first used
 * ([install], asked first), and it goes with the tool's data.
 */
object Iperf {
    /** The part (and its Play module), the network tools' extraExtensions. */
    const val PART = "iperf3"
    const val PORT = 5201

    data class ClientOptions(
        val host: String,
        val port: Int = PORT,
        val seconds: Int = 10,
        val streams: Int = 1,
        val udp: Boolean = false,
        /** UDP's target rate, in Mbit/s (iperf3 sends 1 Mbit/s unless told). */
        val udpMbps: Int = 100,
        /** The server sends, the phone receives (-R). */
        val reverse: Boolean = false,
    )

    const val MAX_SECONDS = 3600
    const val MAX_STREAMS = 128

    fun clientCommand(o: ClientOptions): List<String> = buildList {
        addAll(listOf("iperf3", "-c", Hosts.clean(o.host), "-p", "${o.port}", "-t", "${o.seconds}", "-P", "${o.streams}"))
        if (o.udp) addAll(listOf("-u", "-b", "${o.udpMbps}M"))
        if (o.reverse) add("-R")
        addAll(listOf("--json-stream", "--forceflush"))
    }

    fun serverCommand(port: Int): List<String> = listOf("iperf3", "-s", "-p", "$port", "--forceflush")

    /** Whether the app ships iperf3 for this device (the subsystem's release has it, for its ABI). */
    fun isAvailable(context: Context) = LinuxParts.isAvailable(context, listOf(PART))

    /** Whether it's ready to run: installed, with the core. */
    fun isInstalled(context: Context) = LinuxParts.missingExtensions(context, listOf(PART)).isEmpty()

    /** Installs it for the network tools (asked first; a task in the bottom bar). */
    suspend fun install(context: Context): InstallResult = LinuxTasks.installParts(context, NetworkToolsFeature, listOf(PART))

    /**
     * Runs [cmd] (a client's or a server's) until it ends or the caller's cancelled (its page left), [onState]
     * getting the test as it goes. Failing, it's told in the bottom bar, as any command.
     */
    suspend fun run(context: Context, cmd: List<String>, server: Boolean, onState: (IperfState) -> Unit): ExecResult {
        val text = IperfParser.Text()
        var state = IperfState()
        val timeout = if (server) Long.MAX_VALUE else (cmd.getOrNull(cmd.indexOf("-t") + 1)?.toLongOrNull() ?: 10L) * 1000 + START_MS
        return LinuxTasks.run(context, cmd, NetworkToolsFeature, timeoutMs = timeout) { line ->
            val event = if (server) text.parse(line) else IperfParser.json(line)
            if (event != null) synchronized(this) {
                state = state.add(event)
                onState(state)
            }
        }
    }

    /** Time a client may take besides its test: connecting, and the server's totals. */
    private const val START_MS = 30_000L
}
