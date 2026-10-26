package org.lukemech.lmtoolbox.helpers

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The address QR codes (QrCode) as Project Nayuki's reference encoder (qrcodegen 1.8, byte mode, level M, no
 * boost) makes them: the same version, the same mask picked, the same modules (their rows as 0s and 1s, a line
 * each, SHA-256'd). Nothing on the screen: just the encoder, on the device.
 */
class QrCodeTest {
    private fun digest(code: QrCode): String {
        val rows = (0 until code.size).joinToString("\n") { y -> (0 until code.size).joinToString("") { x -> if (code[x, y]) "1" else "0" } }
        return MessageDigest.getInstance("SHA-256").digest(rows.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun check(text: String, mask: Int?, version: Int, chosen: Int, sha: String) {
        val code = QrCode.encode(text, mask)
        assertEquals(version, code.version)
        assertEquals(version * 4 + 17, code.size)
        assertEquals(chosen, code.mask)
        assertEquals(sha, digest(code))
    }

    @Test
    fun aLanAddress() = check("http://192.168.1.23:8080/", null, 2, 2, "7e0332481e2b05d4b0378db652e485511b916b783ee2c05ab8a57372b492713b")

    @Test
    fun aQuickTunnel() {
        val url = "https://some-random-words-here.trycloudflare.com"
        check(url, null, 4, 2, "4000975089ab402396a937144dc5716fbbfa8a555752dd20b6bf7f2f6b6c796e")
        check(url, 5, 4, 5, "7cc2bc7b4cf36c6613dc9bd2134db333c3bb2210595169b63b1dd7a63df2d530")
    }

    @Test
    fun boresAddress() {
        check("http://bore.pub:41234/", null, 2, 2, "cd73e8fa36f9e9b06aa4e4252b2ddda958be32fd62feff482410ffe55d1c9a88")
        check("http://bore.pub:41234/", 0, 2, 0, "28b650605e46020bebea839496c86b34078d1e70d2fd04a37b65d2cef85ca433")
    }

    @Test
    fun utf8() {
        check("Zażółć gęślą jaźń", null, 2, 2, "96d0a04d3e343bac62c0b1d3f14560bdbeec8d07d537f5bf07daf4a2f51dcf18")
        check("Zażółć gęślą jaźń", 7, 2, 7, "5c023acbcf3cb5994cc8435db4780d9d031ab75ee94d0c0f2b7411dcc70b3510")
    }

    @Test
    fun withVersionInformation() {
        check("x".repeat(150), null, 8, 2, "9782fa7f0642d3e6b22295c01fc9e2fe9a0f19d22af2cf7b3964dcb6a932e44e")
        check("x".repeat(150), 3, 8, 3, "dd8bc6552402d546eac3223a890a1c66065984e643b6ecb8bc59a5859e4ae03b")
    }

    @Test
    fun blocksOfTwoLengthsAndASixteenBitCount() {
        val text = "https://phone.tail1234.ts.net:8080/" + "a".repeat(300)
        check(text, null, 14, 1, "0fac1049f68a882f7bb09cd7dcb564957510e294223224f69fcf710818bc9b39")
        check(text, 6, 14, 6, "6f105820e22fb72ee571f55759bcc36b4de82266e46771da1c62e7fce78caced")
    }

    @Test
    fun tooLong() {
        assertThrows(IllegalArgumentException::class.java) { QrCode.encode("x".repeat(QrCode.MAX_BYTES + 1)) }
        assertEquals(40, QrCode.encode("x".repeat(QrCode.MAX_BYTES)).version)
    }
}
