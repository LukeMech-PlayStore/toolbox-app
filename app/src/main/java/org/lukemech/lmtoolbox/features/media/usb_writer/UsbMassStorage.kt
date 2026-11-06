package org.lukemech.lmtoolbox.features.media.usb_writer

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The drive has no medium in it (a card reader without a card). */
class NoMediumException : IOException("No medium in the drive")

/** A SCSI command failed: its sense key and additional sense code (and qualifier). */
class ScsiException(val command: Int, val senseKey: Int, val asc: Int, val ascq: Int) :
    IOException("SCSI command 0x%02x failed: sense %x/%02x/%02x".format(command, senseKey, asc, ascq))

/**
 * A USB drive's logical unit [lun], spoken to over USB mass storage's bulk-only transport (BOT): SCSI commands
 * wrapped in command blocks (CBW), their data, a status block (CSW) back. The few commands a drive maker needs
 * (TEST UNIT READY, REQUEST SENSE, READ CAPACITY(10), READ(10), WRITE(10)), with what drives need when they
 * misbehave: a failed command retried (from its data's start: nothing's half-sent twice), a stalled endpoint
 * cleared, the transport reset (bulk-only reset recovery). READ(10)/WRITE(10): 2 TiB at most.
 *
 * [connection]'s interface is claimed (Android lets go of the drive: it's unmounted) until [close].
 */
class UsbMassStorage private constructor(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val inEp: UsbEndpoint,
    private val outEp: UsbEndpoint,
    private val lun: Int,
) : BlockDevice {
    override var blockSize = 0
        private set
    override var blockCount = 0L
        private set

    @Volatile
    private var closed = false
    /** Each transfer's: short while it's set up ([init]), long after (a write of a MiB to a slow drive). */
    private var timeout = INIT_TIMEOUT_MS
    /** The biggest bulk transfer this phone takes (see [bulk]). */
    private var maxTransfer = MAX_TRANSFER
    private var tag = 1
    private val cbw = ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN)
    private val csw = ByteArray(13)

    companion object {
        private const val TAG = "UsbMassStorage"
        private const val TIMEOUT_MS = 20_000
        /** While it's set up (a drive that doesn't answer is told of soon, not after minutes). */
        private const val INIT_TIMEOUT_MS = 5_000
        /** At most this much in one bulk transfer, at first (see [bulk]); down to [MIN_TRANSFER]. */
        private const val MAX_TRANSFER = 256 * 1024
        private const val MIN_TRANSFER = 16 * 1024
        /** A transfer failing faster than this wasn't waited for: refused, not timed out. */
        private const val FAST_FAILURE_MS = 1000L
        /** How long the status is waited for after a failed data stage (a stalled endpoint's comes at once). */
        private const val STATUS_AFTER_FAILURE_MS = 2000
        private const val RETRIES = 3

        /**
         * The drive behind [connection] (its [usbInterface] claimed here), its first logical unit with a medium:
         * ready, its size known. Closes the connection when it fails.
         */
        fun open(connection: UsbDeviceConnection, usbInterface: UsbInterface, inEp: UsbEndpoint, outEp: UsbEndpoint): UsbMassStorage {
            try {
                if (!connection.claimInterface(usbInterface, true)) throw IOException("Couldn't claim the drive's interface")
                // No SET_INTERFACE: Android's driver let go of it (uas, usb-storage), the kernel put the interface
                // back in its alternate setting 0, bulk-only, already; asked for again, a SanDisk drive gave no status
                // to the command after it
                // Get Max LUN: drives with one may stall it
                val maxLun = ByteArray(1)
                val n = connection.controlTransfer(0xA1, 0xFE, 0, usbInterface.id, maxLun, 1, INIT_TIMEOUT_MS)
                val luns = if (n == 1) (maxLun[0].toInt() and 0x0F) else 0
                var noMedium: Exception? = null
                for (lun in 0..luns) {
                    val drive = UsbMassStorage(connection, usbInterface, inEp, outEp, lun)
                    try {
                        drive.init()
                        Log.i(TAG, "LUN $lun: ${drive.blockCount} blocks of ${drive.blockSize}")
                        return drive
                    } catch (e: NoMediumException) {
                        noMedium = e
                    }
                }
                throw noMedium ?: NoMediumException()
            } catch (e: Exception) {
                runCatching { connection.releaseInterface(usbInterface) }
                connection.close()
                throw e
            }
        }
    }

    private fun init() {
        // A drive just plugged in (or reset) tells of it once (UNIT ATTENTION), or isn't ready yet: asked again
        var ready = false
        for (attempt in 0 until 10) {
            try {
                command(byteArrayOf(0x00, 0, 0, 0, 0, 0), null, 0, 0, input = true)
                ready = true
                break
            } catch (e: ScsiException) {
                if (e.senseKey == 0x02 && e.asc == 0x3A) throw NoMediumException()
                if (e.senseKey != 0x06 && e.senseKey != 0x02) throw e
                Thread.sleep(200)
            }
        }
        if (!ready) throw IOException("The drive isn't ready")
        val capacity = ByteArray(8)
        command(byteArrayOf(0x25, 0, 0, 0, 0, 0, 0, 0, 0, 0), capacity, 0, 8, input = true)
        val b = ByteBuffer.wrap(capacity).order(ByteOrder.BIG_ENDIAN)
        val lastLba = b.getInt(0).toLong() and 0xFFFFFFFFL
        if (lastLba == 0xFFFFFFFFL) throw IOException("Drives over 2 TiB aren't supported")
        blockSize = b.getInt(4)
        if (blockSize !in setOf(512, 1024, 2048, 4096)) throw IOException("Block size $blockSize isn't supported")
        blockCount = lastLba + 1
        timeout = TIMEOUT_MS
    }

    /** What the drive says it is (INQUIRY): its vendor's and product's names, e.g. "SanDisk" "Ultra". */
    fun inquiry(): String {
        val data = ByteArray(36)
        command(byteArrayOf(0x12, 0, 0, 0, 36, 0), data, 0, 36, input = true)
        val vendor = String(data, 8, 8, Charsets.US_ASCII).trim()
        val product = String(data, 16, 16, Charsets.US_ASCII).trim()
        return listOf(vendor, product).filter { it.isNotEmpty() }.joinToString(" ")
    }

    override fun write(lba: Long, buf: ByteArray, len: Int) = transfer(0x2A, lba, buf, len, input = false)

    override fun read(lba: Long, buf: ByteArray, len: Int) = transfer(0x28, lba, buf, len, input = true)

    /** READ(10) or WRITE(10) of [len] bytes (whole blocks, up to 65535 of them) from block [lba] on. */
    private fun transfer(op: Int, lba: Long, buf: ByteArray, len: Int, input: Boolean) {
        require(len % blockSize == 0 && len / blockSize <= 0xFFFF) { "$len bytes" }
        require(lba >= 0 && lba + len / blockSize <= blockCount) { "Past the end: block $lba" }
        val blocks = len / blockSize
        val cdb = byteArrayOf(
            op.toByte(), 0,
            (lba ushr 24).toByte(), (lba ushr 16).toByte(), (lba ushr 8).toByte(), lba.toByte(),
            0, (blocks ushr 8).toByte(), blocks.toByte(), 0
        )
        command(cdb, buf, 0, len, input)
    }

    /**
     * One SCSI command [cdb] with [len] bytes of data in [buf] (from [off]), in or out: retried as a whole when the
     * transfer fails (the transport reset first) or the drive says to (UNIT ATTENTION, NOT READY becoming ready).
     */
    private fun command(cdb: ByteArray, buf: ByteArray?, off: Int, len: Int, input: Boolean) {
        var last: IOException? = null
        for (attempt in 0..RETRIES) {
            checkOpen()
            try {
                val status = transport(cdb, buf, off, len, input)
                if (status == 0) return
                if (status == 2) throw IOException("Phase error")
                // Failed: why, by REQUEST SENSE
                val sense = ByteArray(18)
                transport(byteArrayOf(0x03, 0, 0, 0, 18, 0), sense, 0, 18, input = true)
                val key = sense[2].toInt() and 0x0F
                val error = ScsiException(cdb[0].toInt() and 0xFF, key, sense[12].toInt() and 0xFF, sense[13].toInt() and 0xFF)
                // Only what may go away by itself is retried
                if (key != 0x06 && key != 0x02 && key != 0x0B) throw error
                if (key == 0x02 && error.asc == 0x3A) throw error
                last = error
                Thread.sleep(100)
            } catch (e: ScsiException) {
                throw e
            } catch (e: IOException) {
                if (closed) throw e
                last = e
                Log.w(TAG, "Command 0x%02x failed (attempt ${attempt + 1}): ${e.message}".format(cdb[0]))
                runCatching { resetRecovery() }
            }
        }
        throw last ?: IOException("Command failed")
    }

    /** CBW, data, CSW: the command's status (0 passed, 1 failed, 2 phase error). */
    private fun transport(cdb: ByteArray, buf: ByteArray?, off: Int, len: Int, input: Boolean): Int {
        val myTag = tag++
        cbw.clear()
        cbw.putInt(0x43425355).putInt(myTag).putInt(len)
            .put(if (input) 0x80.toByte() else 0).put(lun.toByte()).put(cdb.size.toByte()).put(cdb)
        while (cbw.position() < 31) cbw.put(0)
        bulk(outEp, cbw.array(), 0, 31)

        // A data stage that failed: the endpoint may have stalled (the drive refused it: cleared, and its status
        // comes at once), or the drive still waits for the rest (no status ever: reset recovery, by the caller)
        var dataFailed = false
        if (len > 0 && buf != null) {
            try {
                bulk(if (input) inEp else outEp, buf, off, len)
            } catch (e: IOException) {
                Log.w(TAG, "Data stage failed: ${e.message}")
                dataFailed = true
                clearHalt(if (input) inEp else outEp)
            }
        }

        val wait = if (dataFailed) STATUS_AFTER_FAILURE_MS else timeout
        var got = statusTransfer(wait)
        if (got != 13 && !dataFailed) {
            // A stalled status: cleared and read again, once
            clearHalt(inEp)
            got = statusTransfer(wait)
        }
        if (got != 13) throw IOException("No status from the drive ($got)${if (dataFailed) " after a failed data transfer" else ""}")
        val c = ByteBuffer.wrap(csw).order(ByteOrder.LITTLE_ENDIAN)
        if (c.getInt(0) != 0x53425355 || c.getInt(4) != myTag) throw IOException("Bad status from the drive")
        return csw[12].toInt() and 0xFF
    }

    private fun statusTransfer(timeout: Int): Int {
        checkOpen()
        return runCatching { connection.bulkTransfer(inEp, csw, 13, timeout) }.getOrDefault(-1)
    }

    /**
     * [len] bytes of [buf] (from [off]) to or from [ep], in as many transfers as it takes. A transfer refused at
     * once (some phones' kernels can't take big ones: usbfs can't get the memory) is made again, smaller: nothing of
     * it went out, so the drive's none the wiser. The size that worked is kept for the next ones.
     */
    private fun bulk(ep: UsbEndpoint, buf: ByteArray, off: Int, len: Int) {
        var done = 0
        while (done < len) {
            checkOpen()
            val size = minOf(maxTransfer, len - done)
            val started = SystemClock.elapsedRealtime()
            val n = connection.bulkTransfer(ep, buf, off + done, size, timeout)
            if (n <= 0) {
                val took = SystemClock.elapsedRealtime() - started
                if (n < 0 && took < FAST_FAILURE_MS && maxTransfer > MIN_TRANSFER) {
                    maxTransfer = maxOf(MIN_TRANSFER, maxTransfer / 2)
                    Log.w(TAG, "A $size-byte transfer refused at once ($took ms): ${maxTransfer} bytes from now on")
                    continue
                }
                throw IOException("USB transfer of $size bytes failed ($n, $took ms) after $done of $len bytes")
            }
            done += n
        }
    }

    private fun checkOpen() {
        if (closed) throw IOException("The drive was closed")
    }

    /** CLEAR_FEATURE(ENDPOINT_HALT) on [ep]. */
    private fun clearHalt(ep: UsbEndpoint) {
        connection.controlTransfer(0x02, 0x01, 0, ep.address, null, 0, INIT_TIMEOUT_MS)
    }

    /**
     * Bulk-only mass storage reset, then both endpoints' halts cleared (USB MSC BOT 5.3.4); the interface claimed
     * again first, in case it was lost (the drive reset: the kernel let go of it, "did not claim interface").
     */
    private fun resetRecovery() {
        checkOpen()
        connection.claimInterface(usbInterface, true)
        connection.controlTransfer(0x21, 0xFF, 0, usbInterface.id, null, 0, INIT_TIMEOUT_MS)
        clearHalt(inEp)
        clearHalt(outEp)
    }

    /**
     * Gives the drive back. From another thread too, while a transfer's under way (a job canceled): that transfer
     * then fails at once, rather than after its timeout.
     */
    override fun close() {
        synchronized(this) {
            if (closed) return
            closed = true
        }
        runCatching { connection.releaseInterface(usbInterface) }
        connection.close()
    }
}
