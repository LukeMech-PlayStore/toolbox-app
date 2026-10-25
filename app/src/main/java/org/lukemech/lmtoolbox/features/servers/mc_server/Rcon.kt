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
        /** A type no server knows: answered ("Unknown request 64") after what came before it, the answer's end. */
        const val MARKER = 100
        const val MARKER_ID = 3
        const val TIMEOUT_MS = 8_000
        /** How long a command's answer has to start before it's taken as none (Forge's say answers nothing). */
        const val QUIET_MS = 1_000
        /**
         * How long its answer may take: the command runs on the server's main thread, in its turn, which a server
         * just started (Forge's, on a slow phone) can keep busy for longer than [TIMEOUT_MS]
         */
        const val ANSWER_MS = 60_000
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
        // Its answer: in several packets if long, none at all for some (Forge's say). Then a marker, answered in
        // turn, ends it; sent once the command's surely read (the server takes a packet a read, two sent together
        // lose the second): its answer starting, or a while without one
        val answer = StringBuilder()
        var answered = false
        socket.soTimeout = QUIET_MS
        try {
            answer.append(read(input).third)
            answered = true
        } catch (_: SocketTimeoutException) {
        }
        try {
            send(out, MARKER_ID, MARKER, "")
        } catch (e: IOException) {
            if (answered) return@use answer.toString() else throw e
        }
        socket.soTimeout = ANSWER_MS
        while (true) {
            val (id, _, body) = try {
                read(input)
            } catch (e: IOException) {
                // Closed once it answered ("stop"): that answer's all
                if (answered) break else throw e
            }
            if (id == MARKER_ID) break
            answer.append(body)
            answered = true
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
