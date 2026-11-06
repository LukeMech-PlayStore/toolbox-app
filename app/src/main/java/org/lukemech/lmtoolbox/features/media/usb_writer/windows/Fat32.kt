package org.lukemech.lmtoolbox.features.media.usb_writer.windows

import java.io.IOException
import java.util.Calendar
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.random.Random
import org.lukemech.lmtoolbox.features.media.usb_writer.BytesContent
import org.lukemech.lmtoolbox.features.media.usb_writer.Content
import org.lukemech.lmtoolbox.features.media.usb_writer.DiskPlan
import org.lukemech.lmtoolbox.features.media.usb_writer.Extent
import org.lukemech.lmtoolbox.features.media.usb_writer.JoinedContent
import org.lukemech.lmtoolbox.features.media.usb_writer.NotEnoughSpaceException
import org.lukemech.lmtoolbox.features.media.usb_writer.ZeroContent

/** A file too big for FAT32 (4 GiB less a byte at most). */
class FileTooLargeException(val path: String, val size: Long) : IOException("$path is too big for FAT32 ($size bytes)")

/** Each directory's children with their names (see Fat32.named). */
private typealias Names = HashMap<FatDir, List<Pair<Any, Fat32.Name>>>

/** A file of a [FatDir]: [content] its bytes. */
class FatFile(val name: String, val content: Content)

class FatDir(val name: String, val dirs: List<FatDir> = emptyList(), val files: List<FatFile> = emptyList())

/**
 * A drive made anew: an MBR with one FAT32 partition (from 1 MiB, type 0x0C, active) holding [root]'s files, as
 * Windows formats it (cluster size by its size), for UEFI to boot from. The files lie one after another, each in
 * clusters in a row, so the drive's written from start to end; what's free is left as it was (the FATs say so).
 * The drive's old partition tables are wiped: the MBR's 1 MiB, and the end of the drive (a GPT's backup).
 */
object Fat32 {
    const val MAX_FILE = 0xFFFFFFFFL
    private const val MIB = 1L shl 20
    private const val ATTR_DIRECTORY = 0x10
    private const val ATTR_ARCHIVE = 0x20
    private const val ATTR_VOLUME = 0x08
    private const val ATTR_LFN = 0x0F
    private const val END_OF_CHAIN = 0x0FFFFFFF

    /** What the plan laid out, for the tests and the page. */
    class Layout(
        val sectorSize: Int,
        val partitionStart: Long,
        val partitionSectors: Long,
        val reservedSectors: Int,
        val fatSectors: Long,
        val sectorsPerCluster: Int,
        val clusterCount: Long,
        val usedClusters: Long,
    ) {
        val clusterSize get() = sectorsPerCluster * sectorSize
        val dataStart get() = partitionStart + reservedSectors + 2 * fatSectors

        /** The drive's byte where [cluster] starts. */
        fun offsetOf(cluster: Long) = (dataStart + (cluster - 2) * sectorsPerCluster) * sectorSize
    }

    class Result(val plan: DiskPlan, val layout: Layout)

    /** [root]'s files on a drive of [deviceSize] bytes in sectors of [sectorSize], its volume named [label]. */
    fun plan(
        deviceSize: Long, sectorSize: Int, label: String, root: FatDir,
        now: Calendar = Calendar.getInstance(), random: Random = Random.Default,
    ): Result {
        require(sectorSize in setOf(512, 1024, 2048, 4096)) { "Sector size $sectorSize" }
        val sectors = deviceSize / sectorSize
        val mib = MIB / sectorSize
        val partitionStart = mib
        // It ends a MiB before the drive does at least (where a GPT's backup was, wiped), on a MiB, in the MBR's 32 bits
        val partitionEnd = minOf((sectors - mib) / mib * mib, partitionStart + 0xFFFFFFFFL / mib * mib)
        val partitionSectors = partitionEnd - partitionStart
        if (partitionSectors <= 0) throw NotEnoughSpaceException(4 * MIB, deviceSize)

        // Windows' cluster sizes for FAT32
        val bytes = partitionSectors * sectorSize
        val clusterBytes = when {
            bytes <= 8L shl 30 -> 4096
            bytes <= 16L shl 30 -> 8192
            bytes <= 32L shl 30 -> 16384
            else -> 32768
        }
        val spc = maxOf(1, clusterBytes / sectorSize)
        // A FAT entry for every cluster there could be (more than there are, as the FATs take some room)
        val fatSectors = ceilDiv((ceilDiv(partitionSectors - 32, spc.toLong()) + 2) * 4, sectorSize.toLong())
        // The data from a MiB on: the reserved sectors make up for it
        val dataStart = ceilDiv(partitionStart + 32 + 2 * fatSectors, mib) * mib
        val reserved = (dataStart - partitionStart - 2 * fatSectors).toInt()
        val clusterCount = (partitionEnd - dataStart) / spc
        if (clusterCount < 65525) throw NotEnoughSpaceException(64 * MIB, deviceSize)

        // Clusters: the directories first (the root's from 2 on), then the files, in the tree's order
        val clusterSize = spc.toLong() * sectorSize
        var next = 2L
        val dirClusters = LinkedHashMap<FatDir, Long>()
        val dirBytes = HashMap<FatDir, Int>()
        val names = Names()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            val size = directorySize(dir, dir === root, names)
            dirClusters[dir] = next
            dirBytes[dir] = size
            next += ceilDiv(size.toLong(), clusterSize)
            queue.addAll(dir.dirs)
        }
        val fileClusters = LinkedHashMap<FatFile, Long>()
        fun allocate(dir: FatDir, path: String) {
            for (file in dir.files) {
                if (file.content.length > MAX_FILE) throw FileTooLargeException("$path/${file.name}", file.content.length)
                if (file.content.length == 0L) continue
                fileClusters[file] = next
                next += ceilDiv(file.content.length, clusterSize)
            }
            dir.dirs.forEach { allocate(it, "$path/${it.name}") }
        }
        allocate(root, "")
        val used = next - 2
        if (used > clusterCount) throw NotEnoughSpaceException(dataStart * sectorSize + used * clusterSize, deviceSize)

        val layout = Layout(sectorSize, partitionStart, partitionSectors, reserved, fatSectors, spc, clusterCount, used)

        // The FAT: the media byte, then the chains, each to the next cluster in a row
        val fat = java.nio.ByteBuffer.allocate((next * 4).toInt()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        fat.putInt(0x0FFFFFF8).putInt(END_OF_CHAIN)
        val chains = (dirClusters.map { (d, c) -> c to ceilDiv(dirBytes[d]!!.toLong(), clusterSize) } +
            fileClusters.map { (f, c) -> c to ceilDiv(f.content.length, clusterSize) }).sortedBy { it.first }
        for ((startCluster, count) in chains) {
            for (i in 0 until count) fat.putInt(((startCluster + i) * 4).toInt(), if (i == count - 1) END_OF_CHAIN else (startCluster + i + 1).toInt())
        }
        val fatContent = JoinedContent(listOf(BytesContent(fat.array()), ZeroContent(fatSectors * sectorSize - fat.capacity())))

        val volumeLabel = shortLabel(label)
        val serial = random.nextInt()
        val extents = mutableListOf<Extent>()
        extents += Extent(0, JoinedContent(listOf(BytesContent(mbr(sectorSize, partitionStart, partitionSectors, random)), ZeroContent(partitionStart * sectorSize - sectorSize))))
        extents += Extent(partitionStart * sectorSize, BytesContent(reservedArea(layout, volumeLabel, serial, clusterCount - used, next)))
        extents += Extent((partitionStart + reserved) * sectorSize, fatContent)
        extents += Extent((partitionStart + reserved + fatSectors) * sectorSize, fatContent)
        val stamp = DosTime(now)
        val parents = IdentityHashMap<FatDir, Long>()
        for ((dir, cluster) in dirClusters) dir.dirs.forEach { parents[it] = cluster }
        for ((dir, cluster) in dirClusters) {
            // A directory in the root has 0 for its parent
            val parent = parents[dir]?.takeIf { it != 2L } ?: 0
            val entries = directory(dir, dir === root, volumeLabel, cluster, parent, dirClusters, fileClusters, stamp, names)
            check(entries.size == dirBytes[dir])
            val padded = entries.copyOf(ceilDiv(entries.size.toLong(), clusterSize).toInt() * clusterSize.toInt())
            extents += Extent(layout.offsetOf(cluster), BytesContent(padded))
        }
        for ((file, cluster) in fileClusters) extents += Extent(layout.offsetOf(cluster), file.content)
        // What's left after the partition: a GPT's backup, wiped
        extents += Extent(partitionEnd * sectorSize, ZeroContent(sectors * sectorSize - partitionEnd * sectorSize))
        return Result(DiskPlan(extents, requiredSize = sectors * sectorSize), layout)
    }

    private fun ceilDiv(a: Long, b: Long) = (a + b - 1) / b

    private fun mbr(sectorSize: Int, start: Long, count: Long, random: Random): ByteArray {
        val b = ByteArray(sectorSize)
        putInt(b, 440, random.nextInt())
        val p = 446
        b[p] = 0x80.toByte()
        // CHS: past what it can say, LBA tells
        b[p + 1] = 0xFE.toByte(); b[p + 2] = 0xFF.toByte(); b[p + 3] = 0xFF.toByte()
        b[p + 4] = 0x0C
        b[p + 5] = 0xFE.toByte(); b[p + 6] = 0xFF.toByte(); b[p + 7] = 0xFF.toByte()
        putInt(b, p + 8, start.toInt())
        putInt(b, p + 12, count.toInt())
        b[510] = 0x55; b[511] = 0xAA.toByte()
        return b
    }

    /** The boot sector, FS information sector, and their backups (sectors 6 and 7). */
    private fun reservedArea(l: Layout, label: ByteArray, serial: Int, freeClusters: Long, nextFree: Long): ByteArray {
        val ss = l.sectorSize
        val area = ByteArray(l.reservedSectors * ss)
        val boot = ByteArray(ss)
        boot[0] = 0xEB.toByte(); boot[1] = 0x58; boot[2] = 0x90.toByte()
        "MSWIN4.1".toByteArray(Charsets.US_ASCII).copyInto(boot, 3)
        putShort(boot, 11, ss)
        boot[13] = l.sectorsPerCluster.toByte()
        putShort(boot, 14, l.reservedSectors)
        boot[16] = 2
        boot[21] = 0xF8.toByte()
        putShort(boot, 24, 63)
        putShort(boot, 26, 255)
        putInt(boot, 28, l.partitionStart.toInt())
        putInt(boot, 32, l.partitionSectors.toInt())
        putInt(boot, 36, l.fatSectors.toInt())
        putInt(boot, 44, 2)
        putShort(boot, 48, 1)
        putShort(boot, 50, 6)
        boot[64] = 0x80.toByte()
        boot[66] = 0x29
        putInt(boot, 67, serial)
        label.copyInto(boot, 71)
        "FAT32   ".toByteArray(Charsets.US_ASCII).copyInto(boot, 82)
        // Not bootable by a BIOS (UEFI reads the files): INT 18h, the next boot device, else halt
        byteArrayOf(0xCD.toByte(), 0x18, 0xF4.toByte(), 0xEB.toByte(), 0xFD.toByte()).copyInto(boot, 90)
        boot[510] = 0x55; boot[511] = 0xAA.toByte()

        val info = ByteArray(ss)
        putInt(info, 0, 0x41615252)
        putInt(info, 484, 0x61417272)
        putInt(info, 488, freeClusters.toInt())
        putInt(info, 492, nextFree.toInt())
        putInt(info, 508, 0xAA550000.toInt())

        boot.copyInto(area, 0)
        info.copyInto(area, ss)
        boot.copyInto(area, 6 * ss)
        info.copyInto(area, 7 * ss)
        return area
    }

    /** The volume's label: 11 characters, upper case, in what FAT allows. */
    private fun shortLabel(label: String): ByteArray {
        val clean = label.uppercase(Locale.ROOT).map { if (it.code in 0x21..0x7E && it !in "\"*+,./:;<=>?[\\]|") it else '_' }
            .joinToString("").trim('_').take(11).ifEmpty { "WINDOWS" }
        return clean.padEnd(11, ' ').toByteArray(Charsets.US_ASCII)
    }

    /** A directory's entries' names: each one's short name, and its long one's entries before it, if it needs them. */
    class Name(val short: ByteArray, val long: String?) {
        val entries get() = 1 + (long?.let { (it.length + 12) / 13 } ?: 0)
    }

    /** [dir]'s children (directories, then files), each with its names, unique in it (kept in [names]). */
    private fun named(dir: FatDir, names: Names): List<Pair<Any, Name>> = names.getOrPut(dir) {
        val children: List<Pair<Any, String>> = dir.dirs.map { it to it.name } + dir.files.map { it to it.name }
        val taken = HashSet<String>()
        val result = arrayOfNulls<Pair<Any, Name>>(children.size)
        // Names that are short ones already first, the made-up ones (NAME~1.EXT) then
        children.forEachIndexed { i, (child, name) ->
            require(name.isNotEmpty() && name.length <= 255) { "Bad file name '$name'" }
            exactShort(name)?.let { short ->
                if (taken.add(String(short, Charsets.ISO_8859_1))) {
                    result[i] = child to Name(short, if (name == name.uppercase(Locale.ROOT)) null else name)
                }
            }
        }
        children.forEachIndexed { i, (child, name) ->
            if (result[i] != null) return@forEachIndexed
            val dot = name.lastIndexOf('.').takeIf { it > 0 }
            val base = clean(if (dot != null) name.substring(0, dot) else name).ifEmpty { "_" }
            val ext = clean(if (dot != null) name.substring(dot + 1) else "").take(3)
            var n = 1
            while (true) {
                val tail = "~$n"
                val short = (base.take(8 - tail.length) + tail).padEnd(8) + ext.padEnd(3)
                if (taken.add(short)) {
                    result[i] = child to Name(short.toByteArray(Charsets.ISO_8859_1), name)
                    break
                }
                n++
            }
        }
        result.map { it!! }
    }

    private const val SHORT_CHARS = "!#$%&'()-@^_`{}~"
    private fun shortChar(c: Char) = c in 'A'..'Z' || c in '0'..'9' || c in SHORT_CHARS

    /** [name] as an 8.3 name as it is (but its case), or null. */
    private fun exactShort(name: String): ByteArray? {
        val upper = name.uppercase(Locale.ROOT)
        val dot = upper.lastIndexOf('.')
        val base = if (dot >= 0) upper.substring(0, dot) else upper
        val ext = if (dot >= 0) upper.substring(dot + 1) else ""
        if (base.isEmpty() || base.length > 8 || ext.length > 3 || (dot >= 0 && ext.isEmpty())) return null
        if (!(base + ext).all(::shortChar)) return null
        return (base.padEnd(8) + ext.padEnd(3)).toByteArray(Charsets.ISO_8859_1)
    }

    private fun clean(part: String) = part.uppercase(Locale.ROOT).filter { it != ' ' && it != '.' }
        .map { if (shortChar(it)) it else '_' }.joinToString("")

    /** How many bytes [dir]'s entries take: its label (the root) or its "." and "..", its children's. */
    private fun directorySize(dir: FatDir, isRoot: Boolean, names: Names) =
        32 * ((if (isRoot) 1 else 2) + named(dir, names).sumOf { it.second.entries })

    private fun directory(
        dir: FatDir, isRoot: Boolean, label: ByteArray, self: Long, parent: Long,
        dirClusters: Map<FatDir, Long>, fileClusters: Map<FatFile, Long>, t: DosTime, names: Names,
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        if (isRoot) out.write(entry(label, ATTR_VOLUME, 0, 0, t))
        else {
            out.write(entry(".          ".toByteArray(Charsets.US_ASCII), ATTR_DIRECTORY, self, 0, t))
            out.write(entry("..         ".toByteArray(Charsets.US_ASCII), ATTR_DIRECTORY, parent, 0, t))
        }
        for ((child, name) in named(dir, names)) {
            name.long?.let { out.write(longEntries(it, name.short)) }
            when (child) {
                is FatDir -> out.write(entry(name.short, ATTR_DIRECTORY, dirClusters.getValue(child), 0, t))
                is FatFile -> out.write(entry(name.short, ATTR_ARCHIVE, fileClusters[child] ?: 0, child.content.length, t))
            }
        }
        return out.toByteArray()
    }

    private fun entry(name: ByteArray, attr: Int, cluster: Long, size: Long, t: DosTime): ByteArray {
        val e = ByteArray(32)
        name.copyInto(e, 0, 0, 11)
        e[11] = attr.toByte()
        putShort(e, 14, t.time); putShort(e, 16, t.date); putShort(e, 18, t.date)
        putShort(e, 20, (cluster ushr 16).toInt())
        putShort(e, 22, t.time); putShort(e, 24, t.date)
        putShort(e, 26, (cluster and 0xFFFF).toInt())
        putInt(e, 28, size.toInt())
        return e
    }

    /** [name]'s long-name entries, last part first, as they come before its short entry. */
    private fun longEntries(name: String, short: ByteArray): ByteArray {
        var sum = 0
        for (b in short) sum = (((sum and 1) shl 7) + (sum ushr 1) + (b.toInt() and 0xFF)) and 0xFF
        val count = (name.length + 12) / 13
        val out = ByteArray(32 * count)
        for (i in 0 until count) {
            val e = ByteArray(32)
            e[0] = ((i + 1) or (if (i == count - 1) 0x40 else 0)).toByte()
            e[11] = ATTR_LFN.toByte()
            e[13] = sum.toByte()
            val offsets = intArrayOf(1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30)
            for (k in 0 until 13) {
                val at = i * 13 + k
                val c = when {
                    at < name.length -> name[at].code
                    at == name.length -> 0
                    else -> 0xFFFF
                }
                putShort(e, offsets[k], c)
            }
            e.copyInto(out, 32 * (count - 1 - i))
        }
        return out
    }

    private class DosTime(c: Calendar) {
        val date = ((c.get(Calendar.YEAR) - 1980).coerceIn(0, 127) shl 9) or ((c.get(Calendar.MONTH) + 1) shl 5) or c.get(Calendar.DAY_OF_MONTH)
        val time = (c.get(Calendar.HOUR_OF_DAY) shl 11) or (c.get(Calendar.MINUTE) shl 5) or (c.get(Calendar.SECOND) / 2)
    }

    private fun putShort(b: ByteArray, at: Int, v: Int) {
        b[at] = v.toByte(); b[at + 1] = (v ushr 8).toByte()
    }

    private fun putInt(b: ByteArray, at: Int, v: Int) {
        putShort(b, at, v); putShort(b, at + 2, v ushr 16)
    }
}
