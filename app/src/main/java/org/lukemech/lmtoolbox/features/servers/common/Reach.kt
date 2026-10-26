package org.lukemech.lmtoolbox.features.servers.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Where others reach a server on the phone (the Minecraft server, the folder share): the phone's own network
 * and its tailnet; the tunnels' addresses are theirs (see ServerTunnels).
 */
object Reach {
    /**
     * The phone's IPv4 addresses others on the same network (Wi-Fi, Ethernet, the phone's hotspot)
     * connect to; not the mobile network's (behind the carrier's NAT, nobody reaches those).
     */
    fun lanAddresses(context: Context): List<String> {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val fromNetworks = runCatching {
            @Suppress("DEPRECATION") // allNetworks: every network, not just the default one (Wi-Fi with mobile data on)
            cm.allNetworks.filter { n ->
                // Not a VPN's (Tailscale's): it has the transports of the network under it, Wi-Fi's too
                cm.getNetworkCapabilities(n)?.let {
                    !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                        (it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                } == true
            }.flatMap { n -> cm.getLinkProperties(n)?.linkAddresses.orEmpty() }.map { it.address }
        }.getOrDefault(emptyList())
        // The hotspot's interface isn't a network of the phone's own
        val hotspot = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && (it.name.startsWith("ap") || it.name.startsWith("swlan") || it.name.startsWith("softap")) }
                .flatMap { it.inetAddresses.toList() }
        }.getOrDefault(emptyList())
        return (fromNetworks + hotspot).filterIsInstance<Inet4Address>().filter { !it.isLoopbackAddress }
            .mapNotNull { it.hostAddress }.distinct()
    }

    /**
     * The phone's tailnet addresses, while the Tailscale app connects it (its VPN): others on the same
     * tailnet reach it at them, from anywhere, with nothing to set up here. Tailscale's are in 100.64.0.0/10,
     * as carriers' NAT addresses are: just a VPN's (or a tun interface's) count.
     */
    fun tailscaleAddresses(context: Context): List<String> {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val fromVpn = runCatching {
            @Suppress("DEPRECATION")
            cm.allNetworks.filter { n -> cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
                .flatMap { n -> cm.getLinkProperties(n)?.linkAddresses.orEmpty() }.map { it.address }
        }.getOrDefault(emptyList())
        // The app itself kept out of the VPN (split tunneling) doesn't see its network
        val fromTun = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && it.name.startsWith("tun") }
                .flatMap { it.inetAddresses.toList() }
        }.getOrDefault(emptyList())
        return (fromVpn + fromTun).filterIsInstance<Inet4Address>()
            .filter { a -> a.address.let { it[0].toInt() and 0xFF == 100 && it[1].toInt() and 0xC0 == 64 } }
            .mapNotNull { it.hostAddress }.distinct()
    }

    /**
     * The tailnet's name of the phone at [address] (MagicDNS, e.g. phone.tail1234.ts.net), as Tailscale's
     * DNS answers; null without MagicDNS. Blocking.
     */
    fun tailscaleName(address: String): String? = runCatching {
        InetAddress.getByName(address).canonicalHostName.trimEnd('.').takeIf { it != address && it.endsWith(".ts.net") }
    }.getOrNull()

    /** A hostname as typed, without what's not a hostname (a scheme, a port, a path). */
    fun hostnameOf(input: String): String =
        input.trim().substringAfter("://").substringBefore('/').substringBefore(':').trim('.').lowercase()
}
