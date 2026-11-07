package org.lukemech.lmtoolbox.features.media.usb_writer

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/**
 * A drive as its blocks: written and read whole blocks at a time, by their number (LBA). A USB drive's
 * ([UsbMassStorage]), or a file's ([FileBlockDevice]: the tests', a drive's image).
 */
interface BlockDevice : Closeable {
    val blockSize: Int
    val blockCount: Long
    val size: Long get() = blockSize.toLong() * blockCount

    /** [len] bytes (whole blocks) of [buf] onto the drive from block [lba] on. */
    fun write(lba: Long, buf: ByteArray, len: Int)

    /** [len] bytes (whole blocks) from block [lba] on into [buf]. */
    fun read(lba: Long, buf: ByteArray, len: Int)
}

/** A file as a drive of [blockCount] blocks of [blockSize] bytes: a drive's image, the tests'. */
class FileBlockDevice(file: File, override val blockSize: Int, override val blockCount: Long) : BlockDevice {
    private val raf = RandomAccessFile(file, "rw").apply { setLength(blockSize.toLong() * blockCount) }

    override fun write(lba: Long, buf: ByteArray, len: Int) {
        check(lba, len)
        raf.seek(lba * blockSize)
        raf.write(buf, 0, len)
    }

    override fun read(lba: Long, buf: ByteArray, len: Int) {
        check(lba, len)
        raf.seek(lba * blockSize)
        raf.readFully(buf, 0, len)
    }

    private fun check(lba: Long, len: Int) {
        require(len % blockSize == 0) { "Not whole blocks: $len" }
        require(lba >= 0 && lba + len / blockSize <= blockCount) { "Past the end: block $lba, $len bytes" }
    }

    override fun close() = raf.close()
}
