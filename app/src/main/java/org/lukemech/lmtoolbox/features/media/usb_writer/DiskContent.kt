package org.lukemech.lmtoolbox.features.media.usb_writer

import java.io.EOFException
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Collections

/** Bytes read anywhere in them, as a file's are: an ISO, a file in it. */
interface RandomSource {
    val size: Long

    /** Exactly [len] bytes at [pos] into [buf] from [off] (fails past the end). */
    fun readFully(pos: Long, buf: ByteArray, off: Int = 0, len: Int = buf.size - off)

    /** [len] bytes from [start] on, as something to write. */
    fun range(start: Long = 0, len: Long = size - start): Content = RangeContent(this, start, len)
}

/** A file's channel (a picked file's descriptor), read at any position: safe from several threads at once. */
class ChannelSource(private val channel: FileChannel, override val size: Long = channel.size()) : RandomSource {
    override fun readFully(pos: Long, buf: ByteArray, off: Int, len: Int) {
        require(pos >= 0 && pos + len <= size) { "Past the end: $pos+$len of $size" }
        val bb = ByteBuffer.wrap(buf, off, len)
        var at = pos
        while (bb.hasRemaining()) {
            val n = channel.read(bb, at)
            if (n < 0) throw EOFException("End of file at $at")
            at += n
        }
    }
}

/** Bytes in memory as a source (the tests', a small file built here). */
class BytesSource(private val bytes: ByteArray) : RandomSource {
    override val size get() = bytes.size.toLong()
    override fun readFully(pos: Long, buf: ByteArray, off: Int, len: Int) {
        require(pos >= 0 && pos + len <= size) { "Past the end: $pos+$len of $size" }
        System.arraycopy(bytes, pos.toInt(), buf, off, len)
    }
}

/**
 * A file made of pieces of [source] (an ISO's file, as its extents have it): each piece [Piece.at] in it, or zeros
 * (an extent allocated but not recorded) where that's -1.
 */
class PiecesSource(private val source: RandomSource, private val pieces: List<Piece>) : RandomSource {
    class Piece(val at: Long, val length: Long)

    /** Where each piece starts in the file. */
    private val starts = LongArray(pieces.size + 1).also { s ->
        pieces.forEachIndexed { i, p -> s[i + 1] = s[i] + p.length }
    }
    override val size = starts.last()

    override fun readFully(pos: Long, buf: ByteArray, off: Int, len: Int) {
        require(pos >= 0 && pos + len <= size) { "Past the end: $pos+$len of $size" }
        var at = pos
        var o = off
        var left = len
        // The piece [at] is in: the last one starting at or before it
        var i = starts.binarySearch(at).let { if (it >= 0) it else -it - 2 }
        while (left > 0) {
            val piece = pieces[i]
            val inPiece = at - starts[i]
            val n = minOf(left.toLong(), piece.length - inPiece).toInt()
            if (piece.at < 0) java.util.Arrays.fill(buf, o, o + n, 0)
            else source.readFully(piece.at + inPiece, buf, o, n)
            at += n; o += n; left -= n
            i++
        }
    }
}

/** What's written somewhere on a drive: [length] bytes, read in order from what [open] gives. */
interface Content {
    val length: Long
    fun open(): InputStream
}

class BytesContent(val bytes: ByteArray) : Content {
    override val length get() = bytes.size.toLong()
    override fun open(): InputStream = bytes.inputStream()
}

class ZeroContent(override val length: Long) : Content {
    override fun open(): InputStream = object : InputStream() {
        private var left = length
        override fun read(): Int = if (left-- > 0) 0 else -1.also { left = 0 }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val n = minOf(len.toLong(), left).toInt()
            java.util.Arrays.fill(b, off, off + n, 0)
            left -= n
            return n
        }
    }
}

/** A stream of [length] bytes, as [opener] gives it (a picked file's, read once from start to end). */
class StreamContent(override val length: Long, private val opener: () -> InputStream) : Content {
    override fun open() = opener()
}

/** [length] bytes of [source] from [start] on. */
class RangeContent(private val source: RandomSource, private val start: Long, override val length: Long) : Content {
    override fun open(): InputStream = object : InputStream() {
        private var at = 0L
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (at >= length) return -1
            val n = minOf(len.toLong(), length - at).toInt()
            source.readFully(start + at, b, off, n)
            at += n
            return n
        }
    }
}

/** [parts] one after another. */
class JoinedContent(private val parts: List<Content>) : Content {
    override val length = parts.sumOf { it.length }
    override fun open(): InputStream = SequenceInputStream(Collections.enumeration(LazyStreams(parts)))

    /** Each part opened only once it's reached (and closed by the SequenceInputStream as it's left). */
    private class LazyStreams(private val parts: List<Content>) : AbstractList<InputStream>() {
        override val size get() = parts.size
        override fun get(index: Int) = parts[index].open()
    }
}

/** [content] at byte [offset] of a drive. */
class Extent(val offset: Long, val content: Content) {
    val end get() = offset + content.length
}

/**
 * What a drive gets: its [extents] (in order, apart), all written and then checked; what's between them stays as it
 * was, but for the rest of the blocks they end in (zeros). [requiredSize]: the drive must have that many bytes at least.
 */
class DiskPlan(extents: List<Extent>, val requiredSize: Long = extents.maxOfOrNull { it.end } ?: 0) {
    val extents = extents.filter { it.content.length > 0 }.sortedBy { it.offset }

    init {
        this.extents.zipWithNext { a, b -> require(a.end <= b.offset) { "Overlapping: ${a.offset}..${a.end} and ${b.offset}" } }
    }

    /** The bytes there are to write. */
    val total = this.extents.sumOf { it.content.length }
}
