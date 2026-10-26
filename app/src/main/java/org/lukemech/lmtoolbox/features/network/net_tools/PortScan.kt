package org.lukemech.lmtoolbox.features.network.net_tools

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock

/** Well-known TCP ports by what usually listens there: the scans' names for what they find, and their presets. */
object Services {
    /** The common ones over TCP, by port (IANA's names, as tools show them; a few apps' own besides). */
    val NAMES: Map<Int, String> = mapOf(
        20 to "FTP data", 21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS", 80 to "HTTP",
        81 to "HTTP", 88 to "Kerberos", 110 to "POP3", 111 to "RPC", 119 to "NNTP", 135 to "MS RPC",
        139 to "NetBIOS", 143 to "IMAP", 179 to "BGP", 389 to "LDAP", 443 to "HTTPS", 445 to "SMB",
        465 to "SMTPS", 515 to "LPD", 548 to "AFP", 554 to "RTSP", 587 to "SMTP", 631 to "IPP", 636 to "LDAPS",
        853 to "DNS over TLS", 873 to "rsync", 993 to "IMAPS", 995 to "POP3S", 1080 to "SOCKS", 1194 to "OpenVPN",
        1433 to "MS SQL", 1521 to "Oracle", 1723 to "PPTP", 1883 to "MQTT", 2049 to "NFS", 2375 to "Docker",
        3000 to "HTTP (dev)", 3128 to "Squid", 3306 to "MySQL", 3389 to "RDP", 5000 to "UPnP / Synology", 5001 to "Synology",
        5060 to "SIP", 5201 to "iperf3", 5432 to "PostgreSQL",
        5555 to "ADB", 5900 to "VNC", 6379 to "Redis", 6443 to "Kubernetes", 6667 to "IRC", 7000 to "AirPlay",
        8000 to "HTTP", 8008 to "HTTP", 8009 to "Chromecast", 8080 to "HTTP proxy", 8081 to "HTTP", 8123 to "Home Assistant",
        8443 to "HTTPS", 8883 to "MQTT (TLS)", 8888 to "HTTP", 9000 to "HTTP", 9090 to "HTTP", 9100 to "Printer (raw)",
        9200 to "Elasticsearch", 11211 to "Memcached", 25565 to "Minecraft", 27017 to "MongoDB", 32400 to "Plex",
        49152 to "UPnP", 62078 to "iPhone sync",
    )

    /** The common ports, for a quick scan: every one named here. */
    val COMMON: List<Int> = NAMES.keys.sorted()

    /** What the LAN scan knocks at on each device: few, to be quick, the ones that tell what a device is. */
    val LAN: List<Int> = listOf(22, 53, 80, 139, 443, 445, 554, 631, 5000, 8008, 8009, 8080, 8443, 9100, 62078)

    fun name(port: Int): String? = NAMES[port]
}

/**
 * Port ranges as typed: "1-1024", "22, 80, 443", "8000-8100, 25565"; the ports in order, or null when something
 * in it isn't a port (or the range is backwards).
 */
object PortRanges {
    const val MAX_PORTS = 65_535

    fun parse(text: String): List<Int>? {
        val parts = text.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val ports = sortedSetOf<Int>()
        for (part in parts) {
            val ends = part.split('-', '–').map { it.trim() }
            val from = ends.first().toIntOrNull() ?: return null
            val to = (if (ends.size == 2) ends[1].toIntOrNull() else if (ends.size == 1) from else null) ?: return null
            if (from !in 1..MAX_PORTS || to !in 1..MAX_PORTS || to < from) return null
            ports.addAll(from..to)
        }
        return ports.toList()
    }
}

/** TCP connect scan: a port's open when it takes the connection. Root would allow quieter ways; this one needs none. */
object PortScan {
    data class Open(val port: Int, val service: String?, val ms: Long)

    data class Result(
        val address: String? = null,
        val total: Int = 0,
        val scanned: Int = 0,
        val open: List<Open> = emptyList(),
        val failure: String? = null,
        val done: Boolean = false,
    ) {
        val progress get() = if (total == 0) 0f else scanned.toFloat() / total
    }

    /** Connections tried at once: a phone copes, a router's connection table too. */
    private const val PARALLEL = 128

    fun run(host: String, ports: List<Int>, timeoutMs: Int): Flow<Result> = channelFlow {
        val address = Hosts.resolve(host) ?: run {
            send(Result(failure = "Unknown host", done = true))
            return@channelFlow
        }
        val lock = Mutex()
        var result = Result(address.hostAddress, total = ports.size)
        send(result)
        val scanned = AtomicInteger()
        val permits = Semaphore(PARALLEL)
        coroutineScope {
            ports.forEach { port ->
                // Taken before it's started: not a coroutine for each of 65535 ports at once
                permits.acquire()
                launch {
                    try {
                        val ms = connect(address, port, timeoutMs)
                        val done = scanned.incrementAndGet()
                        lock.withLock {
                            result = result.copy(
                                scanned = done,
                                open = if (ms != null) (result.open + Open(port, Services.name(port), ms)).sortedBy { it.port } else result.open
                            )
                            send(result)
                        }
                    } finally {
                        permits.release()
                    }
                }
            }
        }
        send(result.copy(done = true))
    }.conflate().flowOn(Dispatchers.IO)

    /** How long [port] of [address] took to take a connection; null if it didn't (refused, or no answer in [timeoutMs]). */
    internal suspend fun connect(address: InetAddress, port: Int, timeoutMs: Int): Long? = runInterruptible(Dispatchers.IO) {
        val start = System.nanoTime()
        runCatching { Socket().use { it.connect(InetSocketAddress(address, port), timeoutMs) } }
            .map { (System.nanoTime() - start) / 1_000_000 }.getOrNull()
    }
}
