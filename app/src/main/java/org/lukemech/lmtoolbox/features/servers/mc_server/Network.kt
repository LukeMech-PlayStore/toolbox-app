package org.lukemech.lmtoolbox.features.servers.mc_server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface
import org.json.JSONObject

/** Where players reach the server: the phone's own network, and the domain of its tunnel. */
object Network {
    /**
     * The phone's IPv4 addresses players on the same network (Wi-Fi, Ethernet, the phone's hotspot)
     * connect to; not the mobile network's (behind the carrier's NAT, nobody reaches those).
     */
    fun lanAddresses(context: Context): List<String> {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val fromNetworks = runCatching {
            @Suppress("DEPRECATION") // allNetworks: every network, not just the default one (Wi-Fi with mobile data on)
            cm.allNetworks.filter { n ->
                cm.getNetworkCapabilities(n)?.let {
                    it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
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

    /** What the tunnel's hostname has for Modflared, see [checkDomain]. */
    sealed interface DomainCheck {
        /** The hostname doesn't exist (no record of it in DNS). */
        data object NotFound : DomainCheck
        /** It exists, without Modflared's TXT record: players' Modflared won't know to use the tunnel. */
        data object NoTxt : DomainCheck
        /** Modflared's record: the tunnel on this hostname, or [route]'s. */
        data class Ready(val route: String?) : DomainCheck
        data class Failed(val message: String) : DomainCheck
    }

    /**
     * Asks Cloudflare's DNS over HTTPS (as players' DNS would answer) what [hostname] has: Modflared
     * looks for a TXT record "cloudflared-use-tunnel" (the tunnel on the same hostname) or
     * "cloudflared-route=<hostname>" on the hostname players type.
     */
    suspend fun checkDomain(hostname: String): DomainCheck = runCatching {
        fun query(type: String) = "https://cloudflare-dns.com/dns-query?name=${Http.encode(hostname)}&type=$type"
        val txt = JSONObject(Http.text(query("TXT"), accept = "application/dns-json"))
        // 3: NXDOMAIN
        if (txt.optInt("Status") == 3) return@runCatching DomainCheck.NotFound
        val records = txt.optJSONArray("Answer")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
            .filter { it.optInt("type") == 16 }
            .map { it.optString("data").trim().removeSurrounding("\"") }
        records.firstOrNull { it == "cloudflared-use-tunnel" }?.let { return@runCatching DomainCheck.Ready(null) }
        records.firstOrNull { it.startsWith("cloudflared-route=") }?.let { return@runCatching DomainCheck.Ready(it.substringAfter('=')) }
        // Something there at all (a proxied CNAME to the tunnel shows as Cloudflare's addresses)?
        val a = JSONObject(Http.text(query("A"), accept = "application/dns-json"))
        if (a.optJSONArray("Answer")?.length()?.let { it > 0 } == true) DomainCheck.NoTxt else DomainCheck.NotFound
    }.getOrElse { DomainCheck.Failed(it.message ?: it.javaClass.simpleName) }

    /** A hostname as typed, without what's not a hostname (a scheme, a port, a path). */
    fun hostnameOf(input: String): String =
        input.trim().substringAfter("://").substringBefore('/').substringBefore(':').trim('.').lowercase()
}
