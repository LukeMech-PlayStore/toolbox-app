package org.lukemech.lmtoolbox.features.media.qr_gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lukemech.lmtoolbox.ui.pages.features.media.qr_gen.QrType

class QrPayloadTest {
    @Test
    @Suppress("HttpUrlsUsage") // A link typed with http keeps it
    fun `links get a scheme when they have none`() {
        assertEquals("https://example.com", QrPayload.url(" example.com "))
        assertEquals("http://example.com", QrPayload.url("http://example.com"))
        assertEquals("mailto:a@b.c", QrPayload.url("mailto:a@b.c"))
    }

    @Test
    fun `wifi escapes its special characters`() {
        assertEquals("""WIFI:T:WPA;S:My\;Net;P:pa\:ss\\;;""", QrPayload.wifi("My;Net", """pa:ss\""", "WPA", hidden = false))
        assertEquals("WIFI:T:nopass;S:Open;H:true;;", QrPayload.wifi("Open", "ignored", "nopass", hidden = true))
    }

    @Test
    fun `contact is a vCard with only what was given`() {
        val card = QrPayload.vCard("Jan", "Kowalski", "+48 123", "", "", "")
        assertEquals("BEGIN:VCARD\nVERSION:3.0\nN:Kowalski;Jan;;;\nFN:Jan Kowalski\nTEL:+48 123\nEND:VCARD", card)
    }

    @Test
    fun `email encodes subject and body`() {
        assertEquals("mailto:a@b.c?subject=Hi%20there&body=A%26B", QrPayload.email("a@b.c", "Hi there", "A&B"))
        assertEquals("mailto:a@b.c", QrPayload.email(" a@b.c ", "", ""))
    }

    @Test
    fun `phone numbers keep digits and plus`() {
        assertEquals("tel:+48123456789", QrPayload.phone("+48 123-456-789"))
        assertEquals("SMSTO:123:Hello", QrPayload.sms("1 2 3", "Hello"))
    }

    @Test
    fun `location is a maps link showing or navigating to coordinates or a place`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=52.2297,21.0122",
            QrPayload.location("52,2297 21,0122", navigate = false)
        )
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Plac%20Zamkowy",
            QrPayload.location(" Plac Zamkowy ", navigate = false)
        )
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=52.2297,21.0122",
            QrPayload.location("52.2297, 21.0122", navigate = true)
        )
    }

    @Test
    fun `coordinates are recognized in what was typed`() {
        assertCoordinates("52.2297", "21.0122", typed = "52.2297, 21.0122")
        assertCoordinates("52.2297", "21.0122", typed = "52,2297, 21,0122")
        assertCoordinates("-33.9", "151.2", typed = "-33.9;151.2")
        assertCoordinates("52", "21", typed = "52 21")
        assertNull(QrPayload.coordinates("Plac Zamkowy 4"))
        assertNull(QrPayload.coordinates("95.0, 21.0"))
    }

    private fun assertCoordinates(latitude: String, longitude: String, typed: String) =
        assertEquals(latitude to longitude, QrPayload.coordinates(typed))

    @Test
    fun `forms need their required values`() {
        assertFalse(QrType.Wifi.isComplete(mapOf("ssid" to " ")))
        assertTrue(QrType.Wifi.isComplete(mapOf("ssid" to "Home")))
        assertFalse(QrType.Location.isComplete(mapOf("place" to "")))
        assertTrue(QrType.Location.isComplete(mapOf("place" to "52.1, 21")))
        assertTrue(QrType.Contact.isComplete(mapOf("email" to "a@b.c")))
    }
}
