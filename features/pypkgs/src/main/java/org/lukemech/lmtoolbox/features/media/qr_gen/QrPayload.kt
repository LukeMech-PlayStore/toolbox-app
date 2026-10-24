package org.lukemech.lmtoolbox.features.media.qr_gen

import java.net.URLEncoder

/** Contents of QR codes as scanners understand them. */
object QrPayload {
    /** Adds https:// when there's no scheme. */
    fun url(address: String): String {
        val trimmed = address.trim()
        return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(trimmed)) trimmed else "https://$trimmed"
    }

    /** `WIFI:T:WPA;S:name;P:password;H:true;;` ([security] WPA, WEP or nopass, which drops the password). */
    fun wifi(ssid: String, password: String, security: String, hidden: Boolean): String = buildString {
        append("WIFI:T:").append(security).append(";S:").append(escapeWifi(ssid)).append(';')
        if (security != "nopass") append("P:").append(escapeWifi(password)).append(';')
        if (hidden) append("H:true;")
        append(';')
    }

    private fun escapeWifi(value: String) = value.replace(Regex("""([\\;,:"])"""), """\\$1""")

    fun vCard(first: String, last: String, phone: String, email: String, organization: String, website: String): String {
        fun esc(value: String) = value.trim().replace(Regex("""([\\;,])"""), """\\$1""")
        return buildList {
            add("BEGIN:VCARD")
            add("VERSION:3.0")
            add("N:${esc(last)};${esc(first)};;;")
            add("FN:${esc(listOf(first.trim(), last.trim()).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { organization.trim() })}")
            if (organization.isNotBlank()) add("ORG:${esc(organization)}")
            if (phone.isNotBlank()) add("TEL:${phone.trim()}")
            if (email.isNotBlank()) add("EMAIL:${email.trim()}")
            if (website.isNotBlank()) add("URL:${url(website)}")
            add("END:VCARD")
        }.joinToString("\n")
    }

    fun email(address: String, subject: String, body: String): String {
        val query = listOfNotNull(
            subject.takeIf { it.isNotBlank() }?.let { "subject=${encode(it)}" },
            body.takeIf { it.isNotBlank() }?.let { "body=${encode(it)}" }
        )
        return "mailto:${address.trim()}" + if (query.isEmpty()) "" else "?" + query.joinToString("&")
    }

    fun sms(number: String, message: String) = "SMSTO:${phoneNumber(number)}:$message"

    fun phone(number: String) = "tel:${phoneNumber(number)}"

    private fun phoneNumber(number: String) = number.filter { it.isDigit() || it == '+' }

    /**
     * Maps link showing [place] (an address, or coordinates, see [coordinates]) or navigating there
     * ([navigate]). Not geo: links, which camera apps and scanners mostly don't open.
     */
    fun location(place: String, navigate: Boolean): String {
        val target = coordinates(place)?.let { (lat, lon) -> "$lat,$lon" } ?: encode(place.trim())
        return if (navigate) "https://www.google.com/maps/dir/?api=1&destination=$target"
        else "https://www.google.com/maps/search/?api=1&query=$target"
    }

    /**
     * Latitude and longitude if [text] is just them, e.g. `52.2297, 21.0122`, `52,2297 21,0122` or
     * `52.2297;21.0122` (commas also work as decimal separators), within their ranges; else null.
     */
    fun coordinates(text: String): Pair<String, String>? {
        val match = COORDINATES.matchEntire(text.trim()) ?: return null
        val (lat, lon) = match.destructured.toList().map { it.replace(',', '.') }
        val latitude = lat.toDoubleOrNull() ?: return null
        val longitude = lon.toDoubleOrNull() ?: return null
        return if (latitude in -90.0..90.0 && longitude in -180.0..180.0) lat to lon else null
    }

    private val COORDINATES = Regex("(-?\\d{1,3}(?:[.,]\\d+)?)\\s*[,;\\s]\\s*(-?\\d{1,3}(?:[.,]\\d+)?)")

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/** How a code looks, see gen.py: width in px, error correction (0-3: L, M, Q, H), border in modules, module style. */
data class QrStyle(val size: Int = 1024, val errorCorrection: Int = 1, val border: Int = 1, val style: String = "square") {
    companion object {
        /** gen.py's module styles, in the order they're offered. */
        val STYLES = listOf("square", "gapped_square", "circle", "rounded", "vertical", "horizontal")
        const val MIN_SIZE = 256
        const val MAX_SIZE = 4096
        const val SIZE_STEP = 128
        const val MAX_BORDER = 10
    }
}
