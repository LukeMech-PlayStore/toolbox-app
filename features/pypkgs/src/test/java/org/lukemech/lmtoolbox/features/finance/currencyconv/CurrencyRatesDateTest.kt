package org.lukemech.lmtoolbox.features.finance.currencyconv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CurrencyRatesDateTest {
    private fun frankfurt(date: String, hour: Int) =
        ZonedDateTime.of(LocalDate.parse(date).atTime(hour, 0), ZoneId.of("Europe/Berlin"))

    @Test
    fun `today's rates only count after publication`() {
        // Tuesday
        assertEquals(LocalDate.parse("2026-10-12"), EcbRates.latestRatesDate(frankfurt("2026-10-13", 10)))
        assertEquals(LocalDate.parse("2026-10-13"), EcbRates.latestRatesDate(frankfurt("2026-10-13", 17)))
    }

    @Test
    fun `weekend falls back to friday`() {
        assertEquals(LocalDate.parse("2026-10-09"), EcbRates.latestRatesDate(frankfurt("2026-10-11", 12)))
        // Monday morning: still Friday's
        assertEquals(LocalDate.parse("2026-10-09"), EcbRates.latestRatesDate(frankfurt("2026-10-12", 9)))
    }

    @Test
    fun `target holidays are skipped`() {
        // Easter 2026 is 5 April: Good Friday 3rd, Easter Monday 6th
        assertEquals(LocalDate.parse("2026-04-02"), EcbRates.latestRatesDate(frankfurt("2026-04-06", 18)))
        // 25 and 26 December 2025 (Thursday, Friday)
        assertEquals(LocalDate.parse("2025-12-24"), EcbRates.latestRatesDate(frankfurt("2025-12-28", 12)))
        assertEquals(LocalDate.parse("2025-12-31"), EcbRates.latestRatesDate(frankfurt("2026-01-01", 18)))
    }

    @Test
    fun `outdated compares with the latest publication`() {
        val now = frankfurt("2026-10-13", 17)
        assertFalse(EcbRates.isOutdated("2026-10-13", now))
        assertTrue(EcbRates.isOutdated("2026-10-12", now))
        assertTrue(EcbRates.isOutdated("Unknown", now))
    }
}
