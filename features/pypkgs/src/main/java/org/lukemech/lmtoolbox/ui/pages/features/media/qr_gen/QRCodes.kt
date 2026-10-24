package org.lukemech.lmtoolbox.ui.pages.features.media.qr_gen

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import org.lukemech.lmtoolbox.features.pypkgs.R
import org.lukemech.lmtoolbox.ui.pages.features.media.qr_gen.QrField.Keyboard
import org.lukemech.lmtoolbox.features.media.qr_gen.QrPayload

/** One input of a [QrType]'s form; its value is kept under [key]. */
sealed class QrField(val key: String, @param:StringRes val label: Int) {
    /**
     * What a [Text]'s keyboard is for. Not Compose's KeyboardType itself: a value class, it turned
     * the JVM constructor of [Text] into one only its defaults one calls.
     */
    enum class Keyboard {
        Plain, Uri, Password, Phone, Email;

        val type: KeyboardType
            get() = when (this) {
                Plain -> KeyboardType.Text
                Uri -> KeyboardType.Uri
                Password -> KeyboardType.Password
                Phone -> KeyboardType.Phone
                Email -> KeyboardType.Email
            }
    }

    class Text(
        key: String,
        @StringRes label: Int,
        val keyboard: Keyboard = Keyboard.Plain,
        val required: Boolean = false,
        val multiline: Boolean = false,
        /** Note under the field, if any. */
        @param:StringRes val note: Int? = null
    ) : QrField(key, label)

    /** One of [options] (value to label); the first one by default. */
    class Choice(key: String, @StringRes label: Int, val options: List<Pair<String, Int>>) : QrField(key, label)

    /** "true" or not. */
    class Toggle(key: String, @StringRes label: Int) : QrField(key, label)
}

/**
 * Kinds of QR codes, as QR generators usually offer them: each with its form [fields], how its
 * values turn into the code's content ([encode]), and the value naming it ([summaryKey]).
 */
enum class QrType(
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val icon: ImageVector,
    val fields: List<QrField>,
    val summaryKey: String,
    /** What the form as a whole needs, shown above it, e.g. "at least one field". */
    @param:StringRes val formNote: Int? = null,
    private val complete: (Map<String, String>) -> Boolean = { values ->
        fields.filterIsInstance<QrField.Text>().filter { it.required }.all { values[it.key].orEmpty().isNotBlank() }
    },
    val encode: (Map<String, String>) -> String
) {
    Text(
        R.string.qr_type_text, R.string.qr_type_text_desc, Icons.AutoMirrored.Default.ShortText,
        listOf(QrField.Text("text", R.string.qr_f_text, required = true, multiline = true)),
        "text", encode = { it["text"].orEmpty() }
    ),
    Url(
        R.string.qr_type_url, R.string.qr_type_url_desc, Icons.Default.Link,
        listOf(QrField.Text("url", R.string.qr_f_url, Keyboard.Uri, required = true)),
        "url", encode = { QrPayload.url(it["url"].orEmpty()) }
    ),
    Wifi(
        R.string.qr_type_wifi, R.string.qr_type_wifi_desc, Icons.Default.Wifi,
        listOf(
            QrField.Text("ssid", R.string.qr_f_ssid, required = true),
            QrField.Text("password", R.string.qr_f_password, Keyboard.Password),
            QrField.Choice(
                "security", R.string.qr_f_security,
                listOf("WPA" to R.string.qr_f_security_wpa, "WEP" to R.string.qr_f_security_wep, "nopass" to R.string.qr_f_security_none)
            ),
            QrField.Toggle("hidden", R.string.qr_f_hidden)
        ),
        "ssid", encode = {
            QrPayload.wifi(it["ssid"].orEmpty(), it["password"].orEmpty(), it["security"] ?: "WPA", it["hidden"] == "true")
        }
    ),
    Contact(
        R.string.qr_type_contact, R.string.qr_type_contact_desc, Icons.Default.Person,
        listOf(
            QrField.Text("first", R.string.qr_f_first_name),
            QrField.Text("last", R.string.qr_f_last_name),
            QrField.Text("phone", R.string.qr_f_phone, Keyboard.Phone),
            QrField.Text("email", R.string.qr_f_email, Keyboard.Email),
            QrField.Text("organization", R.string.qr_f_organization),
            QrField.Text("website", R.string.qr_f_website, Keyboard.Uri)
        ),
        "first",
        formNote = R.string.qr_contact_any,
        complete = { v -> listOf("first", "last", "phone", "email", "organization").any { v[it].orEmpty().isNotBlank() } },
        encode = {
            QrPayload.vCard(
                it["first"].orEmpty(), it["last"].orEmpty(), it["phone"].orEmpty(), it["email"].orEmpty(),
                it["organization"].orEmpty(), it["website"].orEmpty()
            )
        }
    ),
    Email(
        R.string.qr_type_email, R.string.qr_type_email_desc, Icons.Default.Email,
        listOf(
            QrField.Text("email", R.string.qr_f_email, Keyboard.Email, required = true),
            QrField.Text("subject", R.string.qr_f_subject),
            QrField.Text("body", R.string.qr_f_message, multiline = true)
        ),
        "email", encode = { QrPayload.email(it["email"].orEmpty(), it["subject"].orEmpty(), it["body"].orEmpty()) }
    ),
    Sms(
        R.string.qr_type_sms, R.string.qr_type_sms_desc, Icons.AutoMirrored.Default.Message,
        listOf(
            QrField.Text("phone", R.string.qr_f_phone, Keyboard.Phone, required = true),
            QrField.Text("message", R.string.qr_f_message, multiline = true)
        ),
        "phone", encode = { QrPayload.sms(it["phone"].orEmpty(), it["message"].orEmpty()) }
    ),
    Phone(
        R.string.qr_type_phone, R.string.qr_type_phone_desc, Icons.Default.Phone,
        listOf(QrField.Text("phone", R.string.qr_f_phone, Keyboard.Phone, required = true)),
        "phone", encode = { QrPayload.phone(it["phone"].orEmpty()) }
    ),
    Location(
        R.string.qr_type_location, R.string.qr_type_location_desc, Icons.Default.Place,
        listOf(
            QrField.Choice(
                "mode", R.string.qr_f_location_mode,
                listOf("show" to R.string.qr_f_mode_show, "navigate" to R.string.qr_f_mode_navigate)
            ),
            QrField.Text(
                "place", R.string.qr_f_place, required = true,
                // An example of coordinates and of an address
                note = R.string.qr_f_place_hint
            )
        ),
        "place", encode = { QrPayload.location(it["place"].orEmpty(), navigate = it["mode"] == "navigate") }
    );

    /** Whether [values] are enough for a code. */
    fun isComplete(values: Map<String, String>) = complete(values)
}
