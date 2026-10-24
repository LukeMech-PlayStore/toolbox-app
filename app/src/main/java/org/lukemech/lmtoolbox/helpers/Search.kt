package org.lukemech.lmtoolbox.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import org.lukemech.lmtoolbox.R
import org.xmlpull.v1.XmlPullParser
import java.text.Normalizer
import java.util.Locale

/**
 * Search over features and settings in every app language, so "currency" finds the currency
 * converter with the app in Polish too. Each query word has to start a word of the titles or
 * keywords (or appear inside one, from 4 letters on: "code" in "barcode"); case and diacritics
 * don't matter ("zloty" finds "złoty").
 */
object Search {
    /** Normalized words of something searchable; [titles] rank above [keywords]. */
    class Entry(titles: List<String>, keywords: List<String>) {
        val titleWords = titles.flatMap(::words)
        val keywordWords = keywords.flatMap(::words)
    }

    /** Lowercase, without diacritics. */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace('ł', 'l')

    private fun words(text: String) = normalize(text).split(SEPARATORS).filter { it.isNotEmpty() }

    /** 0 if [entry] doesn't match [query], more the better it does; a blank query matches nothing. */
    fun score(query: String, entry: Entry): Int {
        val queryWords = words(query)
        if (queryWords.isEmpty()) return 0
        fun List<String>.has(q: String) = any { it.startsWith(q) || (q.length >= 4 && q in it) }
        return when {
            queryWords.all { entry.titleWords.has(it) } -> 2
            queryWords.all { entry.titleWords.has(it) || entry.keywordWords.has(it) } -> 1
            else -> 0
        }
    }

    /** Entry of string resources, taken in every app language. */
    fun entry(context: Context, @StringRes titles: List<Int>, @StringRes keywords: List<Int> = emptyList()): Entry {
        val all = languages(context)
        return Entry(titles.flatMap { id -> all.map { it.getString(id) } }, keywords.flatMap { id -> all.map { it.getString(id) } })
    }

    @Volatile
    private var languageResources: List<Resources>? = null

    /**
     * Resources in each language of res/xml/locales_config.xml (the current one is among them). All
     * of them are in the app's main APK: the bundle isn't split by language (see app/build.gradle.kts).
     */
    @SuppressLint("AppBundleLocaleChanges")
    private fun languages(context: Context): List<Resources> = languageResources ?: synchronized(this) {
        val app = context.applicationContext
        languageTags(app).map { tag ->
            val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            app.createConfigurationContext(config).resources
        }.also { languageResources = it }
    }

    /** The app's languages, as res/xml/locales_config.xml lists them (e.g. "en", "pl"). */
    fun languageTags(context: Context): List<String> {
        val tags = mutableListOf<String>()
        context.applicationContext.resources.getXml(R.xml.locales_config).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    parser.getAttributeValue(ANDROID_NS, "name")?.let(tags::add)
                }
            }
        }
        return tags
    }

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
    @Suppress("HttpUrlsUsage") // An XML namespace's name, not a link
    private const val ANDROID_NS ="http://schemas.android.com/apk/res/android"
}
