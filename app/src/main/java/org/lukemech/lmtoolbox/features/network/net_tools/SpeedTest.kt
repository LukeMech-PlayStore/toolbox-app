package org.lukemech.lmtoolbox.features.network.net_tools

import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The internet's speed, as speedtest.net measures it, on Cloudflare's speed test servers (speed.cloudflare.com: the
 * nearest of its data centers, no key needed): the ping and its jitter, then how fast it downloads, then uploads, a
 * few connections at once for [PHASE_MS] each, the rate as it goes.
 */
object SpeedTest {
    enum class Phase { Latency, Download, Upload, Done }

    /** Who measures: Cloudflare's data center ([city], its airport [code]) and who the phone's connected through. */
    data class Server(val city: String?, val code: String?, val isp: String?)

    data class Result(
        val phase: Phase = Phase.Latency,
        val server: Server? = null,
        val pingMs: Double? = null,
        val jitterMs: Double? = null,
        /** Bits a second, once its phase is over. */
        val downloadBps: Double? = null,
        val uploadBps: Double? = null,
        /** The phase's rate now (the last second's), and each [SAMPLE_MS]'s so far: its chart. */
        val nowBps: Double? = null,
        val samples: List<Double> = emptyList(),
        /** The whole test's, 0 to 1. */
        val progress: Float = 0f,
        val failure: String? = null,
    ) {
        val done get() = phase == Phase.Done
    }

    private const val HOST = "speed.cloudflare.com"
    private const val BASE = "https://$HOST"
    /** Round trips for the ping (after one more, its DNS's and first connection's: not counted). */
    private const val PINGS = 20
    const val PHASE_MS = 10_000L
    /** A connection's slow start, not counted in the phase's rate (as speedtest.net leaves its start out). */
    private const val WARMUP_MS = 2_000L
    private const val STREAMS = 4
    /** The most the server sends at once (more's refused); a request after another until the phase is over. */
    private const val DOWN_BYTES = 25_000_000
    private const val UP_BYTES = 8_000_000
    const val SAMPLE_MS = 250L
    private const val TIMEOUT_MS = 10_000
    // The progress bar's share of each phase: the ping's quick
    private const val LATENCY_SHARE = 0.1f

    fun run(): Flow<Result> = channelFlow {
        var r = Result()
        send(r)
        try {
            r = r.copy(server = runCatching { server() }.getOrNull())
            send(r)
            val pings = latency { done -> send(r.copy(progress = LATENCY_SHARE * done / PINGS)) }
            r = r.copy(pingMs = median(pings), jitterMs = jitter(pings), phase = Phase.Download, progress = LATENCY_SHARE)
            send(r)
            val half = (1 - LATENCY_SHARE) / 2
            val down = throughput(::download) { now, samples, f -> send(r.copy(nowBps = now, samples = samples, progress = LATENCY_SHARE + half * f)) }
            r = r.copy(downloadBps = down, phase = Phase.Upload, nowBps = null, samples = emptyList(), progress = LATENCY_SHARE + half)
            send(r)
            val up = throughput(::upload) { now, samples, f -> send(r.copy(nowBps = now, samples = samples, progress = LATENCY_SHARE + half * (1 + f))) }
            r = r.copy(uploadBps = up, phase = Phase.Done, nowBps = null, samples = emptyList(), progress = 1f)
            send(r)
        } catch (e: IOException) {
            send(r.copy(phase = Phase.Done, nowBps = null, failure = e.message ?: e.javaClass.simpleName))
        }
    }.conflate().flowOn(Dispatchers.IO)

    /** The data center and the provider, as Cloudflare sees the phone (it answers only its own page: its Referer). */
    private fun server(): Server {
        val c = connect("$BASE/meta").apply { setRequestProperty("Referer", "$BASE/") }
        val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val colo = o.optJSONObject("colo")
        return Server(
            city = colo?.optString("city")?.ifBlank { null },
            code = colo?.optString("iata")?.ifBlank { null },
            isp = o.optString("asOrganization").ifBlank { null },
        )
    }

    /**
     * Round trips as a TCP connection's handshake takes them: one there and back, nothing of the server's in it (an
     * HTTP request's answer waits on the server's work, as long as the trip or longer).
     */
    private suspend fun latency(onPing: suspend (done: Int) -> Unit): List<Double> {
        val address = withContext(Dispatchers.IO) { InetAddress.getByName(HOST) }
        val times = mutableListOf<Double>()
        for (i in 0..PINGS) {
            val start = System.nanoTime()
            Socket().use { it.connect(InetSocketAddress(address, 443), TIMEOUT_MS) }
            if (i > 0) {
                times += (System.nanoTime() - start) / 1e6
                onPing(i)
            }
        }
        return times
    }

    /** The middle one: a slow trip or two (the radio waking) don't count. */
    fun median(values: List<Double>): Double? = values.sorted().let { s ->
        if (s.isEmpty()) null else if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** How much a round trip differs from the one before, on average (as speedtest.net's jitter). */
    fun jitter(values: List<Double>): Double? =
        if (values.size < 2) null else values.zipWithNext { a, b -> abs(b - a) }.average()

    /**
     * [STREAMS] connections doing [transfer] again and again for [PHASE_MS], [onSample] getting the rate now, every
     * [SAMPLE_MS]'s so far and how far the phase is; the rate after its [WARMUP_MS], in bits a second.
     */
    private suspend fun throughput(
        transfer: (bytes: AtomicLong, open: MutableSet<HttpURLConnection>, end: Long) -> Unit,
        onSample: suspend (now: Double, samples: List<Double>, fraction: Float) -> Unit,
    ): Double = coroutineScope {
        val bytes = AtomicLong()
        val open: MutableSet<HttpURLConnection> = Collections.synchronizedSet(mutableSetOf())
        val start = System.nanoTime()
        val end = start + PHASE_MS * 1_000_000
        var failure: IOException? = null
        val workers = List(STREAMS) {
            launch(Dispatchers.IO) {
                while (isActive && System.nanoTime() < end) {
                    try {
                        transfer(bytes, open, end)
                    } catch (e: IOException) {
                        // Closed at the end, or the connection's gone: again (the phase fails if none got through)
                        failure = e
                        if (System.nanoTime() < end) delay(SAMPLE_MS.milliseconds)
                    }
                }
            }
        }
        val samples = mutableListOf<Double>()
        // Where each sample was taken (time, bytes): the rate now is the last second's
        val marks = ArrayDeque<Pair<Long, Long>>().apply { add(start to 0L) }
        var warm: Pair<Long, Long>? = null
        var last = start to 0L
        try {
            while (System.nanoTime() < end) {
                delay(SAMPLE_MS.milliseconds)
                val now = System.nanoTime() to bytes.get()
                val (t0, b0) = marks.last()
                samples += rate(now.second - b0, now.first - t0)
                marks.addLast(now)
                while (marks.size > 1 && now.first - marks.first().first > 1_000_000_000L) marks.removeFirst()
                if (warm == null && now.first - start >= WARMUP_MS * 1_000_000) warm = now
                last = now
                val (ts, bs) = marks.first()
                onSample(rate(now.second - bs, now.first - ts), samples.toList(), ((now.first - start) / (PHASE_MS * 1e6)).toFloat().coerceIn(0f, 1f))
            }
        } finally {
            // Unblocks the reads and writes under way (stopped, or the phase over)
            synchronized(open) { open.toList() }.forEach { runCatching { it.disconnect() } }
            workers.forEach { it.cancel() }
        }
        if (last.second == 0L) throw failure ?: IOException("No data")
        val from = warm ?: (start to 0L)
        rate(last.second - from.second, last.first - from.first)
    }

    private fun rate(bytes: Long, nanos: Long): Double = if (nanos <= 0) 0.0 else bytes * 8 / (nanos / 1e9)

    /** One download, counted as it comes, until the phase is over. */
    private fun download(bytes: AtomicLong, open: MutableSet<HttpURLConnection>, end: Long) {
        val c = connect("$BASE/__down?bytes=$DOWN_BYTES")
        open += c
        try {
            c.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (System.nanoTime() < end) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    bytes.addAndGet(n.toLong())
                }
            }
        } finally {
            open -= c
        }
    }

    /** Random bytes to send: nothing on the way can make them smaller. */
    private val payload by lazy { Random.nextBytes(64 * 1024) }

    /** One upload, counted as it's sent, until the phase is over (then cut off). */
    private fun upload(bytes: AtomicLong, open: MutableSet<HttpURLConnection>, end: Long) {
        val c = connect("$BASE/__up").apply {
            requestMethod = "POST"
            doOutput = true
            setFixedLengthStreamingMode(UP_BYTES)
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        open += c
        try {
            val out = c.outputStream
            var left = UP_BYTES
            while (left > 0) {
                if (System.nanoTime() >= end) {
                    c.disconnect()
                    return
                }
                val n = minOf(left, payload.size)
                out.write(payload, 0, n)
                bytes.addAndGet(n.toLong())
                left -= n
            }
            out.close()
            c.inputStream.use { it.readBytes() }
        } finally {
            open -= c
        }
    }

    private fun connect(url: String) = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        useCaches = false
    }
}
