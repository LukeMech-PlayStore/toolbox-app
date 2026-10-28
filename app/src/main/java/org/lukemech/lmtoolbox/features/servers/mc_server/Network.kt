package org.lukemech.lmtoolbox.features.servers.mc_server

import org.json.JSONObject

/**
 * The domain players reach the server at through its Cloudflare tunnel (with Modflared); the phone's own
 * network and its tailnet are any server's (see Reach).
 */
object Network {
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
}
