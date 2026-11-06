package org.lukemech.lmtoolbox.features.media.usb_writer.windows

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.lukemech.lmtoolbox.features.media.usb_writer.BytesContent
import org.lukemech.lmtoolbox.features.media.usb_writer.Content
import org.lukemech.lmtoolbox.features.media.usb_writer.JoinedContent
import org.lukemech.lmtoolbox.features.media.usb_writer.RandomSource

/**
 * A WIM (install.wim) split into parts (install.swm, install2.swm...), as `dism /Split-Image` and wimlib split
 * them, each small enough for FAT32: Windows Setup installs from them. Its resources are copied as they are
 * (compressed), in their order; each part gets its own header and its own lookup table (the resources in it),
 * the XML data, and the first one the images' metadata. Nothing's written here: each part is [Content] to write,
 * read from the WIM as it's written.
 */
object WimSplit {
    private const val HEADER_SIZE = 208
    private const val ENTRY_SIZE = 50
    private const val FLAG_METADATA = 0x02
    private const val FLAG_COMPRESSED = 0x04
    private const val FLAG_SOLID = 0x10
    private const val HDR_SPANNED = 0x08
    private const val HDR_WRITE_IN_PROGRESS = 0x40

    /** A part's file name (install.swm, install2.swm...) and its bytes. */
    class Part(val name: String, val content: Content)

    /** A resource header: [size] bytes stored at [offset], its [flags]; [originalSize] uncompressed. */
    private class ResHdr(val size: Long, val flags: Int, val offset: Long, val originalSize: Long) {
        fun put(b: ByteBuffer, at: Int, offset: Long = this.offset) {
            b.putLong(at, size or (flags.toLong() shl 56))
            b.putLong(at + 8, offset)
            b.putLong(at + 16, originalSize)
        }

        companion object {
            fun read(b: ByteBuffer, at: Int): ResHdr {
                val sizeAndFlags = b.getLong(at)
                return ResHdr(sizeAndFlags and 0x00FFFFFFFFFFFFFFL, (sizeAndFlags ushr 56).toInt(), b.getLong(at + 8), b.getLong(at + 16))
            }
        }
    }

    /** A lookup table entry: [raw] its 50 bytes (hash, reference count...), [res] its resource. */
    private class Entry(val raw: ByteArray, val res: ResHdr)

    /**
     * [wim] in parts of [maxPart] bytes at most (3800 MiB, as WoeUSB and Rufus have it: under FAT32's 4 GiB), named
     * after [base] (base.swm, base2.swm...). Throws [UnsupportedImageException] for a WIM it can't split as it is (solid, an ESD, split already).
     */
    fun split(wim: RandomSource, base: String = "install", maxPart: Long = 3800L shl 20): List<Part> {
        val header = ByteArray(HEADER_SIZE).also { wim.readFully(0, it) }
        val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        if (String(header, 0, 8, Charsets.US_ASCII) != "MSWIM\u0000\u0000\u0000") throw UnsupportedImageException("Not a WIM file")
        if (h.getInt(8) != HEADER_SIZE) throw UnsupportedImageException("WIM header of ${h.getInt(8)} bytes")
        val version = h.getInt(12)
        if (version != 0x10d00 && version != 0x10b00) throw UnsupportedImageException("WIM version ${version.toString(16)} (solid?)")
        if (h.getShort(40).toInt() != 1 || h.getShort(42).toInt() != 1) throw UnsupportedImageException("The WIM is split already")
        val tableHdr = ResHdr.read(h, 48)
        val xmlHdr = ResHdr.read(h, 72)
        val bootIndex = h.getInt(120)
        if (tableHdr.flags and FLAG_COMPRESSED != 0) throw UnsupportedImageException("Compressed WIM lookup table")
        if (tableHdr.size % ENTRY_SIZE != 0L || tableHdr.size > Int.MAX_VALUE) throw UnsupportedImageException("Bad WIM lookup table")

        val table = ByteArray(tableHdr.size.toInt()).also { wim.readFully(tableHdr.offset, it) }
        val tb = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN)
        val entries = (0 until table.size / ENTRY_SIZE).map { i ->
            Entry(table.copyOfRange(i * ENTRY_SIZE, (i + 1) * ENTRY_SIZE), ResHdr.read(tb, i * ENTRY_SIZE))
        }.filter { it.res.flags and 0x01 == 0 } // free ones aren't anything's
        if (entries.any { it.res.flags and FLAG_SOLID != 0 }) throw UnsupportedImageException("Solid WIM resources")
        val metadata = entries.filter { it.res.flags and FLAG_METADATA != 0 }
        val blobs = entries.filter { it.res.flags and FLAG_METADATA == 0 }.sortedBy { it.res.offset }

        // Into parts: the metadata in the first, the rest in the WIM's order, each part as full as it gets
        val overhead = HEADER_SIZE + xmlHdr.size
        val parts = mutableListOf(metadata.toMutableList())
        var size = overhead + metadata.sumOf { it.res.size + ENTRY_SIZE }
        for (blob in blobs) {
            val adds = blob.res.size + ENTRY_SIZE
            if (overhead + adds > maxPart) throw UnsupportedImageException("A WIM resource too big to split (${blob.res.size} bytes)")
            if (size + adds > maxPart && parts.last().isNotEmpty()) {
                parts += mutableListOf<Entry>()
                size = overhead
            }
            parts.last() += blob
            size += adds
        }

        return parts.mapIndexed { i, inPart ->
            val number = i + 1
            val pieces = mutableListOf<Content>()
            val partTable = ByteBuffer.allocate(inPart.size * ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            var offset = HEADER_SIZE.toLong()
            val moved = HashMap<Entry, Long>()
            inPart.forEachIndexed { k, entry ->
                pieces += wim.range(entry.res.offset, entry.res.size)
                moved[entry] = offset
                entry.raw.copyInto(partTable.array(), k * ENTRY_SIZE)
                entry.res.put(partTable, k * ENTRY_SIZE, offset)
                partTable.putShort(k * ENTRY_SIZE + 24, number.toShort())
                offset += entry.res.size
            }
            val tableAt = offset
            val xmlAt = tableAt + partTable.capacity()

            val ph = ByteBuffer.wrap(header.copyOf()).order(ByteOrder.LITTLE_ENDIAN)
            ph.putInt(16, (h.getInt(16) or HDR_SPANNED) and HDR_WRITE_IN_PROGRESS.inv())
            ph.putShort(40, number.toShort())
            ph.putShort(42, parts.size.toShort())
            ResHdr(partTable.capacity().toLong(), 0, tableAt, partTable.capacity().toLong()).put(ph, 48)
            xmlHdr.put(ph, 72, xmlAt)
            // The boot image's metadata: the first part's
            val boot = metadata.getOrNull(bootIndex - 1)?.takeIf { number == 1 }
            if (boot != null) boot.res.put(ph, 96, moved.getValue(boot)) else ResHdr(0, 0, 0, 0).put(ph, 96)
            if (number != 1) ph.putInt(120, 0)
            // No integrity table
            ResHdr(0, 0, 0, 0).put(ph, 124)

            val content = JoinedContent(
                listOf(BytesContent(ph.array())) + pieces + BytesContent(partTable.array()) + wim.range(xmlHdr.offset, xmlHdr.size)
            )
            Part(if (number == 1) "$base.swm" else "$base$number.swm", content)
        }
    }
}
