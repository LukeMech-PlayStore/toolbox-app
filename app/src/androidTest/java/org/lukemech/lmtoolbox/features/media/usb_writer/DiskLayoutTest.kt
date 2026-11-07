package org.lukemech.lmtoolbox.features.media.usb_writer

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.tukaani.xz.XZInputStream
import org.lukemech.lmtoolbox.features.media.usb_writer.windows.Fat32
import org.lukemech.lmtoolbox.features.media.usb_writer.windows.FatDir
import org.lukemech.lmtoolbox.features.media.usb_writer.windows.FatFile
import org.lukemech.lmtoolbox.features.media.usb_writer.windows.FileTooLargeException
import org.lukemech.lmtoolbox.features.media.usb_writer.windows.Udf
import org.lukemech.lmtoolbox.features.media.usb_writer.windows.WimSplit
import org.lukemech.lmtoolbox.features.media.usb_writer.windows.WindowsMedia

/**
 * The bootable USB maker's own logic, on the device, with a drive that's a file ([FileBlockDevice]): the emulator
 * has no USB drive. A Windows-like ISO (androidTest assets/usb_writer/win-mini.iso.xz, in the test APK only: UDF made by Windows' IMAPI2, x64 and
 * ARM64 bootloaders, a WIM file made by 7-Zip) made into FAT32 and read back by a FAT reader of the test's own.
 */
class DiskLayoutTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()

    private fun temp(name: String) = File(context.cacheDir, "usb_writer_test_$name").also { it.delete(); files += it }

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun iso(): File {
        val file = temp("win-mini.iso")
        InstrumentationRegistry.getInstrumentation().context.assets.open("usb_writer/win-mini.iso.xz").use { input ->
            XZInputStream(input).use { xz -> file.outputStream().use { xz.copyTo(it) } }
        }
        return file
    }

    private fun source(file: File): RandomSource = ChannelSource(RandomAccessFile(file, "r").channel)

    @Test
    fun writerPadsBlocksLeavesGapsAndVerifies() = runBlocking {
        val random = Random(1)
        val big = random.nextBytes(1_500_000)
        val small = random.nextBytes(3000)
        val plan = DiskPlan(
            listOf(
                Extent(100, BytesContent("hello".toByteArray())),
                Extent(5000, BytesContent(small)),
                Extent(2_097_152 + 7, BytesContent(big)),
            )
        )
        FileBlockDevice(temp("pad.img"), 512, 8192).use { dev ->
            // What was on it before: 0xEE everywhere
            val ee = ByteArray(512) { 0xEE.toByte() }
            for (lba in 0 until dev.blockCount) dev.write(lba, ee, 512)

            DiskWriter.write(plan, dev) { _, _ -> }
            val all = ByteArray(dev.size.toInt())
            dev.read(0, all, all.size)
            assertArrayEquals("hello".toByteArray(), all.copyOfRange(100, 105))
            assertArrayEquals(small, all.copyOfRange(5000, 8000))
            assertArrayEquals(big, all.copyOfRange(2_097_159, 2_097_159 + big.size))
            // The rest of a block written: zeros; blocks not written: as they were
            assertTrue(all.copyOfRange(0, 100).all { it == 0.toByte() })
            assertTrue(all.copyOfRange(105, 512).all { it == 0.toByte() })
            assertTrue(all.copyOfRange(10240, 2_097_152).all { it == 0xEE.toByte() })

            DiskWriter.verify(plan, dev) { _, _ -> }
            // A byte changed: verification tells where
            val block = ByteArray(512)
            dev.read(5000 / 512 + 1, block, 512)
            block[3] = (block[3] + 1).toByte()
            dev.write(5000 / 512 + 1, block, 512)
            try {
                DiskWriter.verify(plan, dev) { _, _ -> }
                fail("Verification didn't fail")
            } catch (e: VerificationException) {
                assertEquals((5000 / 512 + 1) * 512L + 3, e.offset)
            }
        }
    }

    @Test
    fun tooSmallDriveRefused() = runBlocking {
        FileBlockDevice(temp("small.img"), 512, 16).use { dev ->
            try {
                DiskWriter.write(DiskPlan(listOf(Extent(0, ZeroContent(9000)))), dev) { _, _ -> }
                fail("Written onto a drive too small")
            } catch (_: NotEnoughSpaceException) {
            }
        }
    }

    @Test
    fun windowsIsoIsReadForX64AndArm64() {
        val inspection = WindowsMedia.inspect(source(iso()))!!
        assertEquals("CCCOMA_A64FRE_PL-PL_DV9", inspection.label)
        assertEquals(listOf(WindowsMedia.Architecture.X64, WindowsMedia.Architecture.ARM64), inspection.architectures)
        assertEquals(false, inspection.splitsWim)
        val names = inspection.udf.root.walk().map { it.name }.toSet()
        assertTrue(names.containsAll(setOf("efi", "boot", "bootx64.efi", "bootaa64.efi", "sources", "install.wim", "Zażółć gęślą.txt")))
    }

    @Test
    fun windowsIsoBecomesFat32WithTheSameFiles() = runBlocking {
        val inspection = WindowsMedia.inspect(source(iso()))!!
        FileBlockDevice(temp("win.img"), 512, 300L * 2048).use { dev ->
            val result = WindowsMedia.plan(inspection, dev.size, dev.blockSize)
            DiskWriter.write(result.plan, dev) { _, _ -> }
            DiskWriter.verify(result.plan, dev) { _, _ -> }

            val fat = FatReader(dev)
            assertEquals("CCCOMA_A64F", fat.label)
            val expected = inspection.udf.root.walk().filter { !it.isDirectory }.associate { e ->
                path(inspection.udf.root, e) to ByteArray(e.size.toInt()).also { e.data!!.readFully(0, it) }
            }
            val actual = fat.files()
            assertEquals(expected.keys, actual.keys)
            expected.forEach { (path, bytes) -> assertArrayEquals(path, bytes, actual[path]) }
        }
    }

    @Test
    fun shortNamesAreUniqueAndLongNamesKept() = runBlocking {
        val names = listOf("LongFileName1.txt", "LongFileName2.txt", "longfi~1.txt", "UPPER.TXT", "MiXeD.TxT", "a.b.c.d", ".hidden", "x".repeat(200) + ".dat")
        val root = FatDir("", listOf(FatDir("Zażółć")), names.map { FatFile(it, BytesContent(it.toByteArray())) })
        FileBlockDevice(temp("names.img"), 512, 300L * 2048).use { dev ->
            val result = Fat32.plan(dev.size, dev.blockSize, "names", root)
            DiskWriter.write(result.plan, dev) { _, _ -> }
            val fat = FatReader(dev)
            val entries = fat.list(fat.rootCluster)
            assertEquals((listOf("Zażółć") + names).toSet(), entries.map { it.name }.toSet())
            assertEquals(entries.size, entries.map { it.short }.toSet().size)
            names.forEach { assertArrayEquals(it.toByteArray(), fat.files()["/$it"]) }
        }
    }

    @Test
    fun fileOver4GiBRefused() {
        val root = FatDir("", files = listOf(FatFile("huge.bin", ZeroContent(Fat32.MAX_FILE + 1))))
        try {
            Fat32.plan(16L shl 30, 512, "huge", root)
            fail("A file over 4 GiB laid out on FAT32")
        } catch (_: FileTooLargeException) {
        }
    }

    @Test
    fun wimSplitsIntoPartsWithEveryResource() {
        val udf = Udf.open(source(iso()))!!
        val wim = udf.root.find("sources/install.wim")!!.data!!
        val original = Wim(ByteArray(wim.size.toInt()).also { wim.readFully(0, it) })
        val parts = WimSplit.split(wim, "install", maxPart = 150_000).map { part ->
            part.name to Wim(part.content.open().use { it.readBytes() })
        }
        assertTrue("Only ${parts.size} part(s)", parts.size >= 2)
        assertEquals(listOf("install.swm") + (2..parts.size).map { "install$it.swm" }, parts.map { it.first })
        val byHash = HashMap<String, ByteArray>()
        parts.forEachIndexed { i, (_, part) ->
            assertEquals(i + 1, part.partNumber)
            assertEquals(parts.size, part.totalParts)
            assertArrayEquals(original.guid, part.guid)
            assertArrayEquals(original.xml, part.xml)
            assertTrue(part.bytes.size <= 150_000)
            part.entries.forEach { e ->
                assertEquals(i + 1, e.part)
                if (e.metadata) assertEquals("Metadata outside the first part", 0, i)
                byHash[e.hash] = part.resource(e)
            }
        }
        // Every resource, each once, as it was
        assertEquals(original.entries.map { it.hash }.toSet(), byHash.keys)
        assertEquals(original.entries.size, parts.sumOf { it.second.entries.size })
        original.entries.forEach { assertArrayEquals(original.resource(it), byHash[it.hash]) }
    }

    private fun path(root: Udf.Entry, target: Udf.Entry): String {
        fun find(dir: Udf.Entry, prefix: String): String? {
            for (c in dir.children) {
                val p = "$prefix/${c.name}"
                if (c === target) return p
                if (c.isDirectory) find(c, p)?.let { return it }
            }
            return null
        }
        return find(root, "")!!
    }

    /** A WIM's (or part's) header and lookup table, read for the test. */
    private class Wim(val bytes: ByteArray) {
        class Entry(val size: Long, val flags: Int, val offset: Long, val part: Int, val hash: String) {
            val metadata get() = flags and 0x02 != 0
        }

        private val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val guid = bytes.copyOfRange(24, 40)
        val partNumber = b.getShort(40).toInt()
        val totalParts = b.getShort(42).toInt()
        val entries = run {
            val size = (b.getLong(48) and 0x00FFFFFFFFFFFFFFL).toInt()
            val at = b.getLong(56).toInt()
            (0 until size / 50).map { i ->
                val e = at + i * 50
                val sf = b.getLong(e)
                Entry(sf and 0x00FFFFFFFFFFFFFFL, (sf ushr 56).toInt(), b.getLong(e + 8), b.getShort(e + 24).toInt(),
                    bytes.copyOfRange(e + 30, e + 50).joinToString("") { "%02x".format(it) })
            }.filter { it.flags and 0x01 == 0 }
        }
        val xml = bytes.copyOfRange(b.getLong(80).toInt(), (b.getLong(80) + (b.getLong(72) and 0x00FFFFFFFFFFFFFFL)).toInt())
        fun resource(e: Entry) = bytes.copyOfRange(e.offset.toInt(), (e.offset + e.size).toInt())
    }

    /** A FAT32 volume read back: the MBR's first partition, its directories with long names, its files. */
    private class FatReader(private val dev: BlockDevice) {
        class Entry(val name: String, val short: String, val isDir: Boolean, val cluster: Long, val size: Long)

        private fun read(offset: Long, len: Int): ByteArray {
            val bs = dev.blockSize
            val first = offset / bs
            val last = (offset + len + bs - 1) / bs
            val buf = ByteArray(((last - first) * bs).toInt())
            dev.read(first, buf, buf.size)
            return buf.copyOfRange((offset - first * bs).toInt(), (offset - first * bs).toInt() + len)
        }

        private val mbr = ByteBuffer.wrap(read(0, 512)).order(ByteOrder.LITTLE_ENDIAN)
        private val start = (mbr.getInt(454).toLong() and 0xFFFFFFFFL) * 512
        private val bpb = ByteBuffer.wrap(read(start, 512)).order(ByteOrder.LITTLE_ENDIAN)
        private val bps = bpb.getShort(11).toInt()
        private val spc = bpb.get(13).toInt() and 0xFF
        private val reserved = bpb.getShort(14).toInt()
        private val fatSize = bpb.getInt(36).toLong()
        val rootCluster = bpb.getInt(44).toLong()
        val label = String(read(start, 512), 71, 11, Charsets.US_ASCII).trim()
        private val clusterBytes = bps * spc
        private val fatStart = start + reserved.toLong() * bps
        private val dataStart = fatStart + 2 * fatSize * bps

        init {
            assertEquals(0x55, mbr.get(510).toInt() and 0xFF)
            assertEquals(0x0C, mbr.get(450).toInt())
            assertEquals("FAT32   ", String(read(start, 512), 82, 8, Charsets.US_ASCII))
        }

        private fun next(cluster: Long) = ByteBuffer.wrap(read(fatStart + cluster * 4, 4)).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0x0FFFFFFF

        private fun chainBytes(first: Long, size: Long? = null): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            var c = first
            while (c in 2 until 0x0FFFFFF8) {
                out.write(read(dataStart + (c - 2) * clusterBytes, clusterBytes))
                if (size != null && out.size() >= size) break
                c = next(c)
            }
            return out.toByteArray().let { if (size != null) it.copyOf(size.toInt()) else it }
        }

        fun list(cluster: Long): List<Entry> {
            val dir = chainBytes(cluster)
            val entries = mutableListOf<Entry>()
            val long = StringBuilder()
            for (at in dir.indices step 32) {
                val first = dir[at].toInt() and 0xFF
                if (first == 0) break
                val attr = dir[at + 11].toInt() and 0xFF
                if (attr == 0x0F) {
                    val part = StringBuilder()
                    for (o in intArrayOf(1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30)) {
                        val c = (dir[at + o].toInt() and 0xFF) or ((dir[at + o + 1].toInt() and 0xFF) shl 8)
                        if (c == 0 || c == 0xFFFF) break
                        part.append(c.toChar())
                    }
                    long.insert(0, part)
                    continue
                }
                val short = String(dir, at, 11, Charsets.ISO_8859_1)
                if (attr and 0x08 != 0 || short.startsWith(".")) {
                    long.clear(); continue
                }
                val e = ByteBuffer.wrap(dir, at, 32).order(ByteOrder.LITTLE_ENDIAN)
                val c = ((e.getShort(at + 20).toLong() and 0xFFFF) shl 16) or (e.getShort(at + 26).toLong() and 0xFFFF)
                val name = long.toString().ifEmpty {
                    val base = short.substring(0, 8).trim()
                    val ext = short.substring(8).trim()
                    if (ext.isEmpty()) base else "$base.$ext"
                }
                entries += Entry(name, short, attr and 0x10 != 0, c, e.getInt(at + 28).toLong() and 0xFFFFFFFFL)
                long.clear()
            }
            return entries
        }

        fun files(cluster: Long = rootCluster, prefix: String = ""): Map<String, ByteArray> = list(cluster).flatMap { e ->
            if (e.isDir) files(e.cluster, "$prefix/${e.name}").toList()
            else listOf("$prefix/${e.name}" to if (e.size == 0L) ByteArray(0) else chainBytes(e.cluster, e.size))
        }.toMap()
    }
}
