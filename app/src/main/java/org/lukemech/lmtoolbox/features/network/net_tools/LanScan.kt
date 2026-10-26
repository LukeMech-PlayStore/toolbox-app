package org.lukemech.lmtoolbox.features.network.net_tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** An IPv4 network: its first address ([network], as an int) and its prefix length. */
data class Subnet(val network: Int, val prefix: Int) {
    val size get() = if (prefix >= 31) 0 else (1L shl (32 - prefix)).toInt() - 2

    /** Its hosts' addresses (not the network's own, nor its broadcast). */
    fun hosts(): List<Int> = (1..size).map { network + it }

    override fun toString() = "${text(network)}/$prefix"

    companion object {
        /** Hosts scanned at most: a /22; on a larger network, the /24 the phone's on. */
        const val MAX_HOSTS = 1022

        fun of(address: Int, prefix: Int): Subnet {
            val p = if (prefix in 1..32 && (1L shl (32 - prefix)) - 2 <= MAX_HOSTS) prefix else 24
            val mask = if (p == 0) 0 else (-1 shl (32 - p))
            return Subnet(address and mask, p)
        }

        fun toInt(address: Inet4Address) = address.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }

        fun text(address: Int) = "${address ushr 24 and 0xFF}.${address ushr 16 and 0xFF}.${address ushr 8 and 0xFF}.${address and 0xFF}"
    }
}

/**
 * What's on the phone's Wi-Fi (or Ethernet): every address of its subnet (from its link's properties) knocked at
 * at once, by ICMP's echo (isReachable) and TCP connections to a few telling ports ([Services.LAN]): a device
 * answering either, even refusing the connection, is there. Its name from reverse DNS (the router's) or mDNS (what
 * it announces, NsdManager). No MAC addresses (and so no makers): Android 10+ keeps the ARP table (/proc/net/arp)
 * from apps; what a device is, is guessed from its ports and services ([Kind]).
 */
object LanScan {
    /** What a device seems to be, from what it answers and announces. */
    enum class Kind { Phone, Router, Printer, Cast, Apple, Computer, Storage, Camera, Server, SmartHome }

    data class Device(
        val address: String,
        val name: String? = null,
        val ports: List<Int> = emptyList(),
        /** mDNS service types it announces (e.g. _ipp._tcp). */
        val services: Set<String> = emptySet(),
        val isGateway: Boolean = false,
        val isSelf: Boolean = false,
    ) {
        val kind: Kind? get() = guess(this)
        val sortKey get() = runCatching { Subnet.toInt(InetAddress.getByName(address) as Inet4Address).toUInt() }.getOrDefault(UInt.MAX_VALUE)
    }

    /** The phone's own network to scan: its address there, its subnet, its router. */
    data class Lan(val self: String, val subnet: Subnet, val gateway: String?)

    data class Result(
        val lan: Lan? = null,
        val total: Int = 0,
        val scanned: Int = 0,
        val devices: List<Device> = emptyList(),
        val done: Boolean = false,
    ) {
        val progress get() = if (total == 0) 0f else scanned.toFloat() / total
    }

    private const val PARALLEL = 32
    private const val REACH_MS = 1000
    private const val CONNECT_MS = 600
    private val NSD_TIME = 3.seconds

    /** mDNS's services that tell what a device is; browsed while the scan runs. */
    private val NSD_TYPES = listOf(
        "_googlecast._tcp", "_airplay._tcp", "_raop._tcp", "_companion-link._tcp", "_ipp._tcp", "_printer._tcp",
        "_smb._tcp", "_ssh._tcp", "_http._tcp", "_hap._tcp", "_spotify-connect._tcp", "_workstation._tcp",
    )

    /** The phone's Wi-Fi or Ethernet network (not a VPN's, nor mobile data's): null without one. */
    fun lan(context: Context): Lan? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION") // allNetworks: Wi-Fi's too while the default one is mobile data
        val networks = runCatching { cm.allNetworks.toList() }.getOrDefault(emptyList())
        for (n in networks) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) continue
            val props = cm.getLinkProperties(n) ?: continue
            val link = props.linkAddresses.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress } ?: continue
            val gateway = props.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
            return Lan(link.address.hostAddress!!, Subnet.of(Subnet.toInt(link.address as Inet4Address), link.prefixLength), gateway)
        }
        return null
    }

    fun run(context: Context, lan: Lan): Flow<Result> = channelFlow {
        val hosts = lan.subnet.hosts().map(Subnet::text)
        val lock = Mutex()
        var result = Result(lan, total = hosts.size)
        suspend fun update(change: (Result) -> Result) = lock.withLock {
            result = change(result)
            send(result)
        }
        /** Adds to (or makes) [address]'s device. */
        suspend fun device(address: String, change: (Device) -> Device) = update { r ->
            val there = r.devices.firstOrNull { it.address == address }
            val new = change(there ?: Device(address, isGateway = address == lan.gateway, isSelf = address == lan.self))
            r.copy(devices = (r.devices.filterNot { it.address == address } + new).sortedBy { it.sortKey })
        }

        send(result)
        coroutineScope {
            // What devices announce, while they're knocked at
            launch {
                announced(context).forEach { (address, info) ->
                    if (address in hosts) device(address) { d -> d.copy(name = d.name ?: info.first, services = d.services + info.second) }
                }
            }
            val permits = Semaphore(PARALLEL)
            hosts.forEach { ip ->
                launch {
                    val ports = permits.withPermit { if (ip == lan.self) emptyList<Int>() to true else probe(ip) }
                    val (open, alive) = ports
                    if (alive) {
                        device(ip) { it.copy(ports = open) }
                        launch { Hosts.nameOf(ip)?.let { n -> device(ip) { it.copy(name = n) } } }
                    }
                    update { it.copy(scanned = it.scanned + 1) }
                }
            }
        }
        update { it.copy(done = true) }
    }.conflate().flowOn(Dispatchers.IO)

    /** [ip]'s ports that took a connection, and whether it answered at all. */
    private suspend fun probe(ip: String): Pair<List<Int>, Boolean> = coroutineScope {
        val address = InetAddress.getByName(ip)
        val echo = async { runInterruptible { runCatching { address.isReachable(REACH_MS) }.getOrDefault(false) } }
        val knocks = Services.LAN.map { port -> async { port to knock(address, port) } }.awaitAll()
        val open = knocks.filter { it.second == Knock.Open }.map { it.first }
        open to (echo.await() || knocks.any { it.second != Knock.Silent })
    }

    private enum class Knock { Open, Refused, Silent }

    /** A connection to [port]: taken, refused (a device is there all the same), or nothing back. */
    private suspend fun knock(address: InetAddress, port: Int): Knock = runInterruptible {
        try {
            Socket().use { it.connect(InetSocketAddress(address, port), CONNECT_MS) }
            Knock.Open
        } catch (e: ConnectException) {
            if (e.message?.contains("ECONNREFUSED") == true || e.message?.contains("refused", ignoreCase = true) == true) Knock.Refused else Knock.Silent
        } catch (_: Exception) {
            Knock.Silent
        }
    }

    /**
     * What devices announce over mDNS ([NSD_TYPES]) within [NSD_TIME]: by their address, their name and the types
     * of their services. Each found one is resolved in turn (Android before 14 resolves one at a time).
     */
    private suspend fun announced(context: Context): Map<String, Pair<String, Set<String>>> = coroutineScope {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return@coroutineScope emptyMap()
        val found = mutableListOf<NsdServiceInfo>()
        val listeners = NSD_TYPES.map { type ->
            object : NsdManager.DiscoveryListener {
                override fun onServiceFound(info: NsdServiceInfo) { synchronized(found) { found += info } }
                override fun onServiceLost(info: NsdServiceInfo) {}
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }.also { l -> runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l) } }
        }
        try {
            delay(NSD_TIME)
        } finally {
            listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        }
        val byAddress = mutableMapOf<String, Pair<String, Set<String>>>()
        for (info in synchronized(found) { found.toList() }) {
            val resolved = withTimeoutOrNull(NSD_TIME) { resolve(nsd, info) } ?: continue
            val type = info.serviceType.trim('.').substringBefore(".local")
            resolved.forEach { address ->
                val (name, types) = byAddress[address] ?: (info.serviceName to emptySet())
                byAddress[address] = name to (types + type)
            }
        }
        byAddress
    }

    /** [info]'s IPv4 addresses, resolved. */
    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): List<String> = suspendCancellableCoroutine { cont ->
        @Suppress("DEPRECATION") // resolveService: one callback, as on every Android the app runs on (14's own way is a subscription)
        runCatching {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (cont.isActive) cont.resume(emptyList())
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) serviceInfo.hostAddresses
                    else listOfNotNull(serviceInfo.host)
                    if (cont.isActive) cont.resume(addresses.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress })
                }
            })
        }.onFailure { if (cont.isActive) cont.resume(emptyList()) }
    }

    /** What [device] seems to be: what it announces first, then its ports, then its name; null if nothing tells. */
    fun guess(device: Device): Kind? {
        val s = device.services
        val p = device.ports.toSet()
        val name = device.name.orEmpty().lowercase()
        return when {
            device.isSelf -> Kind.Phone
            device.isGateway -> Kind.Router
            "_ipp._tcp" in s || "_printer._tcp" in s || 9100 in p || 631 in p -> Kind.Printer
            "_googlecast._tcp" in s || "_spotify-connect._tcp" in s || 8009 in p -> Kind.Cast
            "_companion-link._tcp" in s || "_airplay._tcp" in s || "_raop._tcp" in s || 62078 in p -> Kind.Apple
            "_hap._tcp" in s -> Kind.SmartHome
            554 in p -> Kind.Camera
            listOf("iphone", "ipad", "android", "galaxy", "pixel", "redmi", "xiaomi", "oneplus", "phone").any { it in name } -> Kind.Phone
            5000 in p || 5001 in p || listOf("nas", "synology", "qnap").any { it in name } -> Kind.Storage
            "_smb._tcp" in s || "_workstation._tcp" in s || 445 in p || 139 in p || listOf("desktop", "laptop", "macbook", "windows").any { it in name } -> Kind.Computer
            "_ssh._tcp" in s || 22 in p -> Kind.Server
            53 in p -> Kind.Router
            else -> null
        }
    }
}
