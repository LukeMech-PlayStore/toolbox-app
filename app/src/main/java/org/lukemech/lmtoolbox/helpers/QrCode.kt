package org.lukemech.lmtoolbox.helpers

import kotlin.math.abs

/**
 * A QR code of a text (a link, an address), small and quick, for showing on the screen: its bytes (UTF-8) in
 * byte mode, error correction level M (15% of it can be lost), the smallest version that holds them and the
 * mask with the lowest penalty, as ISO/IEC 18004 has it (after Project Nayuki's QR Code generator, MIT).
 * The QR generator's codes are another matter: styled, saved and shared, they're its Python's.
 *
 * [size] modules a side; [get] tells a module's color (true: dark), without the quiet zone around it.
 */
class QrCode private constructor(val version: Int, val mask: Int, private val modules: Array<BooleanArray>) {
    val size get() = modules.size

    operator fun get(x: Int, y: Int): Boolean = modules[y][x]

    companion object {
        /** The longest text in bytes it takes (version 40's: its mode and 16-bit count first). */
        val MAX_BYTES get() = dataCodewords(40) - 3

        /**
         * [text]'s code; [mask] forces one (0 to 7) rather than the best. Too long a text (over [MAX_BYTES]
         * bytes) throws IllegalArgumentException.
         */
        fun encode(text: String, mask: Int? = null): QrCode {
            require(mask == null || mask in 0..7) { "No mask $mask" }
            val data = text.toByteArray(Charsets.UTF_8)
            val version = (1..40).firstOrNull { v -> 4 + countBits(v) + data.size * 8 <= dataCodewords(v) * 8 }
                ?: throw IllegalArgumentException("Too long for a QR code: ${data.size} bytes")

            // Its bits: byte mode, the count, the bytes, the terminator; then padded to its capacity
            val bits = Bits()
            bits.add(0b0100, 4)
            bits.add(data.size, countBits(version))
            data.forEach { bits.add(it.toInt() and 0xFF, 8) }
            val capacity = dataCodewords(version) * 8
            bits.add(0, minOf(4, capacity - bits.length))
            bits.add(0, (8 - bits.length % 8) % 8)
            var pad = 0xEC
            while (bits.length < capacity) {
                bits.add(pad, 8)
                pad = pad xor (0xEC xor 0x11)
            }

            val grid = Grid(version)
            grid.drawFunctionPatterns()
            grid.drawCodewords(withErrorCorrection(bits.bytes(), version))
            // The one with the lowest penalty, unless one's asked for
            val chosen = mask ?: (0..7).minBy { m ->
                grid.applyMask(m)
                grid.drawFormatBits(m)
                grid.penalty().also { grid.applyMask(m) }
            }
            grid.applyMask(chosen)
            grid.drawFormatBits(chosen)
            return QrCode(version, chosen, grid.modules)
        }

        /** The count's bits in byte mode. */
        private fun countBits(version: Int) = if (version <= 9) 8 else 16

        /** Modules that hold data (all but the function patterns and the format and version information). */
        private fun rawDataModules(version: Int): Int {
            var result = (16 * version + 128) * version + 64
            if (version >= 2) {
                val align = version / 7 + 2
                result -= (25 * align - 10) * align - 55
                if (version >= 7) result -= 36
            }
            return result
        }

        private fun dataCodewords(version: Int) = rawDataModules(version) / 8 - ECC_PER_BLOCK[version] * BLOCKS[version]

        /** [data] split into its blocks, each with its Reed-Solomon codewords after it, then interleaved. */
        private fun withErrorCorrection(data: ByteArray, version: Int): ByteArray {
            val blocks = BLOCKS[version]
            val eccLength = ECC_PER_BLOCK[version]
            val raw = rawDataModules(version) / 8
            val shortBlocks = blocks - raw % blocks
            val shortLength = raw / blocks
            val divisor = reedSolomonDivisor(eccLength)
            var k = 0
            val all = (0 until blocks).map { i ->
                val dataLength = shortLength - eccLength + if (i < shortBlocks) 0 else 1
                val block = data.copyOfRange(k, k + dataLength)
                k += dataLength
                val ecc = reedSolomonRemainder(block, divisor)
                // A short block gets a placeholder, where the long ones have their last data codeword
                (if (i < shortBlocks) block + 0 else block) + ecc
            }
            val result = ByteArray(raw)
            var n = 0
            for (i in all[0].indices) {
                all.forEachIndexed { j, block ->
                    if (i != shortLength - eccLength || j >= shortBlocks) result[n++] = block[i]
                }
            }
            return result
        }

        private fun reedSolomonDivisor(degree: Int): IntArray {
            val result = IntArray(degree)
            result[degree - 1] = 1
            var root = 1
            repeat(degree) {
                for (j in result.indices) {
                    result[j] = gfMultiply(result[j], root)
                    if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
                }
                root = gfMultiply(root, 0x02)
            }
            return result
        }

        private fun reedSolomonRemainder(data: ByteArray, divisor: IntArray): ByteArray {
            val result = IntArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() and 0xFF) xor result[0]
                System.arraycopy(result, 1, result, 0, result.size - 1)
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = result[i] xor gfMultiply(divisor[i], factor)
            }
            return ByteArray(result.size) { result[it].toByte() }
        }

        /** Multiplied in GF(2^8), modulo x^8 + x^4 + x^3 + x^2 + 1. */
        private fun gfMultiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        // Level M's error correction, by version (0: none)
        private val ECC_PER_BLOCK = intArrayOf(
            0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26,
            26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28
        )
        private val BLOCKS = intArrayOf(
            0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
            17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49
        )
        /** Level M's two format bits. */
        private const val LEVEL_M = 0
    }

    /** Bits appended most significant first. */
    private class Bits {
        private val bits = ArrayList<Boolean>()
        val length get() = bits.size

        fun add(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits += (value ushr i) and 1 != 0
        }

        fun bytes() = ByteArray(bits.size / 8) { i -> (0 until 8).fold(0) { acc, j -> (acc shl 1) or if (bits[i * 8 + j]) 1 else 0 }.toByte() }
    }

    /** The modules as they're drawn, and which are the function patterns' (not data, not masked). */
    private class Grid(val version: Int) {
        val size = version * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        private val function = Array(size) { BooleanArray(size) }

        private fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            function[y][x] = true
        }

        fun drawFunctionPatterns() {
            // Timing patterns, then the finders over them
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)
            val align = alignmentPositions()
            for (i in align.indices) for (j in align.indices) {
                // Not where the finders are
                val corner = (i == 0 && j == 0) || (i == 0 && j == align.lastIndex) || (i == align.lastIndex && j == 0)
                if (!corner) alignment(align[i], align[j])
            }
            // Its format's place taken (written once the mask is known), and its version's
            drawFormatBits(0)
            drawVersion()
        }

        private fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                val dist = maxOf(abs(dx), abs(dy))
                if (x in 0 until size && y in 0 until size) set(x, y, dist != 2 && dist != 4)
            }
        }

        private fun alignment(cx: Int, cy: Int) {
            for (dy in -2..2) for (dx in -2..2) set(cx + dx, cy + dy, maxOf(abs(dx), abs(dy)) != 1)
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val count = version / 7 + 2
            val step = (version * 8 + count * 3 + 5) / (count * 4 - 4) * 2
            val result = IntArray(count)
            result[0] = 6
            var pos = size - 7
            for (i in count - 1 downTo 1) {
                result[i] = pos
                pos -= step
            }
            return result
        }

        fun drawFormatBits(mask: Int) {
            val data = LEVEL_M shl 3 or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = (data shl 10 or rem) xor 0x5412
            fun bit(i: Int) = (bits ushr i) and 1 != 0
            // Around the top left finder
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))
            // Its copy, by the other two
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true)
        }

        private fun drawVersion() {
            if (version < 7) return
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            val bits = version shl 12 or rem
            for (i in 0 until 18) {
                val dark = (bits ushr i) and 1 != 0
                val a = size - 11 + i % 3
                val b = i / 3
                set(a, b, dark)
                set(b, a, dark)
            }
        }

        /** The codewords' bits, two columns at a time from the right, up and down in turn (the timing column skipped). */
        fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val upward = (right + 1) and 2 == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!function[y][x] && i < data.size * 8) {
                        modules[y][x] = (data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1 != 0
                        i++
                    }
                }
                right -= 2
            }
        }

        /** Flips the data modules [mask] picks (doing it again undoes it). */
        fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert && !function[y][x]) modules[y][x] = !modules[y][x]
            }
        }

        /** How hard it is to read as masked: runs, 2×2 blocks, finder-like patterns, dark and light out of balance. */
        fun penalty(): Int {
            var result = 0
            for (line in 0 until size) {
                result += linePenalty { modules[line][it] }
                result += linePenalty { modules[it][line] }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += 3
            }
            val dark = modules.sumOf { row -> row.count { it } }
            val total = size * size
            val k = (abs(dark * 20 - total * 10) + total - 1) / total - 1
            return result + k * 10
        }

        /** A row's (or column's) runs of five or more alike, and its finder-like patterns. */
        private fun linePenalty(at: (Int) -> Boolean): Int {
            var result = 0
            var color = false
            var run = 0
            val history = IntArray(7)
            fun addHistory(length: Int) {
                // The light quiet zone before the first run
                val l = if (history[0] == 0) length + size else length
                System.arraycopy(history, 0, history, 1, history.size - 1)
                history[0] = l
            }
            fun countPatterns(): Int {
                val n = history[1]
                val core = n > 0 && history[2] == n && history[3] == n * 3 && history[4] == n && history[5] == n
                return (if (core && history[0] >= n * 4 && history[6] >= n) 1 else 0) +
                    (if (core && history[6] >= n * 4 && history[0] >= n) 1 else 0)
            }
            for (i in 0 until size) {
                if (at(i) == color) {
                    run++
                    if (run == 5) result += 3 else if (run > 5) result++
                } else {
                    addHistory(run)
                    if (!color) result += countPatterns() * 40
                    color = at(i)
                    run = 1
                }
            }
            // The last run, then the quiet zone after it
            if (color) {
                addHistory(run)
                run = 0
            }
            addHistory(run + size)
            return result + countPatterns() * 40
        }
    }
}
