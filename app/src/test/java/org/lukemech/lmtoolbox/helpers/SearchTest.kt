package org.lukemech.lmtoolbox.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    // Titles and keywords as they come from every app language
    private val currency = Search.Entry(
        titles = listOf("Currency Converter", "Przelicznik walut"),
        keywords = listOf("currency, exchange, money", "waluta, kurs, złoty", "Converters", "Przeliczniki")
    )
    private val level = Search.Entry(
        titles = listOf("Spirit level", "Poziomica"),
        keywords = listOf("bubble, tilt", "libella, nachylenie")
    )

    @Test
    fun `finds in any language`() {
        assertTrue(Search.score("currency", currency) > 0)
        assertTrue(Search.score("przelicznik", currency) > 0)
        assertTrue(Search.score("kurs", currency) > 0)
    }

    @Test
    fun `ignores case and diacritics`() {
        assertTrue(Search.score("ZLOTY", currency) > 0)
        assertTrue(Search.score("złoty", currency) > 0)
        assertEquals("zolty zuraw", Search.normalize("Żółty Żuraw"))
    }

    @Test
    fun `words match by their start, or inside from 4 letters on`() {
        assertTrue(Search.score("poz", level) > 0)
        assertTrue(Search.score("ubble", level) > 0)
        assertEquals(0, Search.score("ub", level))
    }

    @Test
    fun `every query word has to match`() {
        assertTrue(Search.score("spirit level", level) > 0)
        assertEquals(0, Search.score("spirit money", level))
    }

    @Test
    fun `title matches rank above keyword ones`() {
        assertTrue(Search.score("poziomica", level) > Search.score("bubble", level))
    }

    @Test
    fun `blank query matches nothing`() {
        assertEquals(0, Search.score("  ", currency))
    }
}
