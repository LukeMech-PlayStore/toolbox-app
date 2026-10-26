package org.lukemech.lmtoolbox.features.network.net_tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Traceroute, made of Android's ping (no root, no Linux): a probe with a TTL of 1, 2, 3... is answered by the
 * router where it runs out ("From 10.0.0.1 icmp_seq=1 Time to live exceeded"), until one reaches the host. A
 * router's "Time to live exceeded" carries no time, so each one found is pinged itself for its round trips (most
 * answer; one that doesn't shows the address alone), and its name is looked up. Termux's traceroute and tracepath
 * would need the subsystem, and raw sockets PRoot doesn't give either.
 */
object Traceroute {
    /** A hop: [ttl], the router that answered (none: [address] null, "*"), its name, the round trips to it. */
    data class Hop(
        val ttl: Int,
        val address: String? = null,
        val name: String? = null,
        val rtts: List<Double> = emptyList(),
        /** Its probe is answered (or timed out). */
        val probed: Boolean = false,
        /** The host itself answered: the end. */
        val reached: Boolean = false,
    )

    data class Result(val address: String? = null, val hops: List<Hop> = emptyList(), val failure: String? = null, val done: Boolean = false)

    const val MAX_HOPS = 30
    /** Probes out at a time: a route's first hops at once, and no more than a few beyond its end. */
    private const val BATCH = 6
    private const val TIMEOUT_S = 2
    /** Round trips measured to each router found. */
    private const val RTT_PROBES = 3

    /** What [probe]'s ping said of one TTL: who answered, and whether that's the host. */
    internal fun answer(lines: List<String>): Pair<String?, Boolean> {
        for (line in lines) {
            when (val p = PingParser.parse(line)) {
                is PingLine.Reply -> return p.from to true
                is PingLine.Error -> return p.from to false
                else -> {}
            }
        }
        return null to false
    }

    fun run(host: String, maxHops: Int = MAX_HOPS): Flow<Result> = channelFlow {
        val address = Hosts.resolve(host) ?: run {
            send(Result(failure = "Unknown host", done = true))
            return@channelFlow
        }
        val binary = Ping.binary(address) ?: run {
            send(Result(address.hostAddress, failure = "No ping on this phone", done = true))
            return@channelFlow
        }
        val ip = address.hostAddress!!
        val lock = Mutex()
        var result = Result(ip)
        suspend fun update(change: (Result) -> Result) = lock.withLock {
            result = change(result)
            send(result)
        }
        fun Result.withHop(ttl: Int, change: (Hop) -> Hop) =
            copy(hops = hops.map { if (it.ttl == ttl) change(it) else it })

        send(result)
        coroutineScope {
            // What's looked up of a hop found goes on beside the next batches (theirs waits for their probes alone)
            val lookups = this
            var end = maxHops + 1
            for (batch in (1..maxHops).chunked(BATCH)) {
                if (batch.first() >= end) break
                update { r -> r.copy(hops = r.hops + batch.map { Hop(it) }) }
                coroutineScope {
                    batch.forEach { ttl ->
                        launch {
                            val lines = mutableListOf<String>()
                            Commands.lines(listOf(binary, "-n", "-c", "1", "-W", "$TIMEOUT_S", "-t", "$ttl", ip)) { synchronized(lines) { lines += it } }
                            val (from, reached) = answer(synchronized(lines) { lines.toList() })
                            if (reached) lock.withLock { end = minOf(end, ttl) }
                            update { r -> r.withHop(ttl) { it.copy(address = from, probed = true, reached = reached) } }
                            // Its name and round trips meanwhile, as the next hops are probed
                            if (from != null) {
                                lookups.launch { Hosts.nameOf(from)?.let { n -> update { r -> r.withHop(ttl) { it.copy(name = n) } } } }
                                lookups.launch { rtts(binary, from).takeIf { it.isNotEmpty() }?.let { t -> update { r -> r.withHop(ttl) { it.copy(rtts = t) } } } }
                            }
                        }
                    }
                }
            }
            // Past the host: the probes that also reached it
            update { r -> r.copy(hops = r.hops.filter { it.ttl <= end }) }
        }
        update { it.copy(done = true) }
    }.conflate().flowOn(Dispatchers.IO)

    /** The round trips to [address] (a router found), by a few pings of it; none if it doesn't answer pings. */
    private suspend fun rtts(binary: String, address: String): List<Double> {
        var result = PingResult()
        Commands.lines(listOf(binary, "-n", "-c", "$RTT_PROBES", "-i", Ping.format(Ping.MIN_INTERVAL), "-W", "$TIMEOUT_S", address)) { line ->
            PingParser.parse(line)?.let { synchronized(this) { result = result.add(it) } }
        }
        return result.rows.mapNotNull { it.timeMs }
    }
}
