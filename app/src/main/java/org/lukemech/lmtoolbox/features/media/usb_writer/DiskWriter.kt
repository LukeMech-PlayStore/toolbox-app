package org.lukemech.lmtoolbox.features.media.usb_writer

import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What was read back from the drive isn't what was written, at byte [offset]. */
class VerificationException(val offset: Long) : IOException("The drive doesn't read back what was written (at byte $offset)")

/** The drive is too small for what's to be written. */
class NotEnoughSpaceException(val needed: Long, val available: Long) :
    IOException("The drive is too small: $needed bytes needed, it has $available")

/**
 * A [DiskPlan] written onto a [BlockDevice], then read back and compared ([verify]). Its content is read
 * (from a picked file: its provider's speed) while the drive writes what was read before, a few [CHUNK]s
 * ahead; each chunk is one write (whole blocks: what an extent leaves of its last block is zeros).
 */
object DiskWriter {
    /** One write or read: 1 MiB, as EtchDroid has it (2048 blocks of 512 bytes, in one SCSI command). */
    const val CHUNK = 1 shl 20
    private const val AHEAD = 3

    fun interface Progress {
        /** How far it is: [done] of [total] bytes of the plan's content. */
        fun update(done: Long, total: Long)
    }

    suspend fun write(plan: DiskPlan, device: BlockDevice, progress: Progress) {
        check(plan, device)
        stream(plan, device.blockSize, progress) { chunk -> device.write(chunk.lba, chunk.buf, chunk.len) }
    }

    /** Reads every extent back and compares it with what it should be: [VerificationException] where it isn't. */
    suspend fun verify(plan: DiskPlan, device: BlockDevice, progress: Progress) {
        check(plan, device)
        val read = ByteArray(CHUNK)
        stream(plan, device.blockSize, progress) { chunk ->
            device.read(chunk.lba, read, chunk.len)
            if (ByteBuffer.wrap(read, 0, chunk.len) != ByteBuffer.wrap(chunk.buf, 0, chunk.len)) {
                val at = (0 until chunk.len).first { read[it] != chunk.buf[it] }
                throw VerificationException(chunk.lba * device.blockSize + at)
            }
        }
    }

    private fun check(plan: DiskPlan, device: BlockDevice) {
        if (plan.requiredSize > device.size) throw NotEnoughSpaceException(plan.requiredSize, device.size)
        require(CHUNK % device.blockSize == 0) { "Block size ${device.blockSize}" }
    }

    /** Whole blocks from block [lba] on: [len] bytes of [buf], [payload] of them the plan's content. */
    private class Chunk(val buf: ByteArray, var lba: Long = 0, var len: Int = 0, var payload: Int = 0)

    /**
     * The plan as chunks of whole blocks, each given to [consume] in order (on this thread), read meanwhile on
     * another one. What's in a block but not in an extent is zeros.
     */
    private suspend fun stream(plan: DiskPlan, blockSize: Int, progress: Progress, consume: (Chunk) -> Unit) =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val free = Channel<Chunk>(AHEAD)
                repeat(AHEAD) { free.send(Chunk(ByteArray(CHUNK))) }
                val full = Channel<Chunk>(AHEAD)
                launch {
                    try {
                        produce(plan, blockSize, free, full)
                        full.close()
                    } catch (e: Throwable) {
                        full.close(e)
                        throw e
                    }
                }
                var done = 0L
                progress.update(0, plan.total)
                for (chunk in full) {
                    ensureActive()
                    consume(chunk)
                    done += chunk.payload
                    progress.update(done, plan.total)
                    free.send(chunk)
                }
            }
        }

    private suspend fun kotlinx.coroutines.CoroutineScope.produce(
        plan: DiskPlan, blockSize: Int, free: Channel<Chunk>, full: Channel<Chunk>,
    ) {
        var chunk: Chunk? = null
        // The drive's byte chunk.buf[0] stands for, and how much of it is filled
        var start = 0L
        var filled = 0

        suspend fun emit() {
            val c = chunk ?: return
            val len = ((filled + blockSize - 1) / blockSize) * blockSize
            java.util.Arrays.fill(c.buf, filled, len, 0)
            c.lba = start / blockSize
            c.len = len
            full.send(c)
            chunk = null
        }

        /** A new chunk, from the block [offset] is in: zeros up to it. */
        suspend fun begin(offset: Long) {
            val c = free.receive().also { it.payload = 0 }
            chunk = c
            start = offset - offset % blockSize
            filled = (offset - start).toInt()
            java.util.Arrays.fill(c.buf, 0, filled, 0)
        }

        for (extent in plan.extents) {
            val at = extent.offset
            val current = chunk
            when {
                current == null -> begin(at)
                at < start + filled -> error("Extent at $at overlaps the one before")
                at < start + CHUNK -> {
                    // In this chunk still: zeros up to it
                    val gap = (at - start).toInt()
                    java.util.Arrays.fill(current.buf, filled, gap, 0)
                    filled = gap
                }
                else -> {
                    emit()
                    begin(at)
                }
            }
            extent.content.open().use { input ->
                var left = extent.content.length
                while (left > 0) {
                    ensureActive()
                    if (filled == CHUNK) {
                        val next = start + CHUNK
                        emit()
                        begin(next)
                    }
                    val c = chunk!!
                    val n = input.read(c.buf, filled, minOf((CHUNK - filled).toLong(), left).toInt())
                    if (n < 0) throw EOFException("The image ended $left bytes early")
                    filled += n
                    c.payload += n
                    left -= n
                }
            }
        }
        emit()
    }
}
