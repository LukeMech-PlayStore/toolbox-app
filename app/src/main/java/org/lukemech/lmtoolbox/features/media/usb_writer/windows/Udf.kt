package org.lukemech.lmtoolbox.features.media.usb_writer.windows

import java.io.IOException
import org.lukemech.lmtoolbox.features.media.usb_writer.PiecesSource
import org.lukemech.lmtoolbox.features.media.usb_writer.RandomSource

/** An image in a form this doesn't read (e.g. UDF 2.5's metadata partition, Blu-ray's). */
class UnsupportedImageException(message: String) : IOException(message)

/**
 * The files of a UDF image (ECMA-167 / OSTA UDF 1.02-2.01), as Windows' installation ISOs have them (their ISO 9660
 * part holds a README only): read-only, its tree read whole at [open]. One partition, 2048-byte blocks.
 */
class Udf private constructor(
    /** The volume's name (e.g. "CCCOMA_X64FRE_EN-US_DV9"). */
    val volumeName: String,
    val root: Entry,
) {
    class Entry(
        val name: String,
        val isDirectory: Boolean,
        /** Its bytes (none for a directory). */
        val data: RandomSource?,
        val children: List<Entry> = emptyList(),
    ) {
        val size get() = data?.size ?: 0L

        /** The entry at [path] ("sources/boot.wim") under this one, its names in any case. */
        fun find(path: String): Entry? = path.split('/').filter { it.isNotEmpty() }.fold(this as Entry?) { dir, name ->
            dir?.children?.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }

        /** Everything under it, files and directories, depth first. */
        fun walk(): Sequence<Entry> = sequence {
            for (child in children) {
                yield(child)
                if (child.isDirectory) yieldAll(child.walk())
            }
        }
    }

    companion object {
        private const val BLOCK = 2048
        private const val MAX_DEPTH = 64

        /** [source]'s UDF file system, or null when it has none. */
        fun open(source: RandomSource): Udf? {
            if (source.size < 257L * BLOCK) return null
            val avdp = ByteArray(BLOCK).also { source.readFully(256L * BLOCK, it) }
            if (tag(avdp) != 2) return null
            return Reader(source).read(avdp)
        }

        private fun tag(b: ByteArray, at: Int = 0) = u16(b, at)
        private fun u8(b: ByteArray, at: Int) = b[at].toInt() and 0xFF
        private fun u16(b: ByteArray, at: Int) = u8(b, at) or (u8(b, at + 1) shl 8)
        private fun u32(b: ByteArray, at: Int) = u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16)

        /** An OSTA compressed Unicode string: 8 bits a character, or 16 (big endian), as its first byte says. */
        private fun dchars(b: ByteArray, at: Int, len: Int): String {
            if (len <= 1) return ""
            return when (u8(b, at)) {
                8 -> String(CharArray(len - 1) { u8(b, at + 1 + it).toChar() })
                16 -> String(CharArray((len - 1) / 2) { ((u8(b, at + 1 + 2 * it) shl 8) or u8(b, at + 2 + 2 * it)).toChar() })
                else -> throw UnsupportedImageException("Unknown name encoding ${u8(b, at)}")
            }
        }

    }

    private class Reader(private val source: RandomSource) {
        private var partitionStart = 0L

        fun block(lbn: Long) = ByteArray(BLOCK).also { source.readFully((partitionStart + lbn) * BLOCK, it) }
        fun sector(n: Long) = ByteArray(BLOCK).also { source.readFully(n * BLOCK, it) }

        fun read(avdp: ByteArray): Udf {
            val vdsLength = u32(avdp, 16)
            val vdsLocation = u32(avdp, 20)
            var partition: Long? = null
            var fileSet: Long? = null
            var volume = ""
            for (i in 0 until vdsLength / BLOCK) {
                val d = sector(vdsLocation + i)
                when (tag(d)) {
                    5 -> partition = u32(d, 188)
                    6 -> {
                        if (u32(d, 212) != BLOCK.toLong()) throw UnsupportedImageException("Block size ${u32(d, 212)}")
                        // A fixed 128-byte field: its last byte is how much of it is used
                        volume = dchars(d, 84, u8(d, 84 + 127).coerceAtMost(127))
                        // The partition maps: one plain one (type 1) only
                        val maps = u32(d, 268).toInt()
                        var at = 440
                        repeat(maps) {
                            if (u8(d, at) != 1) throw UnsupportedImageException("UDF partition map of type ${u8(d, at)}")
                            at += u8(d, at + 1)
                        }
                        // The file set descriptor: a long_ad in the logical volume contents use
                        fileSet = u32(d, 248 + 4)
                    }
                    8 -> break
                }
            }
            partitionStart = partition ?: throw UnsupportedImageException("No UDF partition")
            val fsd = block(fileSet ?: throw UnsupportedImageException("No UDF logical volume"))
            if (tag(fsd) != 256) throw UnsupportedImageException("No UDF file set descriptor")
            val root = entry("", u32(fsd, 400 + 4), 0)
            if (!root.isDirectory) throw UnsupportedImageException("UDF root isn't a directory")
            return Udf(volume, root)
        }

        /** The file (or directory, with what's in it) whose (extended) file entry is at block [lbn]. */
        fun entry(name: String, lbn: Long, depth: Int): Entry {
            if (depth > MAX_DEPTH) throw UnsupportedImageException("UDF directories too deep")
            val fe = block(lbn)
            val extended = when (tag(fe)) {
                261 -> false
                266 -> true
                else -> throw UnsupportedImageException("No UDF file entry at $lbn (tag ${tag(fe)})")
            }
            val isDirectory = u8(fe, 27) == 4
            val adType = u16(fe, 34) and 7
            val length = u32(fe, 56) or (u32(fe, 60) shl 32)
            val eaLength = u32(fe, if (extended) 208 else 168).toInt()
            val adLength = u32(fe, if (extended) 212 else 172).toInt()
            val adStart = (if (extended) 216 else 176) + eaLength
            if (adStart + adLength > BLOCK) throw UnsupportedImageException("UDF file entry too long at $lbn")

            val data: RandomSource = if (adType == 3) {
                // Embedded: the data's in the entry itself
                org.lukemech.lmtoolbox.features.media.usb_writer.BytesSource(fe.copyOfRange(adStart, adStart + length.toInt()))
            } else PiecesSource(source, pieces(fe, adStart, adLength, adType, length))

            if (!isDirectory) return Entry(name, false, data)
            val bytes = ByteArray(data.size.toInt()).also { data.readFully(0, it) }
            return Entry(name, true, null, children(bytes, depth))
        }

        /** The allocation descriptors' extents, as pieces of the image, [length] bytes in all. */
        fun pieces(first: ByteArray, start: Int, len: Int, adType: Int, length: Long): List<PiecesSource.Piece> {
            val size = when (adType) {
                0 -> 8
                1 -> 16
                else -> throw UnsupportedImageException("UDF allocation descriptors of type $adType")
            }
            val pieces = mutableListOf<PiecesSource.Piece>()
            var total = 0L
            var b = first
            var at = start
            var end = start + len
            var hops = 0
            while (at + size <= end && total < length) {
                val raw = u32(b, at)
                val type = (raw ushr 30).toInt()
                val extentLength = raw and 0x3FFFFFFF
                val position = u32(b, at + 4)
                at += size
                if (extentLength == 0L) break
                when (type) {
                    // Recorded, or allocated or not but not recorded (zeros)
                    0 -> pieces += PiecesSource.Piece((partitionStart + position) * BLOCK, minOf(extentLength, length - total))
                    1, 2 -> pieces += PiecesSource.Piece(-1, minOf(extentLength, length - total))
                    3 -> {
                        // The descriptors go on in an allocation extent descriptor
                        if (++hops > 1024) throw UnsupportedImageException("UDF allocation extents loop")
                        b = block(position)
                        if (tag(b) != 258) throw UnsupportedImageException("No UDF allocation extent at $position")
                        at = 24
                        end = 24 + u32(b, 20).toInt().coerceAtMost(BLOCK - 24)
                        continue
                    }
                }
                total += pieces.last().length
            }
            if (total < length) pieces += PiecesSource.Piece(-1, length - total)
            return pieces
        }

        /** A directory's file identifier descriptors: what's in it (not its parent, nor what's deleted). */
        fun children(dir: ByteArray, depth: Int): List<Entry> {
            val children = mutableListOf<Entry>()
            var at = 0
            while (at + 38 <= dir.size && tag(dir, at) == 257) {
                val characteristics = u8(dir, at + 18)
                val nameLength = u8(dir, at + 19)
                val icb = u32(dir, at + 20 + 4)
                val iuLength = u16(dir, at + 36)
                val nameAt = at + 38 + iuLength
                if (characteristics and 0x0C == 0 && nameLength > 0) {
                    children += entry(dchars(dir, nameAt, nameLength), icb, depth + 1)
                }
                at += (38 + iuLength + nameLength + 3) and 3.inv()
            }
            return children
        }
    }
}
