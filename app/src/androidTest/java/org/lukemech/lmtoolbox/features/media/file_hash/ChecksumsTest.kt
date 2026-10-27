package org.lukemech.lmtoolbox.features.media.file_hash

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * File hash's own logic, on the device's own providers: the digests (by the standards' test vectors), checksum
 * files read and written, the checking. SHA-3 by the app's own (SHA3), as Android has none.
 */
class ChecksumsTest {
    private val abc = mapOf(
        HashAlgorithm.MD5 to "900150983cd24fb0d6963f7d28e17f72",
        HashAlgorithm.SHA1 to "a9993e364706816aba3e25717850c26c9cd0d89d",
        HashAlgorithm.SHA224 to "23097d223405d8228642a477bda255b32aadbce4bda0b3f7e36c9da7",
        HashAlgorithm.SHA256 to "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        HashAlgorithm.SHA384 to "cb00753f45a35e8bb5a03d699ac65007272c32ab0eded1631a8b605a43ff5bed8086072ba1e7cc2358baeca134c825a7",
        HashAlgorithm.SHA512 to "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
        HashAlgorithm.SHA3_256 to "3a985da74fe225b2045c172d6bd390bd855f086e3e9d525b46bfe24511431532",
        HashAlgorithm.SHA3_512 to "b751850b1a57168a5693cd924b6b096e08f621827444f70d884f5d0240d2712e10e116e9192af3c91a7ec57647e3934057340b4cf408d5a56592f8274eec53f0",
        HashAlgorithm.CRC32 to "352441c2",
    )

    @Test
    fun abcByEveryAlgorithm() {
        assertEquals(abc, MultiDigest.ofText("abc", HashAlgorithm.entries))
    }

    @Test
    fun ownSHA3ByTheStandardsVectors() {
        assertEquals(abc[HashAlgorithm.SHA3_256], toHex(SHA3(256).digest("abc".toByteArray())))
        assertEquals(abc[HashAlgorithm.SHA3_512], toHex(SHA3(512).digest("abc".toByteArray())))
        assertEquals("a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a", toHex(SHA3(256).digest(ByteArray(0))))
        assertEquals(
            "a69f73cca23a9ac5c8b567dc185a756e97c982164fe25859e0d1dcc1475c80a615b2123af1f5f94c11e3e9402c3ac558f500199d95b6d3e301758586281dcd26",
            toHex(SHA3(512).digest(ByteArray(0)))
        )
        // A million "a"s, in pieces that don't line up with its blocks
        val sha3 = SHA3(256)
        val million = ByteArray(1_000_000) { 'a'.code.toByte() }
        for (at in million.indices step 4093) sha3.update(million, at, minOf(4093, million.size - at))
        assertEquals("5c8875ae474a3634ba4fd55ec85bffd661f32aca75c6d699d0cdcb6c115891c1", toHex(sha3.digest()))
    }

    @Test
    fun hashLengthsAreTheAlgorithms() {
        abc.forEach { (alg, hash) -> assertEquals(alg.label, alg.hexLength, hash.length) }
    }

    @Test
    fun emptyAndKnownText() {
        val empty = MultiDigest.ofText("", listOf(HashAlgorithm.MD5, HashAlgorithm.SHA256, HashAlgorithm.CRC32))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", empty[HashAlgorithm.MD5])
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", empty[HashAlgorithm.SHA256])
        assertEquals("00000000", empty[HashAlgorithm.CRC32])
        assertEquals("414fa339", MultiDigest.ofText("The quick brown fox jumps over the lazy dog", listOf(HashAlgorithm.CRC32))[HashAlgorithm.CRC32])
        // UTF-8: "zażółć" is 10 bytes, not 6
        assertEquals(
            toHex(MessageDigest.getInstance("SHA-256").digest("zażółć".toByteArray(Charsets.UTF_8))),
            MultiDigest.ofText("zażółć", listOf(HashAlgorithm.SHA256))[HashAlgorithm.SHA256]
        )
    }

    @Test
    fun onlyThePickedOnes() {
        assertEquals(setOf(HashAlgorithm.SHA1, HashAlgorithm.CRC32), MultiDigest.ofText("abc", listOf(HashAlgorithm.SHA1, HashAlgorithm.CRC32)).keys)
    }

    @Test
    fun streamedInPiecesOfAnySize() {
        // A million "a"s (FIPS 180's long vectors), handed over a few odd bytes at a time
        val million = ByteArray(1_000_000) { 'a'.code.toByte() }
        val trickling = object : InputStream() {
            private val inner = ByteArrayInputStream(million)
            override fun read() = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int) = inner.read(b, off, minOf(len, 4093))
        }
        var lastRead = 0L
        var calls = 0
        val hashes = MultiDigest.digest(trickling, listOf(HashAlgorithm.SHA1, HashAlgorithm.SHA256)) { lastRead = it; calls++ }
        assertEquals("34aa973cd4c4daa4f61eeb2bdbad27316534016f", hashes[HashAlgorithm.SHA1])
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", hashes[HashAlgorithm.SHA256])
        assertEquals(1_000_000L, lastRead)
        assertTrue(calls > 1)
    }

    @Test
    fun biggerThanTheBufferAsAtOnce() {
        val data = ByteArray(MultiDigest.BUFFER * 3 + 7) { (it * 31 + 7).toByte() }
        val streamed = MultiDigest.digest(ByteArrayInputStream(data), HashAlgorithm.entries)
        HashAlgorithm.entries.filter { it.javaName != null }.forEach { alg ->
            assertEquals(alg.label, toHex(alg.newDigest()!!.digest(data)), streamed[alg])
        }
        val crc = java.util.zip.CRC32().apply { update(data) }
        assertEquals("%08x".format(crc.value), streamed[HashAlgorithm.CRC32])
    }

    @Test
    fun stoppedWhenItsCallbackThrows() {
        val data = ByteArray(MultiDigest.BUFFER * 2)
        var threw = false
        try {
            MultiDigest.digest(ByteArrayInputStream(data), listOf(HashAlgorithm.MD5)) { throw IllegalStateException("cancelled") }
        } catch (_: IllegalStateException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun hex() {
        assertEquals("", toHex(ByteArray(0)))
        assertEquals("00017f80ff", toHex(byteArrayOf(0, 1, 127, -128, -1)))
    }

    @Test
    fun algorithmsByLengthTagAndFileName() {
        assertEquals(listOf(HashAlgorithm.CRC32), HashAlgorithm.byLength(8))
        assertEquals(listOf(HashAlgorithm.MD5), HashAlgorithm.byLength(32))
        assertEquals(listOf(HashAlgorithm.SHA256, HashAlgorithm.SHA3_256), HashAlgorithm.byLength(64))
        assertEquals(listOf(HashAlgorithm.SHA512, HashAlgorithm.SHA3_512), HashAlgorithm.byLength(128))
        assertTrue(HashAlgorithm.byLength(63).isEmpty())

        assertEquals(HashAlgorithm.SHA256, HashAlgorithm.byTag("SHA256"))
        assertEquals(HashAlgorithm.SHA1, HashAlgorithm.byTag("sha-1"))
        assertEquals(HashAlgorithm.SHA3_256, HashAlgorithm.byTag("SHA3-256"))
        assertEquals(HashAlgorithm.MD5, HashAlgorithm.byTag("MD5"))
        assertNull(HashAlgorithm.byTag("BLAKE2b"))

        assertEquals(HashAlgorithm.SHA256, HashAlgorithm.byFileName("SHA256SUMS"))
        assertEquals(HashAlgorithm.SHA256, HashAlgorithm.byFileName("ubuntu-24.04.iso.sha256"))
        assertEquals(HashAlgorithm.SHA512, HashAlgorithm.byFileName("SHA512SUMS.txt"))
        assertEquals(HashAlgorithm.MD5, HashAlgorithm.byFileName("archive.md5"))
        assertEquals(HashAlgorithm.SHA1, HashAlgorithm.byFileName("sha1sum.txt"))
        assertEquals(HashAlgorithm.SHA3_256, HashAlgorithm.byFileName("release.sha3-256"))
        assertEquals(HashAlgorithm.SHA256, HashAlgorithm.byFileName("checksums-SHA-256.txt"))
        assertNull(HashAlgorithm.byFileName("checksums.txt"))
    }

    @Test
    fun gnuChecksumFile() {
        val sums = """
            # made by sha256sum
            ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad  abc.txt
            E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855 *empty.bin
            e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  ./dir/with space.iso
            \e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  back\\slash\nline
            not a line at all
        """.trimIndent()
        val parsed = Checksums.parse(sums, "SHA256SUMS")
        assertEquals(4, parsed.size)
        assertEquals(ExpectedHash("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "abc.txt", HashAlgorithm.SHA256), parsed[0])
        // Binary mode's star isn't the name's; upper case read as lower
        assertEquals("empty.bin", parsed[1].name)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", parsed[1].hash)
        assertEquals("./dir/with space.iso", parsed[2].name)
        assertEquals("back\\slash\nline", parsed[3].name)
    }

    @Test
    fun md5FileWithoutItsAlgorithmInLines() {
        val parsed = Checksums.parse("900150983cd24fb0d6963f7d28e17f72  abc.txt\r\n", "files.md5")
        assertEquals(ExpectedHash("900150983cd24fb0d6963f7d28e17f72", "abc.txt", HashAlgorithm.MD5), parsed.single())
        // Without a name to tell, its length does
        assertEquals(listOf(HashAlgorithm.MD5), Checksums.parse("900150983cd24fb0d6963f7d28e17f72  abc.txt").single().candidates)
    }

    @Test
    fun bsdAndOpenSslLines() {
        val parsed = Checksums.parse(
            """
            SHA256 (abc.txt) = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
            MD5 (file (1).txt) = 900150983cd24fb0d6963f7d28e17f72
            SHA3-256(abc.txt)= 3a985da74fe225b2045c172d6bd390bd855f086e3e9d525b46bfe24511431532
            SHA1 (short.txt) = 900150983cd24fb0d6963f7d28e17f72
            """.trimIndent()
        )
        // The last one's hash isn't as long as its algorithm's: left out
        assertEquals(3, parsed.size)
        assertEquals(ExpectedHash("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "abc.txt", HashAlgorithm.SHA256), parsed[0])
        assertEquals(ExpectedHash("900150983cd24fb0d6963f7d28e17f72", "file (1).txt", HashAlgorithm.MD5), parsed[1])
        assertEquals(HashAlgorithm.SHA3_256, parsed[2].algorithm)
    }

    @Test
    fun bareHashesPasted() {
        assertEquals(
            ExpectedHash("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
            Checksums.parse("  BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD \n").single()
        )
        // Bytes apart, as some pages show them
        assertEquals("352441c2", Checksums.parse("35:24:41:C2").single().hash)
        assertEquals("352441c2", Checksums.parse("0x352441C2").single().hash)
        // Not hex, or no hash's length
        assertTrue(Checksums.parse("hello world").isEmpty())
        assertTrue(Checksums.parse("abcdef").isEmpty())
        assertTrue(Checksums.parse("").isEmpty())
    }

    @Test
    fun entriesForAFile() {
        val expected = Checksums.parse(
            """
            ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad  dir/abc.txt
            e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  Empty.bin
            """.trimIndent(), "SHA256SUMS"
        )
        // By its name alone (no folders), then ignoring case
        assertEquals("dir/abc.txt", Checksums.forName(expected, "abc.txt").single().name)
        assertEquals("Empty.bin", Checksums.forName(expected, "empty.bin").single().name)
        assertTrue(Checksums.forName(expected, "other.iso").isEmpty())
        // One named hash stands for any file (renamed since); a bare one for all
        val one = Checksums.parse("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad  abc.txt")
        assertEquals(one, Checksums.forName(one, "abc (1).txt"))
        val bare = Checksums.parse("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        assertEquals(bare, Checksums.forName(bare, "anything"))
        assertEquals(bare, Checksums.forName(bare, null))
    }

    @Test
    fun verifyMatchesTheRightRow() {
        val computed = abc.filterKeys { it in setOf(HashAlgorithm.MD5, HashAlgorithm.SHA256, HashAlgorithm.SHA3_256) }
        // 64 digits: SHA-256 or SHA3-256; the one that matches is marked, the other left alone
        val ok = Checksums.verify(computed, Checksums.parse(abc.getValue(HashAlgorithm.SHA256).uppercase()))
        assertEquals(setOf(HashAlgorithm.SHA256), ok.matched)
        assertTrue(ok.mismatched.isEmpty())
        assertTrue(ok.isMatch)

        val sha3 = Checksums.verify(computed, Checksums.parse(abc.getValue(HashAlgorithm.SHA3_256)))
        assertEquals(setOf(HashAlgorithm.SHA3_256), sha3.matched)

        // Wrong: both rows it could be are mismatches
        val wrong = Checksums.verify(computed, Checksums.parse("0".repeat(64)))
        assertEquals(setOf(HashAlgorithm.SHA256, HashAlgorithm.SHA3_256), wrong.mismatched)
        assertTrue(wrong.isMismatch)
        assertFalse(wrong.isMatch)

        // Said to be SHA-256: only that row
        val tagged = Checksums.verify(computed, Checksums.parse("SHA256 (abc) = ${"0".repeat(64)}"))
        assertEquals(setOf(HashAlgorithm.SHA256), tagged.mismatched)
    }

    @Test
    fun verifyTellsWhatsToComputeYet() {
        val computed = abc.filterKeys { it == HashAlgorithm.MD5 }
        val verdict = Checksums.verify(computed, Checksums.parse(abc.getValue(HashAlgorithm.SHA512)))
        assertTrue(verdict.matched.isEmpty() && verdict.mismatched.isEmpty())
        assertEquals(setOf(HashAlgorithm.SHA512), verdict.missing)
        // Nothing expected: nothing to tell
        assertEquals(Checksums.Verdict(emptySet(), emptySet(), emptySet()), Checksums.verify(computed, emptyList()))
    }

    @Test
    fun formatAsSha256sumWritesIt() {
        val text = Checksums.format(listOf("abc.txt" to "ba78", "back\\slash.txt" to "e3b0"))
        assertEquals("ba78  abc.txt\n\\e3b0  back\\\\slash.txt\n", text)
        // And reads back as it was
        val sums = Checksums.format(listOf("a b.iso" to abc.getValue(HashAlgorithm.SHA256), "x\\y" to abc.getValue(HashAlgorithm.SHA256)))
        assertEquals(listOf("a b.iso", "x\\y"), Checksums.parse(sums, "x.sha256").map { it.name })
    }
}
