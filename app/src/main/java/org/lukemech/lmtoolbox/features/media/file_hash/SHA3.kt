package org.lukemech.lmtoolbox.features.media.file_hash

import java.security.MessageDigest

/**
 * SHA-3 (FIPS 202) of [bits] (256, 512...), which Android's providers don't have: Keccak-f[1600] over a sponge
 * of [rate] bytes, as the reference's compact form has it.
 */
class SHA3(private val bits: Int) : MessageDigest("SHA3-$bits") {
    private val state = LongArray(25)
    /** Bytes absorbed per permutation: what the capacity (twice the output) leaves of 200. */
    private val rate = 200 - 2 * (bits / 8)
    /** Where the next byte goes in the block. */
    private var position = 0

    override fun engineUpdate(input: Byte) {
        state[position / 8] = state[position / 8] xor ((input.toLong() and 0xff) shl (8 * (position % 8)))
        if (++position == rate) {
            keccak(state)
            position = 0
        }
    }

    override fun engineUpdate(input: ByteArray, offset: Int, len: Int) {
        for (i in offset until offset + len) engineUpdate(input[i])
    }

    override fun engineDigest(): ByteArray {
        // SHA-3's domain bits and the padding's first 1, then its last one at the block's end
        state[position / 8] = state[position / 8] xor (0x06L shl (8 * (position % 8)))
        state[(rate - 1) / 8] = state[(rate - 1) / 8] xor (0x80L shl (8 * ((rate - 1) % 8)))
        keccak(state)
        val out = ByteArray(bits / 8) { (state[it / 8] ushr (8 * (it % 8))).toByte() }
        engineReset()
        return out
    }

    override fun engineReset() {
        state.fill(0)
        position = 0
    }

    override fun engineGetDigestLength() = bits / 8

    private companion object {
        val ROUND_CONSTANTS = longArrayOf(
            0x0000000000000001uL.toLong(), 0x0000000000008082uL.toLong(), 0x800000000000808auL.toLong(), 0x8000000080008000uL.toLong(),
            0x000000000000808buL.toLong(), 0x0000000080000001uL.toLong(), 0x8000000080008081uL.toLong(), 0x8000000000008009uL.toLong(),
            0x000000000000008auL.toLong(), 0x0000000000000088uL.toLong(), 0x0000000080008009uL.toLong(), 0x000000008000000auL.toLong(),
            0x000000008000808buL.toLong(), 0x800000000000008buL.toLong(), 0x8000000000008089uL.toLong(), 0x8000000000008003uL.toLong(),
            0x8000000000008002uL.toLong(), 0x8000000000000080uL.toLong(), 0x000000000000800auL.toLong(), 0x800000008000000auL.toLong(),
            0x8000000080008081uL.toLong(), 0x8000000000008080uL.toLong(), 0x0000000080000001uL.toLong(), 0x8000000080008008uL.toLong(),
        )
        // ρ's rotations and π's lanes, in the order the lanes are walked
        val ROTATIONS = intArrayOf(1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 2, 14, 27, 41, 56, 8, 25, 43, 62, 18, 39, 61, 20, 44)
        val LANES = intArrayOf(10, 7, 11, 17, 18, 3, 5, 16, 8, 21, 24, 4, 15, 23, 19, 13, 12, 2, 20, 14, 22, 9, 6, 1)

        /** Keccak-f[1600]: its 24 rounds over [s]. */
        fun keccak(s: LongArray) {
            val c = LongArray(5)
            for (round in 0 until 24) {
                // θ
                for (i in 0 until 5) c[i] = s[i] xor s[i + 5] xor s[i + 10] xor s[i + 15] xor s[i + 20]
                for (i in 0 until 5) {
                    val t = c[(i + 4) % 5] xor c[(i + 1) % 5].rotateLeft(1)
                    for (j in 0 until 25 step 5) s[j + i] = s[j + i] xor t
                }
                // ρ and π
                var t = s[1]
                for (i in 0 until 24) {
                    val lane = LANES[i]
                    val next = s[lane]
                    s[lane] = t.rotateLeft(ROTATIONS[i])
                    t = next
                }
                // χ
                for (j in 0 until 25 step 5) {
                    for (i in 0 until 5) c[i] = s[j + i]
                    for (i in 0 until 5) s[j + i] = s[j + i] xor (c[(i + 1) % 5].inv() and c[(i + 2) % 5])
                }
                // ι
                s[0] = s[0] xor ROUND_CONSTANTS[round]
            }
        }
    }
}
