package org.lukemech.lmtoolbox.features.network.net_tools

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Hosts as typed: looked up, their names looked up back, and whether they're on the local network. */
object Hosts {
    /** A host as typed, without what isn't the host (a scheme, a path, a port; an IPv6 address keeps its colons). */
    fun clean(input: String): String {
        val host = input.trim().substringAfter("://").substringBefore('/')
        return when {
            host.startsWith("[") -> host.substringAfter('[').substringBefore(']')
            host.count { it == ':' } == 1 -> host.substringBefore(':')
            else -> host
        }.trim().trimEnd('.')
    }

    /** [host]'s address (IPv4 first, as most networks reach it), null if it isn't known. */
    suspend fun resolve(host: String): InetAddress? = withContext(Dispatchers.IO) {
        val all = runCatching { runInterruptible { InetAddress.getAllByName(clean(host)).toList() } }
            .onFailure { if (it is CancellationException) throw it }.getOrNull().orEmpty()
        all.firstOrNull { it is Inet4Address } ?: all.firstOrNull()
    }

    /**
     * [address]'s name, as its DNS has it (reverse DNS, a router's for the devices on its network); null without
     * one, or after [LOOKUP_TIMEOUT] (a lookup that can't be stopped goes on in its thread meanwhile).
     */
    suspend fun nameOf(address: String): String? {
        // Not the caller's child: it'd wait for it
        val lookup = lookups.async { runCatching { InetAddress.getByName(address).canonicalHostName }.getOrNull() }
        return withTimeoutOrNull(LOOKUP_TIMEOUT) { lookup.await() }?.trimEnd('.')?.takeIf { it != address && it.isNotBlank() }
    }

    private val lookups = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Whether [address] is on a local network (private, link-local, unique local), which Android 17 lets an app
     * reach only with the user's leave (ACCESS_LOCAL_NETWORK).
     */
    fun isLocal(address: InetAddress): Boolean =
        address.isSiteLocalAddress || address.isLinkLocalAddress ||
            (address is Inet6Address && (address.address[0].toInt() and 0xFE) == 0xFC)

    private val LOOKUP_TIMEOUT = 3.seconds
}

/** The system's own programs (ping), not the Linux subsystem's. */
object Commands {
    /**
     * Runs [cmd] (its errors as its output), [onLine] getting each line it prints; its exit code. Canceled, it's
     * stopped.
     */
    suspend fun lines(cmd: List<String>, onLine: (String) -> Unit): Int = withContext(Dispatchers.IO) {
        val process = runCatching { ProcessBuilder(cmd).redirectErrorStream(true).start() }.getOrElse {
            onLine("${cmd.first()}: ${it.message}")
            return@withContext -1
        }
        process.outputStream.close()
        // Read in a thread of its own: a pipe's read doesn't stop for a coroutine's cancelling, the process's end does
        val reader = Thread {
            runCatching { process.inputStream.bufferedReader().forEachLine(onLine) }
        }.apply { start() }
        try {
            runInterruptible { process.waitFor() }.also { reader.join(1_000) }
        } finally {
            process.destroy()
        }
    }
}
