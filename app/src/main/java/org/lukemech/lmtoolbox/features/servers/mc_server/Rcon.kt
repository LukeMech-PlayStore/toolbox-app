package org.lukemech.lmtoolbox.features.servers.mc_server

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.lukemech.lmtoolbox.linux.LinuxEnv

/**
 * Minecraft's remote console, on the phone itself: how the app sends the server commands (Linux
 * commands run without an input, see [LinuxEnv.exec]). A connection per command: few and far between.
 */
internal class Rcon(private val port: Int, private val password: String) {
    private companion object {
        const val LOGIN = 3
        const val COMMAND = 2
        const val AUTH_RESPONSE = 2
        const val TIMEOUT_MS = 8_000
    }

    /** Runs [command] (without the slash), returning what the server answered; throws when it can't. */
    fun command(command: String): String = Socket().use { socket ->
        socket.connect(InetSocketAddress("127.0.0.1", port), 3_000)
        socket.soTimeout = TIMEOUT_MS
        val out = socket.getOutputStream()
        val input = DataInputStream(socket.getInputStream().buffered())

        send(out, 1, LOGIN, password)
        // The answer to logging in (some servers send an empty one before it)
        while (true) {
            val (id, type, _) = read(input)
            if (type != AUTH_RESPONSE) continue
            if (id == -1) throw IOException("RCON password rejected")
            break
        }

        send(out, 2, COMMAND, command)
        val answer = StringBuilder(read(input).third)
        // Long answers come in several packets, one right after the other
        socket.soTimeout = 150
        try {
            while (true) answer.append(read(input).third)
        } catch (_: SocketTimeoutException) {
        } catch (_: EOFException) {
        }
        answer.toString()
    }

    private fun send(out: OutputStream, id: Int, type: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val packet = ByteBuffer.allocate(4 + 4 + 4 + bytes.size + 2).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(4 + 4 + bytes.size + 2).putInt(id).putInt(type).put(bytes).put(0).put(0)
        out.write(packet.array())
        out.flush()
    }

    /** A packet: its id, type and body. */
    private fun read(input: DataInputStream): Triple<Int, Int, String> {
        fun int(): Int = Integer.reverseBytes(input.readInt())
        val length = int()
        if (length < 10 || length > 1 shl 20) throw IOException("Bad RCON packet ($length bytes)")
        val id = int()
        val type = int()
        val body = ByteArray(length - 10).also { input.readFully(it) }
        input.skipTerminator()
        return Triple(id, type, String(body, Charsets.UTF_8))
    }

    /** The two null bytes after a packet's body. */
    private fun InputStream.skipTerminator() = repeat(2) { if (read() < 0) throw EOFException() }
}
